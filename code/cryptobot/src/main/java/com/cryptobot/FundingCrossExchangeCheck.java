package com.cryptobot;

import com.cryptobot.funding.FundingCrossExchangeCandidates;
import com.cryptobot.funding.FundingCrossExchangeSpread;
import com.cryptobot.marketdata.MinNotional;
import com.cryptobot.marketdata.ParallelFetch;
import com.cryptobot.marketdata.PerpQuote;
import com.cryptobot.marketdata.bitfinex.BitfinexConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 0024 — foto en vivo (no continua todavía) de viabilidad de
 * funding rate cross-exchange (hipótesis 05, priorizada #3 en el catálogo
 * desde el Sprint 0001, nunca probada hasta ahora por falta de un segundo
 * exchange con perpetuos accesibles): corto en el perpetuo con funding
 * anualizado más alto, largo en el de más bajo (mejor de N candidatos,
 * ver {@link FundingCrossExchangeSpread}), entre Poloniex y Bitfinex.
 */
public class FundingCrossExchangeCheck {

    private static final BigDecimal MIN_NOTIONAL_USDT = MinNotional.forCurrency("USDT");

    public static void main(String[] args) {
        var poloniex = new PoloniexConnector();
        var bitfinex = new BitfinexConnector();

        List<FundingCrossExchangeCandidates.Candidate> candidates = FundingCrossExchangeCandidates.all(poloniex, bitfinex);
        System.out.println("Activos con perpetuo en 2+ exchanges (Poloniex/Bitfinex): " + candidates.size());
        System.out.println();

        List<ParallelFetch.FetchTask<String, PerpQuote>> perpTasks = new ArrayList<>();
        for (FundingCrossExchangeCandidates.Candidate c : candidates) {
            for (FundingCrossExchangeCandidates.PerpVenue v : c.venues()) {
                perpTasks.add(new ParallelFetch.FetchTask<>(perpKey(v.exchangeName(), v.perpSymbol()), v.exchangeName(),
                    () -> fetchQuote(poloniex, bitfinex, v)));
            }
        }
        ParallelFetch.Outcome<String, PerpQuote> outcome = ParallelFetch.fetchAll(perpTasks);

        for (Map.Entry<String, String> error : outcome.errors().entrySet()) {
            System.out.println("  ERROR (" + error.getKey() + "): " + error.getValue());
        }
        System.out.println();

        int reported = 0;
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

            Optional<FundingCrossExchangeSpread.Result> result =
                FundingCrossExchangeSpread.evaluate(c.asset(), perpCandidates, MIN_NOTIONAL_USDT);

            if (result.isEmpty()) {
                System.out.println(c.asset() + ": sin liquidez suficiente en 2 exchanges distintos");
                continue;
            }
            reported++;
            FundingCrossExchangeSpread.Result r = result.get();
            String breakeven = r.breakevenHoursIfPositive()
                .map(h -> h.toPlainString() + "h")
                .orElse("nunca — diferencial <= 0");
            System.out.println(r.asset() + ": corto " + r.shortExchange() + " (funding anualizado "
                + r.shortAnnualizedFundingPct() + "%) / largo " + r.longExchange() + " (funding anualizado "
                + r.longAnnualizedFundingPct() + "%) -> diferencial anualizado " + r.annualizedDifferentialPct()
                + "%, fees entrada " + r.entryFeesPct() + "%, breakeven: " + breakeven);
        }

        System.out.println();
        System.out.println(reported + " activos evaluados con datos suficientes, de " + candidates.size());
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
}
