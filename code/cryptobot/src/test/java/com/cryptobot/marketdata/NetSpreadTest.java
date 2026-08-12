package com.cryptobot.marketdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetSpreadTest {

    @Test
    void missingLiquidityOnEitherSideIsEmpty() {
        var withBid = new PriceLevel(new BigDecimal("100"), new BigDecimal("1"));
        assertTrue(NetSpread.evaluate("Poloniex", "NotBank", "USDT", null, withBid).isEmpty());
        assertTrue(NetSpread.evaluate("Poloniex", "NotBank", "USDT", withBid, null).isEmpty());
    }

    @Test
    void grossSpreadThatDoesNotCoverBothFeesIsNotNetPositive() {
        // Poloniex 0,20% + YoBit 0,20% = 0,40% de fees. Bruto 0,30% no alcanza.
        var buyAt = new PriceLevel(new BigDecimal("100.00"), new BigDecimal("1"));
        var sellAt = new PriceLevel(new BigDecimal("100.30"), new BigDecimal("1"));

        Optional<NetSpread.Result> result = NetSpread.evaluate("YoBit", "Poloniex", "USDT", buyAt, sellAt);

        assertTrue(result.isPresent());
        NetSpread.Result r = result.get();
        assertEquals(new BigDecimal("0.300000"), r.grossPct());
        assertEquals(new BigDecimal("0.4000"), r.feesPct());
        assertFalse(r.isPositive());
    }

    @Test
    void grossSpreadThatCoversBothFeesIsNetPositive() {
        var buyAt = new PriceLevel(new BigDecimal("100.00"), new BigDecimal("1"));
        var sellAt = new PriceLevel(new BigDecimal("100.50"), new BigDecimal("1"));

        NetSpread.Result r = NetSpread.evaluate("YoBit", "Poloniex", "USDT", buyAt, sellAt).orElseThrow();

        assertEquals(new BigDecimal("0.500000"), r.grossPct());
        assertTrue(r.isPositive());
        assertEquals(new BigDecimal("0.100000"), r.netPct());
    }

    @Test
    void notBankFeeChangesWithTheQuoteCurrency() {
        // Mismo bruto, misma otra pata (Buda 0,80%) — cambia solo la fee de NotBank
        // según si el par cotiza en algo fiat-like (USDT/CLP) o en otra cripto (BTC).
        var buyAt = new PriceLevel(new BigDecimal("100.00"), new BigDecimal("1"));
        var sellAt = new PriceLevel(new BigDecimal("101.00"), new BigDecimal("1"));

        NetSpread.Result cryptoFiat = NetSpread.evaluate("Buda", "NotBank", "CLP", buyAt, sellAt).orElseThrow();
        NetSpread.Result cryptoCrypto = NetSpread.evaluate("Buda", "NotBank", "BTC", buyAt, sellAt).orElseThrow();

        assertEquals(new BigDecimal("1.2900"), cryptoFiat.feesPct());   // 0,80% + 0,49%
        assertEquals(new BigDecimal("0.9400"), cryptoCrypto.feesPct()); // 0,80% + 0,14%
    }
}
