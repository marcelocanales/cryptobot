package com.cryptobot.funding;

import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PerpQuote;
import com.cryptobot.marketdata.PriceLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashAndCarrySpreadTest {

    private static final BigDecimal MIN_NOTIONAL = new BigDecimal("50");

    private static OrderBook bookWithAsk(String exchange, BigDecimal ask) {
        var qty = new BigDecimal("10");
        return new OrderBook(exchange, "BTC_USDT", Instant.now(),
            List.of(new PriceLevel(ask.subtract(BigDecimal.ONE), qty)), List.of(new PriceLevel(ask, qty)));
    }

    private static PerpQuote perp(BigDecimal bid, BigDecimal fundingRatePct, long intervalHours) {
        Instant fundingTime = Instant.parse("2026-08-12T00:00:00Z");
        Instant nextFundingTime = fundingTime.plusSeconds(intervalHours * 3600);
        var qty = new BigDecimal("10");
        return new PerpQuote("BTC_USDT_PERP", bid, new PriceLevel(bid, qty),
            new PriceLevel(bid.add(BigDecimal.ONE), qty), fundingRatePct, fundingTime, nextFundingTime);
    }

    @Test
    void picksTheSpotExchangeWithBestNetCostNotJustBestRawPrice() {
        // Poloniex: ask 50000, fee 0,20% -> costo neto 50100.
        // NotBank: ask 49950 (nominalmente mejor), fee 0,49% -> costo neto ~50194,76 — pierde.
        List<CashAndCarrySpread.SpotCandidate> spotCandidates = List.of(
            new CashAndCarrySpread.SpotCandidate("Poloniex", bookWithAsk("Poloniex", new BigDecimal("50000"))),
            new CashAndCarrySpread.SpotCandidate("NotBank", bookWithAsk("NotBank", new BigDecimal("49950")))
        );
        PerpQuote perpQuote = perp(new BigDecimal("50100"), new BigDecimal("0.02"), 8);

        CashAndCarrySpread.Result r = CashAndCarrySpread.evaluate(
            "BTC", spotCandidates, "Poloniex", perpQuote, MIN_NOTIONAL).orElseThrow();

        assertEquals("Poloniex", r.spotExchange(), "Poloniex gana en neto pese al precio nominal peor");
        assertEquals(new BigDecimal("50000"), r.spotAskPrice());

        // basis: (50100 - 50000) / 50000 * 100 = 0.2%
        assertEquals(0, new BigDecimal("0.200000").compareTo(r.basisPct()));
        // fees de entrada: (0,20% + 0,075%) = 0,275%
        assertEquals(0, new BigDecimal("0.2750").compareTo(r.entryFeesPct()));
        // funding anualizado: 0,02% * (365*24/8) = 0,02% * 1095 = 21.9%
        assertEquals(0, new BigDecimal("21.9").compareTo(r.annualizedFundingPct().setScale(1, RoundingMode.HALF_UP)));
        // breakeven: max(0, 0.275 - 0.2) / 0.02 = 3.75 períodos
        assertEquals(0, new BigDecimal("3.75").compareTo(r.breakevenPeriodsIfPositive().orElseThrow()));
    }

    @Test
    void negativeFundingHasNoBreakevenPeriodCount() {
        List<CashAndCarrySpread.SpotCandidate> spotCandidates = List.of(
            new CashAndCarrySpread.SpotCandidate("Poloniex", bookWithAsk("Poloniex", new BigDecimal("50000")))
        );
        PerpQuote perpQuote = perp(new BigDecimal("50100"), new BigDecimal("-0.01"), 8);

        CashAndCarrySpread.Result r = CashAndCarrySpread.evaluate(
            "BTC", spotCandidates, "Poloniex", perpQuote, MIN_NOTIONAL).orElseThrow();

        assertTrue(r.breakevenPeriodsIfPositive().isEmpty(), "con funding negativo nunca se recupera solo con funding");
    }

    @Test
    void missingLiquidityOnEverySpotCandidateIsEmpty() {
        var thinBook = new OrderBook("Poloniex", "BTC_USDT", Instant.now(),
            List.of(new PriceLevel(new BigDecimal("49999"), new BigDecimal("0.0001"))),
            List.of(new PriceLevel(new BigDecimal("50000"), new BigDecimal("0.0001")))); // notional ~$5, no clears $50
        List<CashAndCarrySpread.SpotCandidate> spotCandidates = List.of(
            new CashAndCarrySpread.SpotCandidate("Poloniex", thinBook)
        );
        PerpQuote perpQuote = perp(new BigDecimal("50100"), new BigDecimal("0.02"), 8);

        Optional<CashAndCarrySpread.Result> result = CashAndCarrySpread.evaluate(
            "BTC", spotCandidates, "Poloniex", perpQuote, MIN_NOTIONAL);

        assertTrue(result.isEmpty());
    }
}
