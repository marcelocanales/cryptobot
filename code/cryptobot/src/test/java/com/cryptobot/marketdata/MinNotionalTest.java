package com.cryptobot.marketdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinNotionalTest {

    @Test
    void knownCurrenciesHaveTheirOwnThreshold() {
        assertEquals(new BigDecimal("0.00078"), MinNotional.forCurrency("BTC"));
        assertEquals(new BigDecimal("47500"), MinNotional.forCurrency("CLP"));
    }

    @Test
    void anythingElseDefaultsToTheUsdtThreshold() {
        assertEquals(new BigDecimal("50"), MinNotional.forCurrency("USDT"));
        assertEquals(new BigDecimal("50"), MinNotional.forCurrency("USDC"));
        assertEquals(new BigDecimal("50"), MinNotional.forCurrency("COP")); // no tiene umbral propio todavía
    }
}
