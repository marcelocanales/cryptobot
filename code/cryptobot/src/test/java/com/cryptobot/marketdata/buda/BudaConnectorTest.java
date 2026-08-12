package com.cryptobot.marketdata.buda;

import com.cryptobot.marketdata.ExchangeApiException;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudaConnectorTest {

    // Respuesta real, capturada con curl contra la API pública el 2026-08-12.
    private static final String REAL_RESPONSE = """
        {"order_book":{
          "asks":[["41573.0","90.0"],["41584.06","37.52134158"],["44444.0","0.796911"]],
          "bids":[["41450.0","0.59957297"],["41324.0","47.187"],["41296.77","44.17013543"]],
          "market_id":"LTC-CLP"
        }}
        """;

    private static final String NOT_FOUND_RESPONSE = """
        {"message":"Not found","code":"not_found"}
        """;

    @Test
    void parsesRealBudaResponse() {
        OrderBook book = new BudaConnector().parseOrderBook("ltc-clp", REAL_RESPONSE);

        assertEquals("Buda", book.exchange());
        assertEquals("ltc-clp", book.symbol());
        assertEquals(3, book.bids().size());
        assertEquals(3, book.asks().size());

        // bids: el primero debe ser el mejor (más alto) — Buda ya los manda ordenados así.
        assertEquals(new BigDecimal("41450.0"), book.bestBid().price());
        assertEquals(new BigDecimal("0.59957297"), book.bestBid().quantity());

        // asks: el primero debe ser el mejor (más bajo).
        assertEquals(new BigDecimal("41573.0"), book.bestAsk().price());
        assertEquals(new BigDecimal("90.0"), book.bestAsk().quantity());
    }

    @Test
    void missingOrderBookIsAnError() {
        assertThrows(ExchangeApiException.class,
            () -> new BudaConnector().parseOrderBook("xxx-clp", NOT_FOUND_RESPONSE));
    }

    // Respuesta real (recortada), capturada con curl contra GET /markets el 2026-08-12.
    private static final String REAL_MARKETS_RESPONSE = """
        {"markets":[
          {"id":"BTC-CLP","name":"btc-clp","base_currency":"BTC","quote_currency":"CLP","disabled":false},
          {"id":"ETH-CLP","name":"eth-clp","base_currency":"ETH","quote_currency":"CLP","disabled":false},
          {"id":"USDT-CLP","name":"usdt-clp","base_currency":"USDT","quote_currency":"CLP","disabled":false},
          {"id":"XXX-CLP","name":"xxx-clp","base_currency":"XXX","quote_currency":"CLP","disabled":true}
        ]}
        """;

    @Test
    void parsesRealMarketsResponseAndFiltersOutDisabled() {
        List<Market> markets = new BudaConnector().parseMarkets(REAL_MARKETS_RESPONSE);

        assertEquals(3, markets.size());
        assertTrue(markets.contains(new Market("BTC", "CLP", "btc-clp")));
        assertTrue(markets.contains(new Market("USDT", "CLP", "usdt-clp")));
        assertTrue(markets.stream().noneMatch(m -> m.symbol().equals("xxx-clp")));
    }
}
