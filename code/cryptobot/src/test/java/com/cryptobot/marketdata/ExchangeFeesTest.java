package com.cryptobot.marketdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExchangeFeesTest {

    @Test
    void returnsKnownFeesForEachConnectedExchange() {
        assertEquals(new BigDecimal("0.0020"), ExchangeFees.takerFee("Poloniex"));
        assertEquals(new BigDecimal("0.0060"), ExchangeFees.takerFee("NotBank"));
        assertEquals(new BigDecimal("0.0080"), ExchangeFees.takerFee("Buda"));
        assertEquals(new BigDecimal("0.0020"), ExchangeFees.takerFee("YoBit"));
    }

    @Test
    void unknownExchangeIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> ExchangeFees.takerFee("Binance"));
    }
}
