package com.cryptobot.triangular;

import com.cryptobot.marketdata.CrossVenue;
import com.cryptobot.marketdata.ExchangeConnector;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PriceLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossTriangleSpreadTest {

    private record FakeConnector(String exchangeName) implements ExchangeConnector {
        @Override
        public OrderBook fetchOrderBook(String symbol) {
            throw new UnsupportedOperationException("no hace falta para este test");
        }

        @Override
        public List<Market> fetchMarkets() {
            throw new UnsupportedOperationException("no hace falta para este test");
        }
    }

    // Nombres reales — ExchangeFees solo conoce Poloniex/NotBank/Buda/YoBit.
    private static final FakeConnector POLONIEX = new FakeConnector("Poloniex");
    private static final FakeConnector NOTBANK = new FakeConnector("NotBank");

    private static OrderBook book(String exchange, String symbol, BigDecimal bid, BigDecimal ask) {
        var qty = new BigDecimal("10");
        return new OrderBook(exchange, symbol, Instant.now(),
            List.of(new PriceLevel(bid, qty)), List.of(new PriceLevel(ask, qty)));
    }

    @Test
    void picksTheExchangeWithBestNetResultNotJustBestRawPrice() {
        // La pata USDT->BTC tiene 2 candidatos: NotBank ofrece un ask más bajo
        // (mejor precio bruto: 49990 < 50000), pero su fee (0,49%, CRYPTO-FIAT)
        // es más alta que la de Poloniex (0,20%) — en neto, Poloniex gana.
        CrossTriangle triangle = new CrossTriangle(
            "USDT", "BTC", "ETH",
            List.of(
                new CrossVenue(POLONIEX, new Market("BTC", "USDT", "BTC_USDT")),
                new CrossVenue(NOTBANK, new Market("BTC", "USDT", "BTCUSDT"))
            ),
            List.of(new CrossVenue(POLONIEX, new Market("ETH", "BTC", "ETH_BTC"))),
            List.of(new CrossVenue(POLONIEX, new Market("ETH", "USDT", "ETH_USDT")))
        );

        Map<String, OrderBook> books = new HashMap<>();
        books.put(CrossTriangleSpread.bookKey("Poloniex", "BTC_USDT"),
            book("Poloniex", "BTC_USDT", new BigDecimal("49980"), new BigDecimal("50000")));
        books.put(CrossTriangleSpread.bookKey("NotBank", "BTCUSDT"),
            book("NotBank", "BTCUSDT", new BigDecimal("49970"), new BigDecimal("49990")));
        books.put(CrossTriangleSpread.bookKey("Poloniex", "ETH_BTC"),
            book("Poloniex", "ETH_BTC", new BigDecimal("0.0499"), new BigDecimal("0.05")));
        books.put(CrossTriangleSpread.bookKey("Poloniex", "ETH_USDT"),
            book("Poloniex", "ETH_USDT", new BigDecimal("2510"), new BigDecimal("2511")));

        CrossTriangleSpread.Result r = CrossTriangleSpread.evaluateForward(triangle, books).orElseThrow();

        // reproduce el ejemplo ilustrativo del doc de estrategia (+0,4% bruto)
        assertEquals(0, new BigDecimal("0.4").compareTo(r.grossPct().setScale(1, RoundingMode.HALF_UP)));

        CrossTriangleSpread.Leg firstLeg = r.legs().get(0);
        assertEquals("Poloniex", firstLeg.exchange(), "Poloniex gana en neto pese a que NotBank tenía mejor precio bruto");
        assertEquals(new BigDecimal("50000"), firstLeg.price());
    }

    @Test
    void missingLiquidityInEveryCandidateForALegIsEmpty() {
        CrossTriangle triangle = new CrossTriangle(
            "USDT", "BTC", "ETH",
            List.of(new CrossVenue(POLONIEX, new Market("BTC", "USDT", "BTC_USDT"))),
            List.of(new CrossVenue(POLONIEX, new Market("ETH", "BTC", "ETH_BTC"))),
            List.of(new CrossVenue(POLONIEX, new Market("ETH", "USDT", "ETH_USDT")))
        );

        Map<String, OrderBook> books = new HashMap<>();
        books.put(CrossTriangleSpread.bookKey("Poloniex", "BTC_USDT"),
            book("Poloniex", "BTC_USDT", new BigDecimal("49980"), new BigDecimal("50000")));
        books.put(CrossTriangleSpread.bookKey("Poloniex", "ETH_BTC"),
            book("Poloniex", "ETH_BTC", new BigDecimal("0.0499"), new BigDecimal("0.05")));
        // falta el book de ETH_USDT por completo -> esa pata no tiene ningún candidato con datos

        Optional<CrossTriangleSpread.Result> result = CrossTriangleSpread.evaluateForward(triangle, books);

        assertTrue(result.isEmpty());
    }

    @Test
    void backwardDirectionUsesTheOppositeLegOrder() {
        CrossTriangle triangle = new CrossTriangle(
            "USDT", "BTC", "ETH",
            List.of(new CrossVenue(POLONIEX, new Market("BTC", "USDT", "BTC_USDT"))),
            List.of(new CrossVenue(POLONIEX, new Market("ETH", "BTC", "ETH_BTC"))),
            List.of(new CrossVenue(POLONIEX, new Market("ETH", "USDT", "ETH_USDT")))
        );

        Map<String, OrderBook> books = new HashMap<>();
        books.put(CrossTriangleSpread.bookKey("Poloniex", "BTC_USDT"),
            book("Poloniex", "BTC_USDT", new BigDecimal("49980"), new BigDecimal("50000")));
        books.put(CrossTriangleSpread.bookKey("Poloniex", "ETH_BTC"),
            book("Poloniex", "ETH_BTC", new BigDecimal("0.0499"), new BigDecimal("0.05")));
        books.put(CrossTriangleSpread.bookKey("Poloniex", "ETH_USDT"),
            book("Poloniex", "ETH_USDT", new BigDecimal("2510"), new BigDecimal("2511")));

        CrossTriangleSpread.Result forward = CrossTriangleSpread.evaluateForward(triangle, books).orElseThrow();
        CrossTriangleSpread.Result backward = CrossTriangleSpread.evaluateBackward(triangle, books).orElseThrow();

        assertEquals(List.of("USDT", "BTC", "ETH", "USDT"), forward.path());
        assertEquals(List.of("USDT", "ETH", "BTC", "USDT"), backward.path());
    }
}
