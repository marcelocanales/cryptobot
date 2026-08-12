package com.cryptobot.watch;

import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PriceLevel;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

/**
 * Corre en loop, en primer plano, toda la noche si hace falta: trae el book
 * de Poloniex y NotBank para cada par de la lista, calcula el spread real
 * cruzado en las dos direcciones, y registra cada observación en un CSV —
 * no solo la última, todas. Una foto no alcanza para saber si hay
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

    // Filtro de liquidez real: ignora niveles de book cuyo valor nocional
    // (precio x cantidad, en USDT) sea menor a esto. Sin esto, una sola
    // orden vieja y chica parada lejos del resto del libro (lo vimos en
    // vivo con XTZ en Poloniex) se reporta como "el mejor precio" y genera
    // un spread falso. Ver docs/roadmap.md — backlog "Filtrar por liquidez
    // real antes de evaluar un ciclo".
    private static final BigDecimal MIN_NOTIONAL_USDT = BigDecimal.valueOf(50);

    // Detector de precio congelado: encontrado necesario en la corrida
    // nocturna del Sprint 0003 — XTZ en Poloniex pasó tamaño mínimo (arriba)
    // pero quedó exactamente en el mismo precio 7 horas seguidas: no es un
    // book vivo, son órdenes abandonadas. 10 ciclos de 30s = 5 minutos sin
    // moverse -> se marca como sospechoso (no se descarta, se marca).
    private static final int STALE_AFTER_CYCLES = 10;

    private static final List<TrackedPair> PAIRS = List.of(
        new TrackedPair("BTC", "BTC_USDT", "BTCUSDT"),
        new TrackedPair("ETH", "ETH_USDT", "ETHUSDT"),
        new TrackedPair("LTC", "LTC_USDT", "LTCUSDT"),
        new TrackedPair("DOGE", "DOGE_USDT", "DOGEUSDT"),
        new TrackedPair("AAVE", "AAVE_USDT", "AAVEUSDT"),
        new TrackedPair("GRAM", "GRAM_USDT", "GRAMUSDT"),
        new TrackedPair("XTZ", "XTZ_USDT", "XTZUSDT"),
        new TrackedPair("SHIB", "SHIB_USDT", "SHIBUSDT")
    );

    public static void main(String[] args) throws IOException {
        var poloniex = new PoloniexConnector();
        var notbank = new NotBankConnector();
        var staleness = new StalenessTracker(STALE_AFTER_CYCLES);

        Path outputPath = resolveOutputPath();
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            writer.write("timestamp,pair,polo_bid,polo_ask,nb_bid,nb_ask,"
                + "diff_buy_polo_sell_nb,diff_buy_polo_sell_nb_pct,"
                + "diff_buy_nb_sell_polo,diff_buy_nb_sell_polo_pct,flag,stale,error");
            writer.newLine();
            writer.flush();

            System.out.println("Registrando en: " + outputPath.toAbsolutePath());
            System.out.println("Pares: " + PAIRS.stream().map(TrackedPair::label).toList());
            System.out.println("Intervalo: " + CYCLE_INTERVAL_SECONDS + "s. Precio congelado >= "
                + STALE_AFTER_CYCLES + " ciclos se marca. Ctrl+C para parar (lo ya registrado queda guardado).");
            System.out.println();

            Runtime.getRuntime().addShutdownHook(new Thread(() ->
                System.out.println("\nDetenido. Revisá " + outputPath.toAbsolutePath())));

            long cycle = 0;
            while (true) {
                cycle++;
                Instant cycleStart = Instant.now();
                int flagged = 0;

                for (TrackedPair pair : PAIRS) {
                    flagged += processPair(poloniex, notbank, pair, writer, staleness);
                }
                writer.flush();

                System.out.println("[" + timestamp() + "] ciclo " + cycle + " — "
                    + PAIRS.size() + " pares, " + flagged + " marcados para revisar");

                sleepUntilNextCycle(cycleStart);
            }
        }
    }

    private static int processPair(PoloniexConnector poloniex, NotBankConnector notbank,
                                    TrackedPair pair, BufferedWriter writer, StalenessTracker staleness)
            throws IOException {
        String ts = timestamp();
        try {
            OrderBook poloBook = poloniex.fetchOrderBook(pair.poloniexSymbol());
            OrderBook nbBook = notbank.fetchOrderBook(pair.notbankSymbol());

            PriceLevel poloBid = poloBook.bestBidAbove(MIN_NOTIONAL_USDT);
            PriceLevel poloAsk = poloBook.bestAskAbove(MIN_NOTIONAL_USDT);
            PriceLevel nbBid = nbBook.bestBidAbove(MIN_NOTIONAL_USDT);
            PriceLevel nbAsk = nbBook.bestAskAbove(MIN_NOTIONAL_USDT);

            if (poloBid == null || poloAsk == null || nbBid == null || nbAsk == null) {
                writer.write(String.join(",", ts, pair.label(), "", "", "", "", "", "", "", "", "", "",
                    "sin nivel con liquidez >= " + MIN_NOTIONAL_USDT + " USDT en algún lado"));
                writer.newLine();
                return 0;
            }

            List<String> staleFields = new ArrayList<>();
            if (staleness.observe("Poloniex:" + pair.label() + ":bid", poloBid.price())) staleFields.add("polo_bid");
            if (staleness.observe("Poloniex:" + pair.label() + ":ask", poloAsk.price())) staleFields.add("polo_ask");
            if (staleness.observe("NotBank:" + pair.label() + ":bid", nbBid.price())) staleFields.add("nb_bid");
            if (staleness.observe("NotBank:" + pair.label() + ":ask", nbAsk.price())) staleFields.add("nb_ask");
            String staleLabel = String.join("|", staleFields);

            BigDecimal diffA = nbBid.price().subtract(poloAsk.price());   // comprar Poloniex, vender NotBank
            BigDecimal diffAPct = percent(diffA, poloAsk.price());
            BigDecimal diffB = poloBid.price().subtract(nbAsk.price());   // comprar NotBank, vender Poloniex
            BigDecimal diffBPct = percent(diffB, nbAsk.price());

            boolean interesting = diffA.signum() > 0 || diffB.signum() > 0;

            writer.write(String.join(",",
                ts, pair.label(),
                poloBid.price().toPlainString(), poloAsk.price().toPlainString(),
                nbBid.price().toPlainString(), nbAsk.price().toPlainString(),
                diffA.toPlainString(), diffAPct.toPlainString(),
                diffB.toPlainString(), diffBPct.toPlainString(),
                interesting ? "REVISAR" : "",
                staleLabel,
                ""
            ));
            writer.newLine();

            if (interesting) {
                String staleNote = staleFields.isEmpty() ? "" : " [OJO: " + staleLabel + " congelado]";
                System.out.println("  >> " + pair.label() + ": posible spread bruto — "
                    + "compra Poloniex/vende NotBank " + diffAPct + "% | "
                    + "compra NotBank/vende Poloniex " + diffBPct + "%" + staleNote);
            }

            return interesting ? 1 : 0;
        } catch (Exception e) {
            writer.write(String.join(",", ts, pair.label(), "", "", "", "", "", "", "", "", "", "",
                escapeCsv(String.valueOf(e.getMessage()))));
            writer.newLine();
            System.out.println("  !! " + pair.label() + ": error — " + e.getMessage());
            return 0;
        }
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal base) {
        return amount.divide(base, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
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
