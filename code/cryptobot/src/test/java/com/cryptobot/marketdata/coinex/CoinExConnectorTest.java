package com.cryptobot.marketdata.coinex;

import com.cryptobot.marketdata.ExchangeApiException;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinExConnectorTest {

    // Respuesta real, capturada con curl contra la API pública el 2026-08-13.
    private static final String REAL_RESPONSE = """
        {"code":0,"data":{"depth":{
          "asks":[["63576","0.06065251"],["63583","0.00238002"],["63588","0.00157293"]],
          "bids":[["63575","0.05786348"],["63571","0.04719132"],["63570","0.00018"]],
          "checksum":2848867261,"last":"63575","updated_at":1786582074591
        },"is_full":true,"market":"BTCUSDT"},"message":"OK"}
        """;

    private static final String INVALID_PARAM_RESPONSE = """
        {"code":3639,"data":{},"message":"Invalid Parameter"}
        """;

    @Test
    void parsesRealCoinExResponse() {
        OrderBook book = new CoinExConnector().parseOrderBook("BTCUSDT", REAL_RESPONSE);

        assertEquals("CoinEx", book.exchange());
        assertEquals("BTCUSDT", book.symbol());
        assertEquals(Instant.ofEpochMilli(1786582074591L), book.timestamp());
        assertEquals(3, book.bids().size());
        assertEquals(3, book.asks().size());

        // bids: el primero debe ser el mejor (más alto) — CoinEx ya los manda ordenados así.
        assertEquals(new BigDecimal("63575"), book.bestBid().price());
        assertEquals(new BigDecimal("0.05786348"), book.bestBid().quantity());

        // asks: el primero debe ser el mejor (más bajo).
        assertEquals(new BigDecimal("63576"), book.bestAsk().price());
        assertEquals(new BigDecimal("0.06065251"), book.bestAsk().quantity());
    }

    @Test
    void codeDifferentFromZeroIsAnErrorEvenWithHttp200() {
        assertThrows(ExchangeApiException.class,
            () -> new CoinExConnector().parseOrderBook("XXXUSDT", INVALID_PARAM_RESPONSE));
    }

    // Respuesta real (recortada), capturada con curl contra GET /v2/spot/market el 2026-08-13.
    private static final String REAL_MARKETS_RESPONSE = """
        {"code":0,"data":[
          {"base_ccy":"BTC","quote_ccy":"USDT","market":"BTCUSDT","status":"online","taker_fee_rate":"0.002","maker_fee_rate":"0.002"},
          {"base_ccy":"ETH","quote_ccy":"USDT","market":"ETHUSDT","status":"online","taker_fee_rate":"0.002","maker_fee_rate":"0.002"},
          {"base_ccy":"LTC","quote_ccy":"BTC","market":"LTCBTC","status":"online","taker_fee_rate":"0.003","maker_fee_rate":"0.003"},
          {"base_ccy":"XXX","quote_ccy":"USDT","market":"XXXUSDT","status":"delisted","taker_fee_rate":"0.003","maker_fee_rate":"0.003"}
        ],"message":"OK"}
        """;

    @Test
    void parsesRealMarketsResponseAndFiltersOutNonOnlineStatus() {
        List<Market> markets = new CoinExConnector().parseMarkets(REAL_MARKETS_RESPONSE);

        assertEquals(3, markets.size());
        assertTrue(markets.contains(new Market("BTC", "USDT", "BTCUSDT")));
        assertTrue(markets.contains(new Market("LTC", "BTC", "LTCBTC")));
        assertTrue(markets.stream().noneMatch(m -> m.symbol().equals("XXXUSDT")));
    }
}
