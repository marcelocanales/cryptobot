package com.cryptobot.funding;

import com.cryptobot.marketdata.PerpQuote;
import com.cryptobot.marketdata.PriceLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundingCrossExchangeSpreadTest {

    private static final BigDecimal MIN_NOTIONAL = new BigDecimal("50");

    private static PerpQuote perp(BigDecimal fundingRatePct, long intervalHours, BigDecimal bidQty, BigDecimal askQty) {
        Instant fundingTime = Instant.parse("2026-08-13T00:00:00Z");
        Instant nextFundingTime = fundingTime.plusSeconds(intervalHours * 3600);
        BigDecimal price = new BigDecimal("100");
        return new PerpQuote("X", price, new PriceLevel(price, bidQty), new PriceLevel(price, askQty),
            fundingRatePct, fundingTime, nextFundingTime);
    }

    private static FundingCrossExchangeSpread.PerpCandidate candidate(String exchange, PerpQuote quote) {
        return new FundingCrossExchangeSpread.PerpCandidate(exchange, quote);
    }

    @Test
    void picksHighestAnnualizedFundingAsShortAndLowestAsLong() {
        // Poloniex: 0,01% cada 8h -> anualizado 10,95%. Bitfinex: 0,03% cada 8h -> 32,85%.
        List<FundingCrossExchangeSpread.PerpCandidate> candidates = List.of(
            candidate("Poloniex", perp(new BigDecimal("0.01"), 8, new BigDecimal("10"), new BigDecimal("10"))),
            candidate("Bitfinex", perp(new BigDecimal("0.03"), 8, new BigDecimal("10"), new BigDecimal("10")))
        );

        FundingCrossExchangeSpread.Result r = FundingCrossExchangeSpread.evaluate("BTC", candidates, MIN_NOTIONAL)
            .orElseThrow();

        assertEquals("Bitfinex", r.shortExchange(), "el de mayor funding se cobra en corto");
        assertEquals("Poloniex", r.longExchange());
        assertEquals(0, new BigDecimal("21.90").compareTo(r.annualizedDifferentialPct()));
        // fees: Poloniex 0,06% + Bitfinex 0% = 0,06%
        assertEquals(0, new BigDecimal("0.06").compareTo(r.entryFeesPct()));
        // breakeven: 0,06 / (21,9/8760) = 24h
        assertEquals(0, new BigDecimal("24").compareTo(r.breakevenHoursIfPositive().orElseThrow()));
    }

    @Test
    void picksTheExtremesAmongThreeCandidates() {
        // El candidato del medio (funding intermedio) nunca se elige, así que
        // su fee nunca se consulta — puede ser un exchange ficticio sin fee
        // registrada en ExchangeFees, sin romper el test. Los dos extremos sí
        // se eligen, así que tienen que ser exchanges reales con fee conocida.
        List<FundingCrossExchangeSpread.PerpCandidate> candidates = List.of(
            candidate("Poloniex", perp(new BigDecimal("0.01"), 8, new BigDecimal("10"), new BigDecimal("10"))),
            candidate("SinFeeRegistrada", perp(new BigDecimal("0.03"), 8, new BigDecimal("10"), new BigDecimal("10"))),
            candidate("Bitfinex", perp(new BigDecimal("0.05"), 8, new BigDecimal("10"), new BigDecimal("10")))
        );

        FundingCrossExchangeSpread.Result r = FundingCrossExchangeSpread.evaluate("BTC", candidates, MIN_NOTIONAL)
            .orElseThrow();

        assertEquals("Bitfinex", r.shortExchange(), "el de mayor funding de los 3");
        assertEquals("Poloniex", r.longExchange(), "el de menor funding de los 3");
    }

    @Test
    void annualizesWithEachCandidatesOwnInterval() {
        // Poloniex: 0,01% cada 8h -> anualizado 10,95%.
        // Bitfinex: 0,02% cada 24h -> anualizado 7,30% (0,02 * 365).
        // Comparado CRUDO por período, Bitfinex (0,02) > Poloniex (0,01) —
        // pero anualizado, Poloniex gana (su intervalo es 3x más corto).
        List<FundingCrossExchangeSpread.PerpCandidate> candidates = List.of(
            candidate("Poloniex", perp(new BigDecimal("0.01"), 8, new BigDecimal("10"), new BigDecimal("10"))),
            candidate("Bitfinex", perp(new BigDecimal("0.02"), 24, new BigDecimal("10"), new BigDecimal("10")))
        );

        FundingCrossExchangeSpread.Result r = FundingCrossExchangeSpread.evaluate("BTC", candidates, MIN_NOTIONAL)
            .orElseThrow();

        assertEquals("Poloniex", r.shortExchange(), "anualizado, Poloniex tiene mayor funding aunque su tasa cruda sea menor");
        assertEquals("Bitfinex", r.longExchange());
    }

    @Test
    void insufficientLiquidityCandidateIsExcluded() {
        var thinBid = new BigDecimal("0.01"); // notional ~$1, no cubre los $50 mínimos
        List<FundingCrossExchangeSpread.PerpCandidate> candidates = List.of(
            candidate("Poloniex", perp(new BigDecimal("0.01"), 8, thinBid, new BigDecimal("10"))),
            candidate("Bitfinex", perp(new BigDecimal("0.03"), 8, new BigDecimal("10"), new BigDecimal("10")))
        );

        // Poloniex no puede ser el corto (bid insuficiente) pero sí puede ser el largo (ask ok).
        FundingCrossExchangeSpread.Result r = FundingCrossExchangeSpread.evaluate("BTC", candidates, MIN_NOTIONAL)
            .orElseThrow();

        assertEquals("Bitfinex", r.shortExchange());
        assertEquals("Poloniex", r.longExchange());
    }

    @Test
    void onlyOneExchangeWithEnoughLiquidityOnBothSidesIsEmpty() {
        var thinBid = new BigDecimal("0.01");
        var thinAsk = new BigDecimal("0.01");
        List<FundingCrossExchangeSpread.PerpCandidate> candidates = List.of(
            candidate("Poloniex", perp(new BigDecimal("0.01"), 8, new BigDecimal("10"), new BigDecimal("10"))),
            candidate("Bitfinex", perp(new BigDecimal("0.03"), 8, thinBid, thinAsk))
        );

        // Bitfinex queda sin liquidez de ningún lado -> solo Poloniex es elegible en ambos,
        // pero no puede ser corto y largo a la vez.
        Optional<FundingCrossExchangeSpread.Result> result =
            FundingCrossExchangeSpread.evaluate("BTC", candidates, MIN_NOTIONAL);

        assertTrue(result.isEmpty());
    }

    @Test
    void equalFundingHasNoBreakeven() {
        List<FundingCrossExchangeSpread.PerpCandidate> candidates = List.of(
            candidate("Poloniex", perp(new BigDecimal("0.02"), 8, new BigDecimal("10"), new BigDecimal("10"))),
            candidate("Bitfinex", perp(new BigDecimal("0.02"), 8, new BigDecimal("10"), new BigDecimal("10")))
        );

        FundingCrossExchangeSpread.Result r = FundingCrossExchangeSpread.evaluate("BTC", candidates, MIN_NOTIONAL)
            .orElseThrow();

        assertTrue(r.breakevenHoursIfPositive().isEmpty(), "sin diferencial no se recupera nunca la entrada");
    }
}
