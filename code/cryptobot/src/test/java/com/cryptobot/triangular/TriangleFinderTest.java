package com.cryptobot.triangular;

import com.cryptobot.marketdata.Market;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriangleFinderTest {

    @Test
    void findsATriangleWhenTheThirdMarketExists() {
        List<Market> markets = List.of(
            new Market("BTC", "USDT", "BTC_USDT"),
            new Market("ETH", "USDT", "ETH_USDT"),
            new Market("ETH", "BTC", "ETH_BTC")
        );

        List<Triangle> found = TriangleFinder.find(markets, "USDT");

        assertEquals(1, found.size());
        Triangle t = found.get(0);
        assertEquals("USDT", t.currencyA());
        assertEquals(new Market("BTC", "USDT", "BTC_USDT"), t.marketAB());
        assertEquals(new Market("ETH", "BTC", "ETH_BTC"), t.marketBC());
        assertEquals(new Market("ETH", "USDT", "ETH_USDT"), t.marketCA());
    }

    @Test
    void doesNotInventATriangleWhenTheThirdMarketIsMissing() {
        List<Market> markets = List.of(
            new Market("BTC", "USDT", "BTC_USDT"),
            new Market("DOGE", "USDT", "DOGE_USDT")
            // sin DOGE/BTC ni BTC/DOGE — no hay triángulo real
        );

        assertTrue(TriangleFinder.find(markets, "USDT").isEmpty());
    }

    @Test
    void findsMultipleTrianglesSharingTheSameAnchorMarket() {
        List<Market> markets = List.of(
            new Market("BTC", "USDT", "BTC_USDT"),
            new Market("ETH", "USDT", "ETH_USDT"),
            new Market("LTC", "USDT", "LTC_USDT"),
            new Market("ETH", "BTC", "ETH_BTC"),
            new Market("LTC", "BTC", "LTC_BTC")
            // sin ETH/LTC ni LTC/ETH — ese tercer triángulo no existe
        );

        List<Triangle> found = TriangleFinder.find(markets, "USDT");

        assertEquals(2, found.size());
    }

    @Test
    void ignoresTheAnchorCurrencyItselfAsASpoke() {
        List<Market> markets = List.of(
            new Market("USDT", "USDT", "USDT_USDT"), // no debería pasar en la práctica, pero no debe romper nada
            new Market("BTC", "USDT", "BTC_USDT")
        );

        assertTrue(TriangleFinder.find(markets, "USDT").isEmpty());
    }
}
