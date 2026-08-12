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
        assertTrue(NetSpread.evaluate("Poloniex", "NotBank", null, withBid).isEmpty());
        assertTrue(NetSpread.evaluate("Poloniex", "NotBank", withBid, null).isEmpty());
    }

    @Test
    void grossSpreadThatDoesNotCoverBothFeesIsNotNetPositive() {
        // Poloniex 0,20% + YoBit 0,20% = 0,40% de fees. Bruto 0,30% no alcanza.
        var buyAt = new PriceLevel(new BigDecimal("100.00"), new BigDecimal("1"));
        var sellAt = new PriceLevel(new BigDecimal("100.30"), new BigDecimal("1"));

        Optional<NetSpread.Result> result = NetSpread.evaluate("YoBit", "Poloniex", buyAt, sellAt);

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

        NetSpread.Result r = NetSpread.evaluate("YoBit", "Poloniex", buyAt, sellAt).orElseThrow();

        assertEquals(new BigDecimal("0.500000"), r.grossPct());
        assertTrue(r.isPositive());
        assertEquals(new BigDecimal("0.100000"), r.netPct());
    }
}
