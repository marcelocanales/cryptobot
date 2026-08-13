package com.cryptobot.marketdata.binance;

import com.cryptobot.marketdata.ExchangeApiException;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinanceConnectorTest {

    // Respuesta real, capturada con curl contra GET /api/v3/depth el 2026-08-13.
    private static final String REAL_RESPONSE = """
        {"lastUpdateId":98503781841,"bids":[["63880.27000000","4.83570000"],["63880.26000000","0.00555000"],["63880.25000000","0.00016000"]],"asks":[["63880.28000000","1.43712000"],["63880.29000000","0.00106000"],["63880.30000000","0.00024000"]]}
        """;

    // Respuesta real (recortada a los campos que el parser usa), capturada
    // con curl contra GET /api/v3/depth?symbol=XXXINVALIDXXX el 2026-08-13
    // — a diferencia de CoinEx/YoBit, Binance responde HTTP no-200 (400)
    // para un símbolo inválido, no HTTP 200 con un campo "code".
    private static final String INVALID_SYMBOL_RESPONSE = """
        {"code":-1121,"msg":"Invalid symbol."}
        """;

    @Test
    void parsesRealBinanceResponse() {
        OrderBook book = new BinanceConnector().parseOrderBook("BTCUSDT", REAL_RESPONSE);

        assertEquals("Binance", book.exchange());
        assertEquals("BTCUSDT", book.symbol());
        assertEquals(3, book.bids().size());
        assertEquals(3, book.asks().size());

        // bids: el primero debe ser el mejor (más alto) — Binance ya los manda ordenados así.
        assertEquals(new BigDecimal("63880.27000000"), book.bestBid().price());
        assertEquals(new BigDecimal("4.83570000"), book.bestBid().quantity());

        // asks: el primero debe ser el mejor (más bajo).
        assertEquals(new BigDecimal("63880.28000000"), book.bestAsk().price());
        assertEquals(new BigDecimal("1.43712000"), book.bestAsk().quantity());
    }

    @Test
    void invalidSymbolBodyDoesNotCrashParsingIfEverReachedDirectly() {
        // parseOrderBook nunca debería recibir esto en la práctica (fetchOrderBook
        // ya corta por status code no-200 antes de llegar acá) — pero si igual
        // se le pasa, debe fallar limpio por falta de bids/asks, no con un NPE.
        assertThrows(ExchangeApiException.class,
            () -> new BinanceConnector().parseOrderBook("XXXINVALIDXXX", INVALID_SYMBOL_RESPONSE));
    }

    // Respuesta real (recortada a los campos que el parser usa — la real trae
    // muchos más, filtros de precio/lote, permisos, etc.), capturada con curl
    // contra GET /api/v3/exchangeInfo?symbols=["BTCUSDT","ETHUSDT"] el 2026-08-13.
    private static final String REAL_MARKETS_RESPONSE = """
        {"timezone":"UTC","serverTime":1786633087752,"symbols":[
          {"symbol":"BTCUSDT","status":"TRADING","baseAsset":"BTC","quoteAsset":"USDT"},
          {"symbol":"ETHUSDT","status":"TRADING","baseAsset":"ETH","quoteAsset":"USDT"},
          {"symbol":"XXXUSDT","status":"BREAK","baseAsset":"XXX","quoteAsset":"USDT"}
        ]}
        """;

    @Test
    void parsesRealMarketsResponseAndFiltersOutNonTradingStatus() {
        List<Market> markets = new BinanceConnector().parseMarkets(REAL_MARKETS_RESPONSE);

        assertEquals(2, markets.size());
        assertTrue(markets.contains(new Market("BTC", "USDT", "BTCUSDT")));
        assertTrue(markets.contains(new Market("ETH", "USDT", "ETHUSDT")));
        assertTrue(markets.stream().noneMatch(m -> m.symbol().equals("XXXUSDT")));
    }
}
