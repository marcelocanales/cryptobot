package com.cryptobot.marketdata.yobit;

import com.cryptobot.marketdata.ExchangeApiException;
import com.cryptobot.marketdata.OrderBook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YobitConnectorTest {

    // Respuesta real, capturada con curl contra la API pública el 2026-08-12.
    private static final String REAL_RESPONSE = """
        {"ltc_usdt":{
          "asks":[[45.68811722,0.00052619],[45.69251,0.00002379],[45.7071258,0.00137764]],
          "bids":[[45.2532318,0.00135573],[45.2078424,0.00136373],[45.162453,0.00137212]]
        }}
        """;

    private static final String INVALID_PAIR_RESPONSE = """
        {"success":0,"error":"Invalid pair name: xxx_usd"}
        """;

    @Test
    void parsesRealYobitResponse() {
        OrderBook book = new YobitConnector().parseOrderBook("ltc_usdt", REAL_RESPONSE);

        assertEquals("YoBit", book.exchange());
        assertEquals("ltc_usdt", book.symbol());
        assertEquals(3, book.bids().size());
        assertEquals(3, book.asks().size());

        // bids: el primero debe ser el mejor (más alto) — YoBit ya los manda ordenados así.
        assertEquals(new BigDecimal("45.2532318"), book.bestBid().price());
        assertEquals(new BigDecimal("0.00135573"), book.bestBid().quantity());

        // asks: el primero debe ser el mejor (más bajo).
        assertEquals(new BigDecimal("45.68811722"), book.bestAsk().price());
        assertEquals(new BigDecimal("0.00052619"), book.bestAsk().quantity());
    }

    @Test
    void invalidPairIsAnErrorEvenWithHttp200() {
        assertThrows(ExchangeApiException.class,
            () -> new YobitConnector().parseOrderBook("xxx_usd", INVALID_PAIR_RESPONSE));
    }
}
