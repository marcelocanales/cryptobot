package com.cryptobot.marketdata.poloniex;

import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // Respuesta real (recortada), capturada con curl contra GET /markets el 2026-08-12.
    private static final String REAL_MARKETS_RESPONSE = """
        [
          {"symbol":"DASH_BTC","baseCurrencyName":"DASH","quoteCurrencyName":"BTC","state":"NORMAL"},
          {"symbol":"ETH_BTC","baseCurrencyName":"ETH","quoteCurrencyName":"BTC","state":"NORMAL"},
          {"symbol":"ETH_USDT","baseCurrencyName":"ETH","quoteCurrencyName":"USDT","state":"NORMAL"},
          {"symbol":"XRPBULL_USDT","baseCurrencyName":"XRPBULL","quoteCurrencyName":"USDT","state":"PAUSE"}
        ]
        """;

    @Test
    void parsesRealMarketsResponseAndFiltersOutNonNormalState() {
        List<Market> markets = new PoloniexConnector().parseMarkets(REAL_MARKETS_RESPONSE);

        assertEquals(3, markets.size());
        assertTrue(markets.contains(new Market("ETH", "BTC", "ETH_BTC")));
        assertTrue(markets.contains(new Market("ETH", "USDT", "ETH_USDT")));
        assertTrue(markets.stream().noneMatch(m -> m.symbol().equals("XRPBULL_USDT")));
    }
}
