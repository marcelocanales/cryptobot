package com.cryptobot;

import com.cryptobot.funding.CashAndCarrySpread;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.ParallelFetch;
import com.cryptobot.marketdata.PerpQuote;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 0015 — foto en vivo (no continua todavía) de viabilidad de
 * cash-and-carry: spot largo (mejor precio neto entre Poloniex y NotBank)
 * + corto en el perpetuo de Poloniex (el único de los 4 exchanges que
 * tiene). A diferencia de {@code OverlapCheck}/{@code TriangleCheck}/
 * {@code CrossTriangleCheck} (arbitraje instantáneo), acá no hay un solo
 * "neto" — se reporta basis, funding por período, funding anualizado, fees
 * de entrada y períodos de breakeven por separado.
 */
public class CashAndCarryCheck {

    private static final BigDecimal MIN_NOTIONAL_USDT = new BigDecimal("50");
    private static final String PERP_SUFFIX = "_USDT_PERP";

    private record Candidate(String asset, String perpSymbol, String poloniexSpotSymbol, String notbankSpotSymbol) {
    }

    public static void main(String[] args) {
        var poloniex = new PoloniexConnector();
        var notbank = new NotBankConnector();

        List<String> perpSymbols = poloniex.fetchPerpSymbols();
        System.out.println("Perpetuos encontrados en Poloniex: " + perpSymbols.size());

        Map<String, String> poloniexSpotSymbolByBase = new HashMap<>();
        for (Market m : poloniex.fetchMarkets()) {
            if ("USDT".equals(m.quote())) {
                poloniexSpotSymbolByBase.put(m.base(), m.symbol());
            }
        }
        Map<String, String> notbankSpotSymbolByBase = new HashMap<>();
        for (Market m : notbank.fetchMarkets()) {
            if ("USDT".equals(m.quote())) {
                notbankSpotSymbolByBase.put(m.base(), m.symbol());
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (String perpSymbol : perpSymbols) {
            String asset = perpSymbol.substring(0, perpSymbol.length() - PERP_SUFFIX.length());
            String poloSpot = poloniexSpotSymbolByBase.get(asset);
            String nbSpot = notbankSpotSymbolByBase.get(asset);
            if (poloSpot == null && nbSpot == null) {
                continue; // sin dónde comprar el spot entre los 2 exchanges de este sprint
            }
            candidates.add(new Candidate(asset, perpSymbol, poloSpot, nbSpot));
        }
        System.out.println("Con spot disponible en Poloniex y/o NotBank: " + candidates.size());
        System.out.println();

        List<ParallelFetch.FetchTask<String, OrderBook>> spotTasks = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.poloniexSpotSymbol() != null) {
                spotTasks.add(new ParallelFetch.FetchTask<>("Poloniex|" + c.poloniexSpotSymbol(), "Poloniex",
                    () -> poloniex.fetchOrderBook(c.poloniexSpotSymbol())));
            }
            if (c.notbankSpotSymbol() != null) {
                spotTasks.add(new ParallelFetch.FetchTask<>("NotBank|" + c.notbankSpotSymbol(), "NotBank",
                    () -> notbank.fetchOrderBook(c.notbankSpotSymbol())));
            }
        }
        ParallelFetch.Outcome<String, OrderBook> spotOutcome = ParallelFetch.fetchAll(spotTasks);

        List<ParallelFetch.FetchTask<String, PerpQuote>> perpTasks = new ArrayList<>();
        for (Candidate c : candidates) {
            perpTasks.add(new ParallelFetch.FetchTask<>(c.perpSymbol(), "Poloniex",
                () -> poloniex.fetchPerpQuote(c.perpSymbol())));
        }
        ParallelFetch.Outcome<String, PerpQuote> perpOutcome = ParallelFetch.fetchAll(perpTasks);

        for (Map.Entry<String, String> error : spotOutcome.errors().entrySet()) {
            System.out.println("  ERROR spot (" + error.getKey() + "): " + error.getValue());
        }
        for (Map.Entry<String, String> error : perpOutcome.errors().entrySet()) {
            System.out.println("  ERROR perp (" + error.getKey() + "): " + error.getValue());
        }
        System.out.println();

        int reported = 0;
        for (Candidate c : candidates) {
            PerpQuote perpQuote = perpOutcome.results().get(c.perpSymbol());
            if (perpQuote == null) {
                continue;
            }

            List<CashAndCarrySpread.SpotCandidate> spotCandidates = new ArrayList<>();
            if (c.poloniexSpotSymbol() != null) {
                OrderBook book = spotOutcome.results().get("Poloniex|" + c.poloniexSpotSymbol());
                if (book != null) {
                    spotCandidates.add(new CashAndCarrySpread.SpotCandidate("Poloniex", book));
                }
            }
            if (c.notbankSpotSymbol() != null) {
                OrderBook book = spotOutcome.results().get("NotBank|" + c.notbankSpotSymbol());
                if (book != null) {
                    spotCandidates.add(new CashAndCarrySpread.SpotCandidate("NotBank", book));
                }
            }
            if (spotCandidates.isEmpty()) {
                continue;
            }

            Optional<CashAndCarrySpread.Result> result = CashAndCarrySpread.evaluate(
                c.asset(), spotCandidates, "Poloniex", perpQuote, MIN_NOTIONAL_USDT);

            if (result.isEmpty()) {
                System.out.println(c.asset() + ": sin liquidez suficiente en spot o en el perpetuo");
                continue;
            }
            reported++;
            CashAndCarrySpread.Result r = result.get();
            String breakeven = r.breakevenPeriodsIfPositive()
                .map(p -> p.toPlainString() + " período(s) de " + r.fundingIntervalHours() + "h")
                .orElse("nunca — funding <= 0");
            System.out.println(r.asset() + ": spot " + r.spotExchange() + "@" + r.spotAskPrice()
                + " / perp " + r.perpExchange() + "@" + r.perpBidPrice()
                + " -> basis " + r.basisPct() + "%, funding " + r.fundingRatePct() + "% cada " + r.fundingIntervalHours()
                + "h (anualizado " + r.annualizedFundingPct() + "%), fees entrada " + r.entryFeesPct()
                + "%, breakeven: " + breakeven);
        }

        System.out.println();
        System.out.println(reported + " activos evaluados con datos suficientes, de " + candidates.size());
    }
}
