package com.cryptobot.watch;

import com.cryptobot.marketdata.ExchangeConnector;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.MinNotional;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.ParallelFetch;
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
 * Cuerpo compartido de {@link TriangleWatcher} — corre en loop, análogo a
 * {@code SpreadWatcher} pero para arbitraje triangular intra-exchange
 * (Sprint 0009 → 0010). Extraído en el Sprint 0018 para que un segundo
 * exchange (YoBit) lo reuse sin duplicar la orquestación completa: el
 * detector de precio congelado y {@code ParallelFetch} ya eran agnósticos
 * de exchange, solo faltaba que el loop también lo fuera.
 */
public final class TriangleWatchRunner {

    private static final int CYCLE_INTERVAL_SECONDS = 30;
    private static final int STALE_AFTER_CYCLES = 10;

    private TriangleWatchRunner() {
    }

    public static void run(ExchangeConnector connector, String exchangeName, String anchor,
                            String outputFilePrefix) throws IOException {
        var staleness = new StalenessTracker(STALE_AFTER_CYCLES);

        List<Market> allMarkets = connector.fetchMarkets();
        List<Triangle> triangles = TriangleFinder.find(allMarkets, anchor);

        Map<String, Market> marketBySymbol = new HashMap<>();
        for (Triangle t : triangles) {
            marketBySymbol.put(t.marketAB().symbol(), t.marketAB());
            marketBySymbol.put(t.marketBC().symbol(), t.marketBC());
            marketBySymbol.put(t.marketCA().symbol(), t.marketCA());
        }

        Path outputPath = resolveOutputPath(outputFilePrefix);
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
                    fetchTasks.add(new ParallelFetch.FetchTask<>(symbol, exchangeName,
                        () -> connector.fetchOrderBook(symbol)));
                }
                ParallelFetch.Outcome<String, OrderBook> outcome = ParallelFetch.fetchAll(fetchTasks);
                Map<String, OrderBook> books = outcome.results();

                Map<String, Boolean> staleBySymbolSide = new HashMap<>();
                for (Map.Entry<String, OrderBook> entry : books.entrySet()) {
                    String symbol = entry.getKey();
                    OrderBook book = entry.getValue();
                    Market market = marketBySymbol.get(symbol);
                    var minNotional = MinNotional.forCurrency(market.quote());
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
                    flagged += writeDirection(writer, ts, exchangeName, t, true, books, staleBySymbolSide);
                    flagged += writeDirection(writer, ts, exchangeName, t, false, books, staleBySymbolSide);
                }
                writer.flush();

                System.out.println("[" + timestamp() + "] ciclo " + cycle + " — "
                    + triangles.size() + " triángulos, " + flagged + " direcciones marcadas para revisar");

                sleepUntilNextCycle(cycleStart);
            }
        }
    }

    private static int writeDirection(BufferedWriter writer, String ts, String exchangeName, Triangle t,
                                       boolean forward, Map<String, OrderBook> books,
                                       Map<String, Boolean> staleBySymbolSide) throws IOException {
        String triangleLabel = t.currencyA() + "-" + t.currencyB() + "-" + t.currencyC();
        String direction = forward ? "forward" : "backward";

        Optional<TriangleSpread.Result> result = forward
            ? TriangleSpread.evaluateForward(exchangeName, t, books)
            : TriangleSpread.evaluateBackward(exchangeName, t, books);

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

    private static Path resolveOutputPath(String outputFilePrefix) {
        String runId = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss")
            .format(LocalDateTime.now(ZoneOffset.UTC));
        return Path.of("data", outputFilePrefix + "-" + runId + "Z.csv");
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
