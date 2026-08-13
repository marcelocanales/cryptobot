package com.cryptobot.watch;

import com.cryptobot.funding.FundingCrossExchangeCandidates;
import com.cryptobot.funding.FundingCrossExchangeSpread;
import com.cryptobot.marketdata.MinNotional;
import com.cryptobot.marketdata.ParallelFetch;
import com.cryptobot.marketdata.PerpQuote;
import com.cryptobot.marketdata.PriceLevel;
import com.cryptobot.marketdata.bitfinex.BitfinexConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;

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
 * Corre en loop, análogo a {@link CashAndCarryWatcher} pero para la
 * hipótesis 05 (funding rate cross-exchange, Sprint 0024 → 0025): descubre
 * los activos con perpetuo en 2+ exchanges una sola vez al arrancar, y en
 * cada ciclo evalúa corto/largo/diferencial/fees/breakeven, con detector de
 * precio congelado en las patas de precio (bid del corto, ask del largo) —
 * no en el funding rate, que por diseño solo cambia cada 8h y marcarlo
 * "congelado" dentro de esa ventana sería ruido, no una señal (mismo
 * criterio que {@link CashAndCarryWatcher}).
 *
 * Uso: mvn exec:java -Dexec.mainClass=com.cryptobot.watch.FundingCrossExchangeWatcher
 * Parar con Ctrl+C — cada ciclo se guarda (flush) antes del siguiente.
 */
public class FundingCrossExchangeWatcher {

    private static final int CYCLE_INTERVAL_SECONDS = 30;
    private static final int STALE_AFTER_CYCLES = 10;
    private static final BigDecimal MIN_NOTIONAL_USDT = MinNotional.forCurrency("USDT");

    public static void main(String[] args) throws IOException {
        var poloniex = new PoloniexConnector();
        var bitfinex = new BitfinexConnector();
        var staleness = new StalenessTracker(STALE_AFTER_CYCLES);

        List<FundingCrossExchangeCandidates.Candidate> candidates = FundingCrossExchangeCandidates.all(poloniex, bitfinex);

        Path outputPath = resolveOutputPath();
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            writer.write("timestamp,asset,short_exchange,short_annualized_pct,long_exchange,long_annualized_pct,"
                + "annualized_differential_pct,entry_fees_pct,breakeven_hours,stale,flag,error");
            writer.newLine();
            writer.flush();

            System.out.println("Registrando en: " + outputPath.toAbsolutePath());
            System.out.println("Activos: " + candidates.size() + " (perpetuo en Poloniex y Bitfinex)");
            System.out.println("Intervalo: " + CYCLE_INTERVAL_SECONDS + "s. Precio congelado (bid/ask, no funding) >= "
                + STALE_AFTER_CYCLES + " ciclos se marca. Ctrl+C para parar (lo ya registrado queda guardado).");
            System.out.println();

            Runtime.getRuntime().addShutdownHook(new Thread(() ->
                System.out.println("\nDetenido. Revisá " + outputPath.toAbsolutePath())));

            long cycle = 0;
            while (true) {
                cycle++;
                Instant cycleStart = Instant.now();
                String ts = timestamp();

                List<ParallelFetch.FetchTask<String, PerpQuote>> perpTasks = new ArrayList<>();
                for (FundingCrossExchangeCandidates.Candidate c : candidates) {
                    for (FundingCrossExchangeCandidates.PerpVenue v : c.venues()) {
                        perpTasks.add(new ParallelFetch.FetchTask<>(perpKey(v.exchangeName(), v.perpSymbol()),
                            v.exchangeName(), () -> fetchQuote(poloniex, bitfinex, v)));
                    }
                }
                ParallelFetch.Outcome<String, PerpQuote> outcome = ParallelFetch.fetchAll(perpTasks);

                for (Map.Entry<String, String> error : outcome.errors().entrySet()) {
                    writeErrorRow(writer, ts, error.getKey(), error.getValue());
                }

                // Staleness: se observa todo lo que se pudo pedir, sin importar si termina
                // siendo el corto/largo elegido — mismo criterio que los otros watchers.
                Map<String, Boolean> staleByKeyAndSide = new HashMap<>();
                for (Map.Entry<String, PerpQuote> entry : outcome.results().entrySet()) {
                    PriceLevel bid = entry.getValue().bestBid();
                    if (bid != null) {
                        String key = entry.getKey() + ":bid";
                        staleByKeyAndSide.put(key, staleness.observe(key, bid.price()));
                    }
                    PriceLevel ask = entry.getValue().bestAsk();
                    if (ask != null) {
                        String key = entry.getKey() + ":ask";
                        staleByKeyAndSide.put(key, staleness.observe(key, ask.price()));
                    }
                }

                int flagged = 0;
                for (FundingCrossExchangeCandidates.Candidate c : candidates) {
                    List<FundingCrossExchangeSpread.PerpCandidate> perpCandidates = new ArrayList<>();
                    for (FundingCrossExchangeCandidates.PerpVenue v : c.venues()) {
                        PerpQuote quote = outcome.results().get(perpKey(v.exchangeName(), v.perpSymbol()));
                        if (quote != null) {
                            perpCandidates.add(new FundingCrossExchangeSpread.PerpCandidate(v.exchangeName(), quote));
                        }
                    }
                    if (perpCandidates.size() < 2) {
                        continue;
                    }

                    flagged += writeResult(writer, ts, c.asset(), perpCandidates, staleByKeyAndSide);
                }
                writer.flush();

                System.out.println("[" + timestamp() + "] ciclo " + cycle + " — "
                    + candidates.size() + " activos, " + flagged + " marcados para revisar");

                sleepUntilNextCycle(cycleStart);
            }
        }
    }

    private static int writeResult(BufferedWriter writer, String ts, String asset,
                                    List<FundingCrossExchangeSpread.PerpCandidate> perpCandidates,
                                    Map<String, Boolean> staleByKeyAndSide) throws IOException {
        Optional<FundingCrossExchangeSpread.Result> result =
            FundingCrossExchangeSpread.evaluate(asset, perpCandidates, MIN_NOTIONAL_USDT);

        if (result.isEmpty()) {
            writer.write(String.join(",", ts, asset, "", "", "", "", "", "", "", "", "",
                "sin liquidez suficiente en 2 exchanges distintos"));
            writer.newLine();
            return 0;
        }

        FundingCrossExchangeSpread.Result r = result.get();

        List<String> staleParts = new ArrayList<>();
        String shortSymbol = symbolFor(perpCandidates, r.shortExchange());
        String shortKey = perpKey(r.shortExchange(), shortSymbol) + ":bid";
        if (Boolean.TRUE.equals(staleByKeyAndSide.get(shortKey))) {
            staleParts.add(r.shortExchange() + ":bid");
        }
        String longSymbol = symbolFor(perpCandidates, r.longExchange());
        String longKey = perpKey(r.longExchange(), longSymbol) + ":ask";
        if (Boolean.TRUE.equals(staleByKeyAndSide.get(longKey))) {
            staleParts.add(r.longExchange() + ":ask");
        }
        String staleLabel = String.join("|", staleParts);

        boolean hasSignal = r.breakevenHoursIfPositive().isPresent();
        String breakevenStr = r.breakevenHoursIfPositive().map(BigDecimal::toPlainString).orElse("");

        writer.write(String.join(",",
            ts, asset, r.shortExchange(), r.shortAnnualizedFundingPct().toPlainString(),
            r.longExchange(), r.longAnnualizedFundingPct().toPlainString(),
            r.annualizedDifferentialPct().toPlainString(), r.entryFeesPct().toPlainString(), breakevenStr,
            staleLabel,
            hasSignal ? "REVISAR" : "",
            ""
        ));
        writer.newLine();

        if (hasSignal) {
            String staleNote = staleParts.isEmpty() ? "" : " [OJO: " + staleLabel + " congelado]";
            System.out.println("  >> " + asset + ": corto " + r.shortExchange() + " / largo " + r.longExchange()
                + " -> diferencial anualizado " + r.annualizedDifferentialPct() + "%, breakeven " + breakevenStr
                + "h" + staleNote);
        }

        return hasSignal ? 1 : 0;
    }

    private static String symbolFor(List<FundingCrossExchangeSpread.PerpCandidate> perpCandidates, String exchangeName) {
        for (FundingCrossExchangeSpread.PerpCandidate candidate : perpCandidates) {
            if (candidate.exchangeName().equals(exchangeName)) {
                return candidate.quote().symbol();
            }
        }
        return "";
    }

    private static PerpQuote fetchQuote(PoloniexConnector poloniex, BitfinexConnector bitfinex,
                                         FundingCrossExchangeCandidates.PerpVenue v) {
        return "Poloniex".equals(v.exchangeName())
            ? poloniex.fetchPerpQuote(v.perpSymbol())
            : bitfinex.fetchPerpQuote(v.perpSymbol());
    }

    private static String perpKey(String exchangeName, String perpSymbol) {
        return exchangeName + "|" + perpSymbol;
    }

    private static void writeErrorRow(BufferedWriter writer, String ts, String key, String message)
            throws IOException {
        writer.write(String.join(",", ts, "", "", "", "", "", "", "", "", "", "",
            escapeCsv(key + ": " + message)));
        writer.newLine();
        System.out.println("  !! " + key + ": error — " + message);
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
        return Path.of("data", "funding-cross-exchange-watch-" + runId + "Z.csv");
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
