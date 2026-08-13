package com.cryptobot.funding;

import com.cryptobot.marketdata.CrossVenue;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.marketdata.yobit.YobitConnector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Descubre, para cada perpetuo real de Poloniex, en qué exchanges hay un
 * spot USDT del mismo activo — antes hardcodeado a exactamente Poloniex +
 * NotBank (Sprint 0015), generalizado en el Sprint 0020 para sumar YoBit
 * sin duplicar el descubrimiento por cada fuente nueva. Mismo principio
 * "descubrir, no hardcodear" que {@code TrackedAssets}/{@code TriangleFinder}.
 *
 * Buda queda fuera a propósito: no tiene ningún mercado cotizado en USDT
 * (su universo es CLP/COP/PEN) — ver docs/sprints/sprint_0020.md.
 */
public final class CashAndCarryCandidates {

    private static final String PERP_SUFFIX = "_USDT_PERP";
    private static final String QUOTE = "USDT";

    public record Candidate(String asset, String perpSymbol, List<CrossVenue> spotVenues) {
    }

    private CashAndCarryCandidates() {
    }

    public static List<Candidate> all(PoloniexConnector poloniex, NotBankConnector notbank, YobitConnector yobit) {
        List<String> perpSymbols = poloniex.fetchPerpSymbols();

        List<CrossVenue> spotVenues = new ArrayList<>();
        for (Market m : poloniex.fetchMarkets()) {
            spotVenues.add(new CrossVenue(poloniex, m));
        }
        for (Market m : notbank.fetchMarkets()) {
            spotVenues.add(new CrossVenue(notbank, m));
        }
        for (Market m : yobit.fetchMarkets()) {
            spotVenues.add(new CrossVenue(yobit, m));
        }

        return discover(perpSymbols, spotVenues);
    }

    /** Testeable con datos sintéticos, sin HTTP. */
    static List<Candidate> discover(List<String> perpSymbols, List<CrossVenue> spotVenues) {
        Map<String, List<CrossVenue>> venuesByBase = new LinkedHashMap<>();
        for (CrossVenue v : spotVenues) {
            if (QUOTE.equals(v.market().quote())) {
                venuesByBase.computeIfAbsent(v.market().base(), k -> new ArrayList<>()).add(v);
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (String perpSymbol : perpSymbols) {
            String asset = perpSymbol.substring(0, perpSymbol.length() - PERP_SUFFIX.length());
            List<CrossVenue> venues = venuesByBase.getOrDefault(asset, List.of());
            if (venues.isEmpty()) {
                continue;
            }
            candidates.add(new Candidate(asset, perpSymbol, venues));
        }
        return candidates;
    }
}
