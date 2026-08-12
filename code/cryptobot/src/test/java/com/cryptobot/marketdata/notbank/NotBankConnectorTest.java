package com.cryptobot.marketdata.notbank;

import com.cryptobot.marketdata.OrderBook;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotBankConnectorTest {

    // Respuesta real de POST /AP/GetL2Snapshot para LTCUSDT (InstrumentId 63),
    // capturada con curl el 2026-08-12. El mejor bid (45.32, cantidad 16.276)
    // coincide con lo que se veía a mano en la UI de NotBank esa misma tarde.
    private static final String REAL_RESPONSE = """
        [
          [30878691, 1, 1786506349330, 0, 45.32, 1, 45.32, 63, 16.276, 0],
          [30878691, 1, 1786506349330, 0, 45.32, 1, 45.31, 63, 18.537, 0],
          [30878691, 1, 1786506349330, 0, 45.32, 1, 45.3, 63, 21.088, 0],
          [30878691, 1, 1786506349330, 0, 45.32, 1, 45.4984, 63, 24.886, 1],
          [30878691, 1, 1786506349330, 0, 45.32, 1, 45.4992, 63, 24.867, 1],
          [30878691, 1, 1786506349330, 0, 45.32, 1, 45.5081, 63, 24.972, 1]
        ]
        """;

    @Test
    void parsesRealNotBankL2Snapshot() throws Exception {
        JsonNode rows = new ObjectMapper().readTree(REAL_RESPONSE);
        OrderBook book = new NotBankConnector().parseOrderBook("LTCUSDT", rows);

        assertEquals("NotBank", book.exchange());
        assertEquals("LTCUSDT", book.symbol());
        assertEquals(3, book.bids().size());
        assertEquals(3, book.asks().size());

        // side=0 son bids: el más alto debe quedar primero después de ordenar.
        assertEquals(new BigDecimal("45.32"), book.bestBid().price());
        assertEquals(new BigDecimal("16.276"), book.bestBid().quantity());

        // side=1 son asks: el más bajo debe quedar primero después de ordenar.
        assertEquals(new BigDecimal("45.4984"), book.bestAsk().price());
        assertEquals(new BigDecimal("24.886"), book.bestAsk().quantity());
    }
}
