package com.cryptobot.marketdata.poloniex;

import com.cryptobot.marketdata.OrderBook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoloniexConnectorTest {

    // Respuesta real, capturada con curl contra la API pública el 2026-08-12.
    private static final String REAL_RESPONSE = """
        {"bids":["45.355","1.488081","45.354","76.953898","45.353","24.830994"],
         "asks":["45.455","29.486764","45.460","26.401252","45.496","9.346616"],
         "scale":"0.001","time":1786505961799,"ts":1786505962289}
        """;

    @Test
    void parsesRealPoloniexResponse() {
        OrderBook book = new PoloniexConnector().parseOrderBook("LTC_USDT", REAL_RESPONSE);

        assertEquals("Poloniex", book.exchange());
        assertEquals("LTC_USDT", book.symbol());
        assertEquals(3, book.bids().size());
        assertEquals(3, book.asks().size());

        // bids: el primero debe ser el mejor (más alto) — Poloniex ya los manda ordenados así.
        assertEquals(new BigDecimal("45.355"), book.bestBid().price());
        assertEquals(new BigDecimal("1.488081"), book.bestBid().quantity());

        // asks: el primero debe ser el mejor (más bajo).
        assertEquals(new BigDecimal("45.455"), book.bestAsk().price());
        assertEquals(new BigDecimal("29.486764"), book.bestAsk().quantity());
    }
}
