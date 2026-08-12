package com.cryptobot.marketdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackedAssetsTest {

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
    private static final FakeConnector EXCHANGE_3 = new FakeConnector("Exchange3");

    private static CrossVenue venue(FakeConnector exchange, String base, String quote, String symbol) {
        return new CrossVenue(exchange, new Market(base, quote, symbol));
    }

    @Test
    void assetSharedByTwoOrMoreExchangesIsIncluded() {
        List<CrossVenue> venues = List.of(
            venue(EXCHANGE_1, "BTC", "USDT", "BTC_USDT"),
            venue(EXCHANGE_2, "BTC", "USDT", "BTCUSDT"),
            venue(EXCHANGE_3, "BTC", "USDT", "btc_usdt")
        );

        List<TrackedAsset> found = TrackedAssets.discover(venues);

        assertEquals(1, found.size());
        TrackedAsset asset = found.get(0);
        assertEquals("BTC/USDT", asset.label());
        assertEquals(3, asset.venues().size());
    }

    @Test
    void assetOfferedByOnlyOneExchangeIsExcluded() {
        List<CrossVenue> venues = List.of(
            venue(EXCHANGE_1, "GRAM", "USDT", "GRAM_USDT")
        );

        List<TrackedAsset> found = TrackedAssets.discover(venues);

        assertTrue(found.isEmpty(), "un solo exchange no tiene nada contra qué comparar");
    }

    @Test
    void unsupportedQuoteCurrencyIsExcludedEvenIfSharedByTwoExchanges() {
        // COP no tiene un umbral de nocional verificado (MinNotional) — se
        // excluye a propósito, aunque el activo esté en 2 exchanges.
        List<CrossVenue> venues = List.of(
            venue(EXCHANGE_1, "BTC", "COP", "BTC_COP"),
            venue(EXCHANGE_2, "BTC", "COP", "BTCCOP")
        );

        List<TrackedAsset> found = TrackedAssets.discover(venues);

        assertTrue(found.isEmpty());
    }

    @Test
    void minNotionalMatchesTheQuoteCurrency() {
        List<CrossVenue> venues = List.of(
            venue(EXCHANGE_1, "BTC", "CLP", "btc-clp"),
            venue(EXCHANGE_2, "BTC", "CLP", "BTCCLP"),
            venue(EXCHANGE_1, "LTC", "BTC", "ltc-btc"),
            venue(EXCHANGE_2, "LTC", "BTC", "LTCBTC")
        );

        List<TrackedAsset> found = TrackedAssets.discover(venues);

        assertEquals(2, found.size());
        TrackedAsset btcClp = found.stream().filter(a -> a.label().equals("BTC/CLP")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("47500"), btcClp.minNotional());
        TrackedAsset ltcBtc = found.stream().filter(a -> a.label().equals("LTC/BTC")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("0.00078"), ltcBtc.minNotional());
    }

    @Test
    void baseAndQuoteOrderMatterUnlikeATriangleLeg() {
        // USDT/BTC (si existiera) no es lo mismo que BTC/USDT — no se
        // normaliza el orden, a diferencia de TriangleFinder.
        List<CrossVenue> venues = List.of(
            venue(EXCHANGE_1, "BTC", "USDT", "BTC_USDT"),
            venue(EXCHANGE_2, "USDT", "BTC", "USDT_BTC")
        );

        List<TrackedAsset> found = TrackedAssets.discover(venues);

        assertTrue(found.isEmpty(), "cada dirección tiene un solo exchange, no se mezclan");
    }
}
