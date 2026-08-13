package com.cryptobot.marketdata.bitfinex;

import com.cryptobot.marketdata.ExchangeApiException;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitfinexConnectorTest {

    // Respuesta real (reordenada a propósito, para probar que el conector
    // ordena y no confía en el orden de la API), capturada con curl contra
    // GET /v2/book/tBTCUSD/P0?len=25 el 2026-08-13. Cantidad positiva = bid,
    // negativa = ask (valor absoluto para el tamaño real).
    private static final String REAL_RESPONSE = """
        [[63534,1,0.07869801],[63537,2,-0.0003845],[63532,1,0.07869947],
         [63540,1,-0.00022],[63535,1,0.07869678],[63536,3,-0.00587135]]
        """;

    @Test
    void parsesRealBitfinexResponseAndSortsBothSides() {
        OrderBook book = new BitfinexConnector().parseOrderBook("tBTCUSD", REAL_RESPONSE);

        assertEquals("Bitfinex", book.exchange());
        assertEquals("tBTCUSD", book.symbol());
        assertEquals(3, book.bids().size());
        assertEquals(3, book.asks().size());

        // bid: el de mayor precio entre los de cantidad positiva.
        assertEquals(new BigDecimal("63535"), book.bestBid().price());
        assertEquals(new BigDecimal("0.07869678"), book.bestBid().quantity());

        // ask: el de menor precio entre los de cantidad negativa, en valor absoluto.
        assertEquals(new BigDecimal("63536"), book.bestAsk().price());
        assertEquals(new BigDecimal("0.00587135"), book.bestAsk().quantity());
    }

    private static final String ERROR_RESPONSE = """
        ["error",10020,"symbol: invalid"]
        """;

    @Test
    void errorArrayBodyIsAnErrorEvenIfHttpStatusWereIgnored() {
        assertThrows(ExchangeApiException.class,
            () -> new BitfinexConnector().parseOrderBook("tNOTREAL", ERROR_RESPONSE));
    }

    // Respuesta real, capturada con curl contra GET /v2/conf/pub:list:pair:exchange
    // el 2026-08-13 — recortada a una muestra representativa.
    private static final String REAL_MARKETS_RESPONSE = """
        [["BTCUSD","ETHUSD","AAVE:USD","AAVE:UST","USTUSD","TESTBTC:TESTUSD"]]
        """;

    @Test
    void parsesRealMarketsResponseNormalizesUstAndFiltersTestPairs() {
        List<Market> markets = new BitfinexConnector().parseMarkets(REAL_MARKETS_RESPONSE);

        assertEquals(5, markets.size());
        assertTrue(markets.contains(new Market("BTC", "USD", "tBTCUSD")));
        assertTrue(markets.contains(new Market("ETH", "USD", "tETHUSD")));
        assertTrue(markets.contains(new Market("AAVE", "USD", "tAAVE:USD")));
        // UST -> USDT, tanto de moneda de cotización...
        assertTrue(markets.contains(new Market("AAVE", "USDT", "tAAVE:UST")));
        // ...como de base.
        assertTrue(markets.contains(new Market("USDT", "USD", "tUSTUSD")));
        assertTrue(markets.stream().noneMatch(m -> m.symbol().contains("TEST")));
    }
}
