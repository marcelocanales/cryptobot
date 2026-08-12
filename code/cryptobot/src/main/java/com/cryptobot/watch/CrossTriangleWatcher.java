package com.cryptobot.watch;

import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.triangular.CrossTriangle;
import com.cryptobot.triangular.CrossTriangleFinder;
import com.cryptobot.triangular.CrossTriangleSpread;
import com.cryptobot.triangular.CrossVenue;
import com.cryptobot.triangular.TriangleSpread;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Corre en loop, análogo a {@link TriangleWatcher} pero para arbitraje
 * triangular cross-exchange (Sprint 0012 → 0013): descubre los triángulos
 * reales de Poloniex + NotBank una sola vez al arrancar, y en cada ciclo
 * evalúa las dos direcciones de cada uno, con el mismo detector de precio
 * congelado y el mismo umbral de implausibilidad (choque de tickers, ver
 * {@code CrossTriangleCheck}) que ya demostraron su valor.
 *
 * Uso: mvn exec:java -Dexec.mainClass=com.cryptobot.watch.CrossTriangleWatcher
 * Parar con Ctrl+C — cada ciclo se guarda (flush) antes del siguiente.
 */
public class CrossTriangleWatcher {

    private static final int CYCLE_INTERVAL_SECONDS = 30;
    private static final int STALE_AFTER_CYCLES = 10;
    private static final String ANCHOR = "USDT";

    // Un bruto > 50% no es una oportunidad real — es la misma señal de choque
    // de tickers entre exchanges confirmada en vivo en el Sprint 0012 (BOB:
    // token cripto en Poloniex, Boliviano fiat en NotBank).
    private static final BigDecimal IMPLAUSIBLE_GROSS_PCT = BigDecimal.valueOf(50);

    public static void main(String[] args) throws IOException {
        var poloniex = new PoloniexConnector();
        var notbank = new NotBankConnector();
        var staleness = new StalenessTracker(STALE_AFTER_CYCLES);

        List<CrossVenue> venues = new ArrayList<>();
        for (Market m : poloniex.fetchMarkets()) {
            venues.add(new CrossVenue(poloniex, m));
        }
        for (Market m : notbank.fetchMarkets()) {
            venues.add(new CrossVenue(notbank, m));
        }
        List<CrossTriangle> triangles = CrossTriangleFinder.find(venues, ANCHOR);

        Map<String, CrossVenue> venueByKey = new HashMap<>();
        for (CrossTriangle t : triangles) {
            for (CrossVenue v : allVenues(t)) {
                venueByKey.put(CrossTriangleSpread.bookKey(v.exchangeName(), v.market().symbol()), v);
            }
        }

        Path outputPath = resolveOutputPath();
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            writer.write("timestamp,triangle,tag,direction,path,gross_pct,net_pct,stale,flag,error");
            writer.newLine();
            writer.flush();

            long necessity = triangles.stream().filter(CrossTriangle::isNecessityCycle).count();
            System.out.println("Registrando en: " + outputPath.toAbsolutePath());
            System.out.println("Triángulos: " + triangles.size() + " (" + necessity + " por necesidad, "
                + venueByKey.size() + " order books únicos)");
            System.out.println("Intervalo: " + CYCLE_INTERVAL_SECONDS + "s. Spread NETO de fees. "
                + "Precio congelado >= " + STALE_AFTER_CYCLES + " ciclos se marca. "
                + "Ctrl+C para parar (lo ya registrado queda guardado).");
            System.out.println();

            Runtime.getRuntime().addShutdownHook(new Thread(() ->
                System.out.println("\nDetenido. Revisá " + outputPath.toAbsolutePath())));

            long cycle = 0;
            while (true) {
                cycle++;
                Instant cycleStart = Instant.now();
                String ts = timestamp();

                Map<String, OrderBook> books = new HashMap<>();
                Map<String, Boolean> staleByKeyAndSide = new HashMap<>();

                for (Map.Entry<String, CrossVenue> entry : venueByKey.entrySet()) {
                    String key = entry.getKey();
                    CrossVenue v = entry.getValue();
                    try {
                        OrderBook book = v.connector().fetchOrderBook(v.market().symbol());
                        books.put(key, book);

                        var minNotional = TriangleSpread.minNotionalFor(v.market().quote());
                        var bid = book.bestBidAbove(minNotional);
                        var ask = book.bestAskAbove(minNotional);
                        if (bid != null) {
                            staleByKeyAndSide.put(key + ":bid", staleness.observe(key + ":bid", bid.price()));
                        }
                        if (ask != null) {
                            staleByKeyAndSide.put(key + ":ask", staleness.observe(key + ":ask", ask.price()));
                        }
                    } catch (Exception e) {
                        writer.write(String.join(",", ts, "", "", "", "", "", "", "", "",
                            escapeCsv(key + ": " + e.getMessage())));
                        writer.newLine();
                        System.out.println("  !! " + key + ": error — " + e.getMessage());
                    }
                }

                int flagged = 0;
                for (CrossTriangle t : triangles) {
                    String triangleLabel = t.currencyA() + "-" + t.currencyB() + "-" + t.currencyC();
                    String tag = t.isNecessityCycle() ? "necesidad" : "optimizacion";
                    flagged += writeDirection(writer, ts, triangleLabel, tag, t, true, books, staleByKeyAndSide);
                    flagged += writeDirection(writer, ts, triangleLabel, tag, t, false, books, staleByKeyAndSide);
                }
                writer.flush();

                System.out.println("[" + timestamp() + "] ciclo " + cycle + " — "
                    + triangles.size() + " triángulos, " + flagged + " direcciones marcadas para revisar");

                sleepUntilNextCycle(cycleStart);
            }
        }
    }

    private static List<CrossVenue> allVenues(CrossTriangle t) {
        List<CrossVenue> all = new ArrayList<>();
        all.addAll(t.venuesAB());
        all.addAll(t.venuesBC());
        all.addAll(t.venuesCA());
        return all;
    }

    private static int writeDirection(BufferedWriter writer, String ts, String triangleLabel, String tag,
                                       CrossTriangle t, boolean forward,
                                       Map<String, OrderBook> books, Map<String, Boolean> staleByKeyAndSide)
            throws IOException {
        String direction = forward ? "forward" : "backward";
        Optional<CrossTriangleSpread.Result> result = forward
            ? CrossTriangleSpread.evaluateForward(t, books)
            : CrossTriangleSpread.evaluateBackward(t, books);

        if (result.isEmpty()) {
            writer.write(String.join(",", ts, triangleLabel, tag, direction, "", "", "", "", "",
                "sin liquidez suficiente en alguna pata"));
            writer.newLine();
            return 0;
        }

        CrossTriangleSpread.Result r = result.get();
        String path = String.join(" -> ", r.path());

        List<String> staleParts = new ArrayList<>();
        for (CrossTriangleSpread.Leg leg : r.legs()) {
            String key = CrossTriangleSpread.bookKey(leg.exchange(), leg.symbol()) + ":" + leg.side();
            if (Boolean.TRUE.equals(staleByKeyAndSide.get(key))) {
                staleParts.add(leg.exchange() + ":" + leg.symbol() + ":" + leg.side());
            }
        }
        String staleLabel = String.join("|", staleParts);

        boolean implausible = r.grossPct().abs().compareTo(IMPLAUSIBLE_GROSS_PCT) > 0;
        String flag = implausible ? "IMPLAUSIBLE" : r.isPositive() ? "REVISAR" : "";

        writer.write(String.join(",",
            ts, triangleLabel, tag, direction, path,
            r.grossPct().toPlainString(), r.netPct().toPlainString(),
            staleLabel, flag, ""
        ));
        writer.newLine();

        if (!implausible && r.isPositive()) {
            String staleNote = staleParts.isEmpty() ? "" : " [OJO: " + staleLabel + " congelado]";
            System.out.println("  >> " + triangleLabel + " (" + direction + "): " + path
                + " -> neto " + r.netPct() + "% (bruto " + r.grossPct() + "%)" + staleNote);
            return 1;
        }
        return 0;
    }

    private static String timestamp() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    }

    private static String escapeCsv(String value) {
        String cleaned = value.replace("\n", " ").replace("\r", " ");
        if (cleaned.contains(",") || cleaned.contains("\"")) {
            return "\"" + cleaned.replace("\"", "\"\"") + "\"";
        }
        return cleaned;
    }

    private static Path resolveOutputPath() {
        String runId = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss")
            .format(LocalDateTime.now(ZoneOffset.UTC));
        return Path.of("data", "cross-triangle-watch-" + runId + "Z.csv");
    }

    private static void sleepUntilNextCycle(Instant cycleStart) {
        long elapsedMs = Instant.now().toEpochMilli() - cycleStart.toEpochMilli();
        long remainingMs = (CYCLE_INTERVAL_SECONDS * 1000L) - elapsedMs;
        if (remainingMs > 0) {
            try {
                Thread.sleep(remainingMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
