package com.cryptobot;

import com.cryptobot.funding.CashAndCarryCandidates;
import com.cryptobot.funding.CashAndCarrySpread;
import com.cryptobot.marketdata.CrossVenue;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.ParallelFetch;
import com.cryptobot.marketdata.PerpQuote;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.marketdata.yobit.YobitConnector;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 0015 — foto en vivo (no continua todavía) de viabilidad de
 * cash-and-carry: spot largo (mejor precio neto entre los candidatos
 * disponibles, ver {@link CashAndCarryCandidates}) + corto en el perpetuo
 * de Poloniex (el único de los 4 exchanges que tiene). A diferencia de
 * {@code OverlapCheck}/{@code TriangleCheck}/{@code CrossTriangleCheck}
 * (arbitraje instantáneo), acá no hay un solo "neto" — se reporta basis,
 * funding por período, funding anualizado, fees de entrada y períodos de
 * breakeven por separado.
 */
public class CashAndCarryCheck {

    private static final BigDecimal MIN_NOTIONAL_USDT = new BigDecimal("50");

    public static void main(String[] args) {
        var poloniex = new PoloniexConnector();
        var notbank = new NotBankConnector();
        var yobit = new YobitConnector();

        List<CashAndCarryCandidates.Candidate> candidates = CashAndCarryCandidates.all(poloniex, notbank, yobit);
        System.out.println("Activos con perpetuo y spot USDT disponible (Poloniex/NotBank/YoBit): " + candidates.size());
        System.out.println();

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
        for (CashAndCarryCandidates.Candidate c : candidates) {
            PerpQuote perpQuote = perpOutcome.results().get(c.perpSymbol());
            if (perpQuote == null) {
                continue;
            }

            List<CashAndCarrySpread.SpotCandidate> spotCandidates = new ArrayList<>();
            for (CrossVenue v : c.spotVenues()) {
                OrderBook book = spotOutcome.results().get(CashAndCarrySpread.bookKey(v.exchangeName(), v.market().symbol()));
                if (book != null) {
                    spotCandidates.add(new CashAndCarrySpread.SpotCandidate(v.exchangeName(), book));
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
