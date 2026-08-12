package com.cryptobot.watch;

import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.ParallelFetch;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.triangular.Triangle;
import com.cryptobot.triangular.TriangleFinder;
import com.cryptobot.triangular.TriangleSpread;

import java.io.BufferedWriter;
import java.io.IOException;
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
 * Corre en loop, análogo a {@link SpreadWatcher} pero para arbitraje
 * triangular intra-exchange (Sprint 0009 → 0010): descubre los triángulos
 * reales de Poloniex una sola vez al arrancar (no cambian en el tiempo que
 * dura una corrida), y en cada ciclo evalúa las dos direcciones de cada
 * uno, con el mismo detector de precio congelado que ya probó su valor con
 * XTZ en el Sprint 0004.
 *
 * Uso: mvn exec:java -Dexec.mainClass=com.cryptobot.watch.TriangleWatcher
 * Parar con Ctrl+C — cada ciclo se guarda (flush) antes del siguiente.
 */
public class TriangleWatcher {

    private static final int CYCLE_INTERVAL_SECONDS = 30;
    private static final int STALE_AFTER_CYCLES = 10;
    private static final String EXCHANGE = "Poloniex";
    private static final String ANCHOR = "USDT";

    public static void main(String[] args) throws IOException {
        var poloniex = new PoloniexConnector();
        var staleness = new StalenessTracker(STALE_AFTER_CYCLES);

        List<Market> allMarkets = poloniex.fetchMarkets();
        List<Triangle> triangles = TriangleFinder.find(allMarkets, ANCHOR);

        Map<String, Market> marketBySymbol = new HashMap<>();
        for (Triangle t : triangles) {
            marketBySymbol.put(t.marketAB().symbol(), t.marketAB());
            marketBySymbol.put(t.marketBC().symbol(), t.marketBC());
            marketBySymbol.put(t.marketCA().symbol(), t.marketCA());
        }

        Path outputPath = resolveOutputPath();
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            writer.write("timestamp,triangle,direction,path,gross_pct,net_pct,stale,flag,error");
            writer.newLine();
            writer.flush();

            System.out.println("Registrando en: " + outputPath.toAbsolutePath());
            System.out.println("Triángulos: " + triangles.size() + " (" + marketBySymbol.size() + " mercados únicos)");
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

                List<ParallelFetch.FetchTask<String, OrderBook>> fetchTasks = new ArrayList<>();
                for (String symbol : marketBySymbol.keySet()) {
                    fetchTasks.add(new ParallelFetch.FetchTask<>(symbol, "Poloniex",
                        () -> poloniex.fetchOrderBook(symbol)));
                }
                ParallelFetch.Outcome<String, OrderBook> outcome = ParallelFetch.fetchAll(fetchTasks);
                Map<String, OrderBook> books = outcome.results();

                Map<String, Boolean> staleBySymbolSide = new HashMap<>();
                for (Map.Entry<String, OrderBook> entry : books.entrySet()) {
                    String symbol = entry.getKey();
                    OrderBook book = entry.getValue();
                    Market market = marketBySymbol.get(symbol);
                    var minNotional = TriangleSpread.minNotionalFor(market.quote());
                    var bid = book.bestBidAbove(minNotional);
                    var ask = book.bestAskAbove(minNotional);
                    if (bid != null) {
                        staleBySymbolSide.put(symbol + ":bid", staleness.observe(symbol + ":bid", bid.price()));
                    }
                    if (ask != null) {
                        staleBySymbolSide.put(symbol + ":ask", staleness.observe(symbol + ":ask", ask.price()));
                    }
                }
                for (Map.Entry<String, String> entry : outcome.errors().entrySet()) {
                    writer.write(String.join(",", ts, "", "", "", "", "", "", "",
                        escapeCsv(entry.getKey() + ": " + entry.getValue())));
                    writer.newLine();
                    System.out.println("  !! " + entry.getKey() + ": error — " + entry.getValue());
                }

                int flagged = 0;
                for (Triangle t : triangles) {
                    flagged += writeDirection(writer, ts, t, true, books, staleBySymbolSide);
                    flagged += writeDirection(writer, ts, t, false, books, staleBySymbolSide);
                }
                writer.flush();

                System.out.println("[" + timestamp() + "] ciclo " + cycle + " — "
                    + triangles.size() + " triángulos, " + flagged + " direcciones marcadas para revisar");

                sleepUntilNextCycle(cycleStart);
            }
        }
    }

    private static int writeDirection(BufferedWriter writer, String ts, Triangle t, boolean forward,
                                       Map<String, OrderBook> books, Map<String, Boolean> staleBySymbolSide)
            throws IOException {
        String triangleLabel = t.currencyA() + "-" + t.currencyB() + "-" + t.currencyC();
        String direction = forward ? "forward" : "backward";

        Optional<TriangleSpread.Result> result = forward
            ? TriangleSpread.evaluateForward(EXCHANGE, t, books)
            : TriangleSpread.evaluateBackward(EXCHANGE, t, books);

        if (result.isEmpty()) {
            writer.write(String.join(",", ts, triangleLabel, direction, "", "", "", "", "",
                "sin liquidez suficiente en alguna pata"));
            writer.newLine();
            return 0;
        }

        TriangleSpread.Result r = result.get();
        String path = String.join(" -> ", r.path());

        List<String> staleParts = new ArrayList<>();
        for (TriangleSpread.Leg leg : r.legs()) {
            String key = leg.symbol() + ":" + leg.side();
            if (Boolean.TRUE.equals(staleBySymbolSide.get(key))) {
                staleParts.add(key);
            }
        }
        String staleLabel = String.join("|", staleParts);

        writer.write(String.join(",",
            ts, triangleLabel, direction, path,
            r.grossPct().toPlainString(), r.netPct().toPlainString(),
            staleLabel,
            r.isPositive() ? "REVISAR" : "",
            ""
        ));
        writer.newLine();

        if (r.isPositive()) {
            String staleNote = staleParts.isEmpty() ? "" : " [OJO: " + staleLabel + " congelado]";
            System.out.println("  >> " + triangleLabel + " (" + direction + "): " + path
                + " -> neto " + r.netPct() + "% (bruto " + r.grossPct() + "%)" + staleNote);
        }

        return r.isPositive() ? 1 : 0;
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
        return Path.of("data", "triangle-watch-" + runId + "Z.csv");
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
