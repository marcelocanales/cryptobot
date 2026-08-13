package com.cryptobot.watch;

import com.cryptobot.funding.CashAndCarryCandidates;
import com.cryptobot.funding.CashAndCarrySpread;
import com.cryptobot.marketdata.CrossVenue;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.ParallelFetch;
import com.cryptobot.marketdata.PerpQuote;
import com.cryptobot.marketdata.PriceLevel;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.marketdata.yobit.YobitConnector;

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
 * Corre en loop, análogo a {@link TriangleWatcher}/{@link CrossTriangleWatcher}
 * pero para la hipótesis 04 (funding rate cash-and-carry, Sprint 0015 →
 * 0016): descubre los perpetuos reales de Poloniex una sola vez al
 * arrancar, y en cada ciclo evalúa basis/funding/fees/breakeven por
 * activo, con detector de precio congelado en las patas de precio (spot y
 * perpetuo) — no en el funding rate, que por diseño solo cambia cada 8h y
 * marcarlo "congelado" dentro de esa ventana sería ruido, no una señal.
 *
 * Uso: mvn exec:java -Dexec.mainClass=com.cryptobot.watch.CashAndCarryWatcher
 * Parar con Ctrl+C — cada ciclo se guarda (flush) antes del siguiente.
 */
public class CashAndCarryWatcher {

    private static final int CYCLE_INTERVAL_SECONDS = 30;
    private static final int STALE_AFTER_CYCLES = 10;
    private static final BigDecimal MIN_NOTIONAL_USDT = new BigDecimal("50");
    private static final String PERP_EXCHANGE = "Poloniex";

    public static void main(String[] args) throws IOException {
        var poloniex = new PoloniexConnector();
        var notbank = new NotBankConnector();
        var yobit = new YobitConnector();
        var staleness = new StalenessTracker(STALE_AFTER_CYCLES);

        List<CashAndCarryCandidates.Candidate> candidates = CashAndCarryCandidates.all(poloniex, notbank, yobit);

        Path outputPath = resolveOutputPath();
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

            writer.write("timestamp,asset,spot_exchange,spot_ask,perp_exchange,perp_bid,basis_pct,"
                + "funding_rate_pct,funding_interval_hours,annualized_funding_pct,entry_fees_pct,"
                + "breakeven_periods,stale,flag,error");
            writer.newLine();
            writer.flush();

            System.out.println("Registrando en: " + outputPath.toAbsolutePath());
            System.out.println("Activos: " + candidates.size() + " (perpetuo en Poloniex + spot en Poloniex/NotBank/YoBit)");
            System.out.println("Intervalo: " + CYCLE_INTERVAL_SECONDS + "s. Precio congelado (spot/perp, no funding) >= "
                + STALE_AFTER_CYCLES + " ciclos se marca. Ctrl+C para parar (lo ya registrado queda guardado).");
            System.out.println();

            Runtime.getRuntime().addShutdownHook(new Thread(() ->
                System.out.println("\nDetenido. Revisá " + outputPath.toAbsolutePath())));

            long cycle = 0;
            while (true) {
                cycle++;
                Instant cycleStart = Instant.now();
                String ts = timestamp();

                List<ParallelFetch.FetchTask<String, OrderBook>> spotTasks = new ArrayList<>();
                for (CashAndCarryCandidates.Candidate c : candidates) {
                    for (CrossVenue v : c.spotVenues()) {
                        spotTasks.add(new ParallelFetch.FetchTask<>(
                            CashAndCarrySpread.bookKey(v.exchangeName(), v.market().symbol()), v.exchangeName(),
                            () -> v.connector().fetchOrderBook(v.market().symbol())));
                    }
                }
                ParallelFetch.Outcome<String, OrderBook> spotOutcome = ParallelFetch.fetchAll(spotTasks);

                List<ParallelFetch.FetchTask<String, PerpQuote>> perpTasks = new ArrayList<>();
                for (CashAndCarryCandidates.Candidate c : candidates) {
                    perpTasks.add(new ParallelFetch.FetchTask<>(c.perpSymbol(), PERP_EXCHANGE,
                        () -> poloniex.fetchPerpQuote(c.perpSymbol())));
                }
                ParallelFetch.Outcome<String, PerpQuote> perpOutcome = ParallelFetch.fetchAll(perpTasks);

                for (Map.Entry<String, String> error : spotOutcome.errors().entrySet()) {
                    writeErrorRow(writer, ts, error.getKey(), error.getValue());
                }
                for (Map.Entry<String, String> error : perpOutcome.errors().entrySet()) {
                    writeErrorRow(writer, ts, error.getKey(), error.getValue());
                }

                // Staleness: se observa todo lo que se pudo pedir, sin importar si termina
                // siendo el spot elegido — mismo criterio que los otros watchers.
                Map<String, Boolean> staleByKeyAndSide = new HashMap<>();
                for (Map.Entry<String, OrderBook> entry : spotOutcome.results().entrySet()) {
                    PriceLevel ask = entry.getValue().bestAskAbove(MIN_NOTIONAL_USDT);
                    if (ask != null) {
                        String key = entry.getKey() + ":ask";
                        staleByKeyAndSide.put(key, staleness.observe(key, ask.price()));
                    }
                }
                for (Map.Entry<String, PerpQuote> entry : perpOutcome.results().entrySet()) {
                    PriceLevel bid = entry.getValue().bestBid();
                    if (bid != null) {
                        String key = entry.getKey() + ":bid";
                        staleByKeyAndSide.put(key, staleness.observe(key, bid.price()));
                    }
                }

                int flagged = 0;
                for (CashAndCarryCandidates.Candidate c : candidates) {
                    PerpQuote perpQuote = perpOutcome.results().get(c.perpSymbol());
                    if (perpQuote == null) {
                        continue;
                    }
                    List<CashAndCarrySpread.SpotCandidate> spotCandidates = new ArrayList<>();
                    for (CrossVenue v : c.spotVenues()) {
                        OrderBook book = spotOutcome.results().get(
                            CashAndCarrySpread.bookKey(v.exchangeName(), v.market().symbol()));
                        if (book != null) {
                            spotCandidates.add(new CashAndCarrySpread.SpotCandidate(v.exchangeName(), book));
                        }
                    }
                    if (spotCandidates.isEmpty()) {
                        continue;
                    }

                    flagged += writeResult(writer, ts, c.asset(), spotCandidates, perpQuote, c.perpSymbol(),
                        staleByKeyAndSide);
                }
                writer.flush();

                System.out.println("[" + timestamp() + "] ciclo " + cycle + " — "
                    + candidates.size() + " activos, " + flagged + " marcados para revisar");

                sleepUntilNextCycle(cycleStart);
            }
        }
    }

    private static int writeResult(BufferedWriter writer, String ts, String asset,
                                    List<CashAndCarrySpread.SpotCandidate> spotCandidates, PerpQuote perpQuote,
                                    String perpSymbol, Map<String, Boolean> staleByKeyAndSide) throws IOException {
        Optional<CashAndCarrySpread.Result> result = CashAndCarrySpread.evaluate(
            asset, spotCandidates, PERP_EXCHANGE, perpQuote, MIN_NOTIONAL_USDT);

        if (result.isEmpty()) {
            writer.write(String.join(",", ts, asset, "", "", "", "", "", "", "", "", "", "", "", "",
                "sin liquidez suficiente en spot o en el perpetuo"));
            writer.newLine();
            return 0;
        }

        CashAndCarrySpread.Result r = result.get();
        List<String> staleParts = new ArrayList<>();
        String spotKey = r.spotExchange() + "|" + spotSymbolFor(spotCandidates, r.spotExchange()) + ":ask";
        if (Boolean.TRUE.equals(staleByKeyAndSide.get(spotKey))) {
            staleParts.add(r.spotExchange() + ":ask");
        }
        String perpKey = perpSymbol + ":bid";
        if (Boolean.TRUE.equals(staleByKeyAndSide.get(perpKey))) {
            staleParts.add(r.perpExchange() + ":bid");
        }
        String staleLabel = String.join("|", staleParts);

        boolean immediatelyProfitable = r.breakevenPeriodsIfPositive().map(p -> p.signum() == 0).orElse(false);
        String breakevenStr = r.breakevenPeriodsIfPositive().map(BigDecimal::toPlainString).orElse("");

        writer.write(String.join(",",
            ts, asset, r.spotExchange(), r.spotAskPrice().toPlainString(),
            r.perpExchange(), r.perpBidPrice().toPlainString(),
            r.basisPct().toPlainString(), r.fundingRatePct().toPlainString(),
            r.fundingIntervalHours().toPlainString(), r.annualizedFundingPct().toPlainString(),
            r.entryFeesPct().toPlainString(), breakevenStr,
            staleLabel,
            immediatelyProfitable ? "REVISAR" : "",
            ""
        ));
        writer.newLine();

        if (immediatelyProfitable) {
            String staleNote = staleParts.isEmpty() ? "" : " [OJO: " + staleLabel + " congelado]";
            System.out.println("  >> " + asset + ": basis ya cubre la entrada — basis " + r.basisPct()
                + "%, fees " + r.entryFeesPct() + "%" + staleNote);
        }

        return immediatelyProfitable ? 1 : 0;
    }

    private static String spotSymbolFor(List<CashAndCarrySpread.SpotCandidate> spotCandidates, String exchangeName) {
        for (CashAndCarrySpread.SpotCandidate candidate : spotCandidates) {
            if (candidate.exchangeName().equals(exchangeName)) {
                return candidate.book().symbol();
            }
        }
        return "";
    }

    private static void writeErrorRow(BufferedWriter writer, String ts, String key, String message)
            throws IOException {
        writer.write(String.join(",", ts, "", "", "", "", "", "", "", "", "", "", "", "", "",
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
        return Path.of("data", "cash-and-carry-watch-" + runId + "Z.csv");
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
