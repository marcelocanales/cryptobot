package com.cryptobot.watch;

import com.cryptobot.marketdata.NetSpread;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.ParallelFetch;
import com.cryptobot.marketdata.PriceLevel;
import com.cryptobot.marketdata.TrackedAsset;
import com.cryptobot.marketdata.TrackedAssets;
import com.cryptobot.marketdata.binance.BinanceConnector;
import com.cryptobot.marketdata.bitfinex.BitfinexConnector;
import com.cryptobot.marketdata.buda.BudaConnector;
import com.cryptobot.marketdata.coinex.CoinExConnector;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.marketdata.yobit.YobitConnector;

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
import java.util.List;
import java.util.Optional;

/**
 * Corre en loop, en primer plano, toda la noche si hace falta: trae el book
 * de los 7 exchanges conectados (Poloniex, NotBank, Buda, YoBit, CoinEx,
 * Bitfinex, Binance) para cada activo de {@link TrackedAssets}, genera todas las combinaciones posibles
 * entre los exchanges que lo cotizan, calcula el spread neto de fees en las
 * dos direcciones de cada combinación, y registra cada observación en un
 * CSV — no solo la última, todas. Una foto no alcanza para saber si hay
 * arbitraje (aparece y desaparece); esto es lo que se necesita en cambio:
 * muestreo continuo, para no depender de tener suerte con el momento en
 * que se mira.
 *
 * Uso: mvn exec:java -Dexec.mainClass=com.cryptobot.watch.SpreadWatcher
 * Parar con Ctrl+C — cada ciclo se guarda (flush) antes del siguiente, así
 * que interrumpirlo no pierde lo ya registrado.
 */
public class SpreadWatcher {

    private static final int CYCLE_INTERVAL_SECONDS = 30;

    // Detector de precio congelado: encontrado necesario en la corrida
    // nocturna del Sprint 0003 — XTZ en Poloniex pasó tamaño mínimo pero
    // quedó exactamente en el mismo precio 7 horas seguidas: no es un book
    // vivo, son órdenes abandonadas. 10 ciclos de 30s = 5 minutos sin
    // moverse -> se marca como sospechoso (no se descarta, se marca).
    private static final int STALE_AFTER_CYCLES = 10;

    public static void main(String[] args) throws IOException {
        var poloniex = new PoloniexConnector();
        var notbank = new NotBankConnector();
        var buda = new BudaConnector();
        var yobit = new YobitConnector();
        var coinex = new CoinExConnector();
        var bitfinex = new BitfinexConnector();
        var binance = new BinanceConnector();
        var staleness = new StalenessTracker(STALE_AFTER_CYCLES);

        List<TrackedAsset> assets = TrackedAssets.all(poloniex, notbank, buda, yobit, coinex, bitfinex, binance);

        Path outputPath = resolveOutputPath();
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            writer.write("timestamp,asset,buy_exchange,sell_exchange,buy_price,sell_price,"
                + "gross_pct,fees_pct,net_pct,stale,flag,error");
            writer.newLine();
            writer.flush();

            System.out.println("Registrando en: " + outputPath.toAbsolutePath());
            System.out.println("Activos: " + assets.stream().map(TrackedAsset::label).toList());
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
                for (TrackedAsset asset : assets) {
                    for (TrackedAsset.Venue venue : asset.venues()) {
                        String key = venue.exchangeName() + "|" + venue.symbol();
                        fetchTasks.add(new ParallelFetch.FetchTask<>(key, venue.exchangeName(),
                            () -> venue.connector().fetchOrderBook(venue.symbol())));
                    }
                }
                ParallelFetch.Outcome<String, OrderBook> outcome = ParallelFetch.fetchAll(fetchTasks);

                int flagged = 0;
                for (TrackedAsset asset : assets) {
                    flagged += processAsset(asset, writer, staleness, outcome, ts);
                }
                writer.flush();

                System.out.println("[" + timestamp() + "] ciclo " + cycle + " — "
                    + assets.size() + " activos, " + flagged + " combinaciones marcadas para revisar");

                sleepUntilNextCycle(cycleStart);
            }
        }
    }

    private record VenueQuote(String exchange, PriceLevel bid, PriceLevel ask,
                               boolean bidStale, boolean askStale) {
    }

    private static int processAsset(TrackedAsset asset, BufferedWriter writer, StalenessTracker staleness,
                                     ParallelFetch.Outcome<String, OrderBook> outcome, String ts)
            throws IOException {
        List<VenueQuote> quotes = new ArrayList<>();

        for (TrackedAsset.Venue venue : asset.venues()) {
            String key = venue.exchangeName() + "|" + venue.symbol();
            OrderBook book = outcome.results().get(key);
            if (book == null) {
                String error = outcome.errors().getOrDefault(key, "sin datos");
                writer.write(String.join(",", ts, asset.label(), venue.exchangeName(), "", "", "", "", "", "", "", "",
                    escapeCsv(error)));
                writer.newLine();
                System.out.println("  !! " + asset.label() + " (" + venue.exchangeName() + "): error — " + error);
                continue;
            }
            PriceLevel bid = book.bestBidAbove(asset.minNotional());
            PriceLevel ask = book.bestAskAbove(asset.minNotional());
            boolean bidStale = bid != null
                && staleness.observe(venue.exchangeName() + ":" + asset.label() + ":bid", bid.price());
            boolean askStale = ask != null
                && staleness.observe(venue.exchangeName() + ":" + asset.label() + ":ask", ask.price());
            quotes.add(new VenueQuote(venue.exchangeName(), bid, ask, bidStale, askStale));
        }

        int flagged = 0;
        for (int i = 0; i < quotes.size(); i++) {
            for (int j = i + 1; j < quotes.size(); j++) {
                VenueQuote a = quotes.get(i);
                VenueQuote b = quotes.get(j);
                flagged += writeDirection(writer, ts, asset.label(), asset.quoteCurrency(), a.exchange(), b.exchange(),
                    a.ask(), b.bid(), a.askStale(), b.bidStale());
                flagged += writeDirection(writer, ts, asset.label(), asset.quoteCurrency(), b.exchange(), a.exchange(),
                    b.ask(), a.bid(), b.askStale(), a.bidStale());
            }
        }
        return flagged;
    }

    private static int writeDirection(BufferedWriter writer, String ts, String assetLabel, String quoteCurrency,
                                       String buyExchange, String sellExchange,
                                       PriceLevel buyAt, PriceLevel sellAt,
                                       boolean buyStale, boolean sellStale) throws IOException {
        Optional<NetSpread.Result> result = NetSpread.evaluate(buyExchange, sellExchange, quoteCurrency, buyAt, sellAt);
        if (result.isEmpty()) {
            writer.write(String.join(",", ts, assetLabel, buyExchange, sellExchange, "", "", "", "", "", "", "",
                "sin liquidez suficiente en algún lado"));
            writer.newLine();
            return 0;
        }

        NetSpread.Result r = result.get();
        List<String> staleParts = new ArrayList<>();
        if (buyStale) staleParts.add(buyExchange + ":ask");
        if (sellStale) staleParts.add(sellExchange + ":bid");
        String staleLabel = String.join("|", staleParts);

        writer.write(String.join(",",
            ts, assetLabel, buyExchange, sellExchange,
            r.buyAt().price().toPlainString(), r.sellAt().price().toPlainString(),
            r.grossPct().toPlainString(), r.feesPct().toPlainString(), r.netPct().toPlainString(),
            staleLabel,
            r.isPositive() ? "REVISAR" : "",
            ""
        ));
        writer.newLine();

        if (r.isPositive()) {
            String staleNote = staleParts.isEmpty() ? "" : " [OJO: " + staleLabel + " congelado]";
            System.out.println("  >> " + assetLabel + ": comprar " + buyExchange + " / vender " + sellExchange
                + " -> neto " + r.netPct() + "% (bruto " + r.grossPct() + "%, fees " + r.feesPct() + "%)" + staleNote);
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
        return Path.of("data", "spread-watch-" + runId + "Z.csv");
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
