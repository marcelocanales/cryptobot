package com.cryptobot.funding;

import com.cryptobot.marketdata.bitfinex.BitfinexConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Descubre, para cada activo, en qué exchanges hay un perpetuo margined en
 * USDT — hace falta 2 o más para poder comparar funding entre ellos
 * (hipótesis 05). Mismo principio "descubrir, no hardcodear" que
 * {@code CashAndCarryCandidates} (Sprint 0020/0021), pero acá se agrupan
 * perpetuos contra perpetuos, no perpetuos contra spot.
 */
public final class FundingCrossExchangeCandidates {

    private static final String POLONIEX_PERP_SUFFIX = "_USDT_PERP";
    private static final String BITFINEX_CONTRACT_SUFFIX = "F0";
    private static final String BITFINEX_USDT_TICKER = "UST";
    private static final String USDT = "USDT";

    public record PerpVenue(String exchangeName, String perpSymbol) {
    }

    public record Candidate(String asset, List<PerpVenue> venues) {
    }

    private FundingCrossExchangeCandidates() {
    }

    public static List<Candidate> all(PoloniexConnector poloniex, BitfinexConnector bitfinex) {
        List<PerpVenue> venues = new ArrayList<>();
        for (String symbol : poloniex.fetchPerpSymbols()) {
            venues.add(new PerpVenue("Poloniex", symbol));
        }
        for (String symbol : bitfinex.fetchPerpSymbols()) {
            venues.add(new PerpVenue("Bitfinex", symbol));
        }
        return discover(venues);
    }

    /** Testeable con datos sintéticos, sin HTTP. */
    static List<Candidate> discover(List<PerpVenue> venues) {
        Map<String, List<PerpVenue>> venuesByAsset = new LinkedHashMap<>();
        for (PerpVenue v : venues) {
            String asset = assetOf(v);
            if (asset != null) {
                venuesByAsset.computeIfAbsent(asset, k -> new ArrayList<>()).add(v);
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<PerpVenue>> entry : venuesByAsset.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue; // hace falta 2+ exchanges para comparar funding
            }
            candidates.add(new Candidate(entry.getKey(), entry.getValue()));
        }
        return candidates;
    }

    /** @return el activo base margined en USDT, o null si no aplica (extracción específica por exchange). */
    private static String assetOf(PerpVenue v) {
        return switch (v.exchangeName()) {
            case "Poloniex" -> assetFromPoloniexPerpSymbol(v.perpSymbol());
            case "Bitfinex" -> assetFromBitfinexPerpSymbol(v.perpSymbol());
            default -> null;
        };
    }

    private static String assetFromPoloniexPerpSymbol(String symbol) {
        return symbol.endsWith(POLONIEX_PERP_SUFFIX)
            ? symbol.substring(0, symbol.length() - POLONIEX_PERP_SUFFIX.length())
            : null;
    }

    /**
     * Símbolo nativo de Bitfinex: {@code "t{BASE}F0:{QUOTE}F0"} (ej.
     * "tBTCF0:USTF0") — confirmado en vivo sobre los 76 perpetuos reales
     * (Sprint 0024), sin excepciones. Solo interesan los margined en USDT
     * (`UST` en la nomenclatura de Bitfinex, normalizado) — un contrato
     * margined en BTC (ej. "tETHF0:BTCF0") no compara contra el funding en
     * USDT de Poloniex.
     */
    private static String assetFromBitfinexPerpSymbol(String symbol) {
        String withoutPrefix = symbol.startsWith("t") ? symbol.substring(1) : symbol;
        String[] parts = withoutPrefix.split(":", 2);
        if (parts.length != 2
                || !parts[0].endsWith(BITFINEX_CONTRACT_SUFFIX)
                || !parts[1].endsWith(BITFINEX_CONTRACT_SUFFIX)) {
            return null;
        }
        String base = parts[0].substring(0, parts[0].length() - BITFINEX_CONTRACT_SUFFIX.length());
        String quote = parts[1].substring(0, parts[1].length() - BITFINEX_CONTRACT_SUFFIX.length());
        String normalizedQuote = BITFINEX_USDT_TICKER.equals(quote) ? USDT : quote;
        return USDT.equals(normalizedQuote) ? base : null;
    }
}
