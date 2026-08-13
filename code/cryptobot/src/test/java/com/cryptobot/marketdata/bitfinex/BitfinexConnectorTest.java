package com.cryptobot.marketdata.bitfinex;

import com.cryptobot.marketdata.ExchangeApiException;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PerpQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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

    // Respuesta real, capturada con curl contra GET /conf/pub:list:pair:futures
    // el 2026-08-13.
    private static final String REAL_PERP_SYMBOLS_RESPONSE = """
        [["BTCF0:USTF0", "ETHF0:USTF0", "LTCF0:BTCF0", "TESTBTCF0:TESTUSDTF0"]]
        """;

    @Test
    void parsesRealPerpSymbolsAndFiltersOutTestPairs() {
        List<String> symbols = new BitfinexConnector().parsePerpSymbols(REAL_PERP_SYMBOLS_RESPONSE);

        assertEquals(List.of("tBTCF0:USTF0", "tETHF0:USTF0", "tLTCF0:BTCF0"), symbols);
    }

    // Respuestas reales, capturadas con curl el 2026-08-13 para tBTCF0:USTF0.
    private static final String REAL_PERP_TICKER_RESPONSE = """
        [63504,8.682083,63512,67.81524493,-423,-0.0066164,63509,462.32025183,64530,63350,1562164542332]
        """;

    private static final String REAL_PERP_DERIV_RESPONSE = """
        [["tBTCF0:USTF0",1786586583000,null,63514.467255465,63470.5,null,64745826.62409877,null,
          1786608000000,0.00016169,2362,null,0.00016712,null,null,63470.64,null,null,
          8872.19154966,null,null,null,0.0005,0.0025]]
        """;

    @Test
    void parsesRealPerpQuoteCombiningTickerAndDerivStatus() {
        PerpQuote quote = new BitfinexConnector()
            .parsePerpQuote("tBTCF0:USTF0", REAL_PERP_TICKER_RESPONSE, REAL_PERP_DERIV_RESPONSE);

        assertEquals("tBTCF0:USTF0", quote.symbol());
        assertEquals(new BigDecimal("63470.64"), quote.markPrice());
        assertEquals(new BigDecimal("63504"), quote.bestBid().price());
        assertEquals(new BigDecimal("8.682083"), quote.bestBid().quantity());
        assertEquals(new BigDecimal("63512"), quote.bestAsk().price());
        assertEquals(new BigDecimal("67.81524493"), quote.bestAsk().quantity());
        // CURRENT_FUNDING=0.00016712 (fracción) -> 0.016712 (porcentaje)
        assertEquals(0, new BigDecimal("0.016712").compareTo(quote.fundingRatePct()));
        assertEquals(Instant.ofEpochMilli(1786608000000L), quote.nextFundingTime());
        // fundingTime se DERIVA (nextFundingTime - 8h), no viene medido de la API — ver javadoc.
        assertEquals(Instant.ofEpochMilli(1786608000000L).minus(Duration.ofHours(8)), quote.fundingTime());
        assertEquals(8, quote.fundingInterval().toHours());
    }
}
