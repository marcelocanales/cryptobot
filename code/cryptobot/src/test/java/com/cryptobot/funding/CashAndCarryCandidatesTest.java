package com.cryptobot.funding;

import com.cryptobot.marketdata.CrossVenue;
import com.cryptobot.marketdata.ExchangeConnector;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashAndCarryCandidatesTest {

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

    private static final FakeConnector POLONIEX = new FakeConnector("Poloniex");
    private static final FakeConnector NOTBANK = new FakeConnector("NotBank");
    private static final FakeConnector YOBIT = new FakeConnector("YoBit");
    private static final FakeConnector BUDA = new FakeConnector("Buda");

    private static CrossVenue venue(FakeConnector exchange, String base, String quote, String symbol) {
        return new CrossVenue(exchange, new Market(base, quote, symbol));
    }

    @Test
    void groupsAllUsdtVenuesForAnAssetWithAPerpetual() {
        List<String> perpSymbols = List.of("BTC_USDT_PERP");
        List<CrossVenue> spotVenues = List.of(
            venue(POLONIEX, "BTC", "USDT", "BTC_USDT"),
            venue(NOTBANK, "BTC", "USDT", "BTCUSDT"),
            venue(YOBIT, "BTC", "USDT", "btc_usdt")
        );

        List<CashAndCarryCandidates.Candidate> candidates = CashAndCarryCandidates.discover(perpSymbols, spotVenues);

        assertEquals(1, candidates.size());
        CashAndCarryCandidates.Candidate c = candidates.get(0);
        assertEquals("BTC", c.asset());
        assertEquals("BTC_USDT_PERP", c.perpSymbol());
        assertEquals(3, c.spotVenues().size());
    }

    @Test
    void perpetualWithNoUsdtSpotAnywhereIsExcluded() {
        List<String> perpSymbols = List.of("XYZ_USDT_PERP");
        List<CrossVenue> spotVenues = List.of(
            venue(POLONIEX, "BTC", "USDT", "BTC_USDT")
        );

        List<CashAndCarryCandidates.Candidate> candidates = CashAndCarryCandidates.discover(perpSymbols, spotVenues);

        assertTrue(candidates.isEmpty());
    }

    @Test
    void aVenueQuotedInSomethingOtherThanUsdtDoesNotCount() {
        // Justo el caso Buda: BTC-CLP existe, pero no es un candidato válido
        // sin conversión CLP->USDT (Sprint 0020, fuera de alcance).
        List<String> perpSymbols = List.of("BTC_USDT_PERP");
        List<CrossVenue> spotVenues = List.of(
            venue(BUDA, "BTC", "CLP", "btc-clp")
        );

        List<CashAndCarryCandidates.Candidate> candidates = CashAndCarryCandidates.discover(perpSymbols, spotVenues);

        assertTrue(candidates.isEmpty());
    }

    @Test
    void stripsNumericPrefixedPerpSymbolsCorrectly() {
        List<String> perpSymbols = List.of("1000SHIB_USDT_PERP");
        List<CrossVenue> spotVenues = List.of(
            venue(POLONIEX, "1000SHIB", "USDT", "1000SHIB_USDT")
        );

        List<CashAndCarryCandidates.Candidate> candidates = CashAndCarryCandidates.discover(perpSymbols, spotVenues);

        assertEquals(1, candidates.size());
        assertEquals("1000SHIB", candidates.get(0).asset());
    }
}
