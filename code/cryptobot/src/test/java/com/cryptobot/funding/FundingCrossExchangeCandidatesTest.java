package com.cryptobot.funding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundingCrossExchangeCandidatesTest {

    private static FundingCrossExchangeCandidates.PerpVenue venue(String exchange, String symbol) {
        return new FundingCrossExchangeCandidates.PerpVenue(exchange, symbol);
    }

    @Test
    void groupsAnAssetPresentOnBothExchanges() {
        List<FundingCrossExchangeCandidates.PerpVenue> venues = List.of(
            venue("Poloniex", "BTC_USDT_PERP"),
            venue("Bitfinex", "tBTCF0:USTF0")
        );

        List<FundingCrossExchangeCandidates.Candidate> candidates = FundingCrossExchangeCandidates.discover(venues);

        assertEquals(1, candidates.size());
        assertEquals("BTC", candidates.get(0).asset());
        assertEquals(2, candidates.get(0).venues().size());
    }

    @Test
    void assetOnOnlyOneExchangeIsExcluded() {
        List<FundingCrossExchangeCandidates.PerpVenue> venues = List.of(
            venue("Poloniex", "APT_USDT_PERP")
        );

        List<FundingCrossExchangeCandidates.Candidate> candidates = FundingCrossExchangeCandidates.discover(venues);

        assertTrue(candidates.isEmpty(), "un solo exchange no tiene con qué comparar funding");
    }

    @Test
    void bitfinexContractNotMarginedInUsdtIsExcluded() {
        // ETHF0:BTCF0 -> margined en BTC, no compara contra el funding en USDT de Poloniex.
        List<FundingCrossExchangeCandidates.PerpVenue> venues = List.of(
            venue("Poloniex", "ETH_USDT_PERP"),
            venue("Bitfinex", "tETHF0:BTCF0")
        );

        List<FundingCrossExchangeCandidates.Candidate> candidates = FundingCrossExchangeCandidates.discover(venues);

        assertTrue(candidates.isEmpty());
    }

    @Test
    void threeExchangesForTheSameAssetAllGroupTogether() {
        List<FundingCrossExchangeCandidates.PerpVenue> venues = List.of(
            venue("Poloniex", "SOL_USDT_PERP"),
            venue("Bitfinex", "tSOLF0:USTF0"),
            venue("CoinEx", "tSOLF0:USTF0") // hipotético, para probar que no está atado a 2 exchanges
        );

        List<FundingCrossExchangeCandidates.Candidate> candidates = FundingCrossExchangeCandidates.discover(venues);

        // "CoinEx" no tiene extracción de asset implementada todavía (assetOf devuelve null
        // para cualquier exchange que no sea Poloniex/Bitfinex) -> solo agrupan los 2 reales.
        assertEquals(1, candidates.size());
        assertEquals(2, candidates.get(0).venues().size());
    }
}
