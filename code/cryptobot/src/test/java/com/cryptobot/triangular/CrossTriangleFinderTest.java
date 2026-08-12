package com.cryptobot.triangular;

import com.cryptobot.marketdata.CrossVenue;
import com.cryptobot.marketdata.ExchangeConnector;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossTriangleFinderTest {

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

    private static final FakeConnector EXCHANGE_1 = new FakeConnector("Exchange1");
    private static final FakeConnector EXCHANGE_2 = new FakeConnector("Exchange2");

    private static CrossVenue venue(FakeConnector exchange, String base, String quote, String symbol) {
        return new CrossVenue(exchange, new Market(base, quote, symbol));
    }

    @Test
    void findsANecessityCycleThatOnlyExistsCombiningTwoExchanges() {
        // Exchange1 tiene BTC/USDT y ETH/USDT, pero NO ETH/BTC.
        // Exchange2 tiene ETH/BTC, pero no los otros dos.
        // El triángulo USDT-BTC-ETH solo existe combinando ambos.
        List<CrossVenue> venues = List.of(
            venue(EXCHANGE_1, "BTC", "USDT", "BTC_USDT"),
            venue(EXCHANGE_1, "ETH", "USDT", "ETH_USDT"),
            venue(EXCHANGE_2, "ETH", "BTC", "ETH_BTC")
        );

        List<CrossTriangle> found = CrossTriangleFinder.find(venues, "USDT");

        assertEquals(1, found.size());
        CrossTriangle t = found.get(0);
        assertTrue(t.isNecessityCycle(), "ningún exchange individual tiene las 3 patas juntas");
        assertEquals(1, t.venuesAB().size());
        assertEquals("Exchange1", t.venuesAB().get(0).exchangeName());
        assertEquals(1, t.venuesBC().size());
        assertEquals("Exchange2", t.venuesBC().get(0).exchangeName());
    }

    @Test
    void aTriangleFullyOfferedByOneExchangeIsNotANecessityCycleEvenIfAnotherExchangeAddsAnOption() {
        // Exchange1 tiene los 3 mercados completos. Exchange2 también ofrece
        // ETH/BTC (a otro precio) — suma una opción, pero no hace falta.
        List<CrossVenue> venues = List.of(
            venue(EXCHANGE_1, "BTC", "USDT", "BTC_USDT"),
            venue(EXCHANGE_1, "ETH", "USDT", "ETH_USDT"),
            venue(EXCHANGE_1, "ETH", "BTC", "ETH_BTC"),
            venue(EXCHANGE_2, "ETH", "BTC", "ETH_BTC")
        );

        List<CrossTriangle> found = CrossTriangleFinder.find(venues, "USDT");

        assertEquals(1, found.size());
        CrossTriangle t = found.get(0);
        assertFalse(t.isNecessityCycle(), "Exchange1 solo ya tiene las 3 patas");
        assertEquals(2, t.venuesBC().size(), "la pata BTC-ETH tiene 2 exchanges candidatos");
    }

    @Test
    void doesNotInventATriangleWhenNoExchangeOffersTheThirdLeg() {
        List<CrossVenue> venues = List.of(
            venue(EXCHANGE_1, "BTC", "USDT", "BTC_USDT"),
            venue(EXCHANGE_2, "DOGE", "USDT", "DOGE_USDT")
            // ninguna combinación de exchanges tiene BTC/DOGE ni DOGE/BTC
        );

        assertTrue(CrossTriangleFinder.find(venues, "USDT").isEmpty());
    }
}
