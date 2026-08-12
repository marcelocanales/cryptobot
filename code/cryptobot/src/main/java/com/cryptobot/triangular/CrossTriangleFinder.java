package com.cryptobot.triangular;

import com.cryptobot.marketdata.CrossVenue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Descubre triángulos sobre la **unión** de mercados de varios exchanges —
 * mismo algoritmo que {@link TriangleFinder}, pero agrupando por par de
 * monedas en vez de quedarse con un solo mercado por par. Es justo esa
 * diferencia la que permite encontrar ciclos "por necesidad" (ver
 * {@link CrossTriangle#isNecessityCycle()}): una pata que ningún exchange
 * individual tiene junto con las otras dos, pero que sí existe en algún
 * exchange distinto.
 */
public final class CrossTriangleFinder {

    private CrossTriangleFinder() {
    }

    public static List<CrossTriangle> find(List<CrossVenue> venues, String anchorCurrency) {
        Map<String, List<CrossVenue>> byPair = new HashMap<>();
        for (CrossVenue v : venues) {
            byPair.computeIfAbsent(pairKey(v.market().base(), v.market().quote()), k -> new ArrayList<>()).add(v);
        }

        Map<String, List<CrossVenue>> anchorVenuesByCurrency = new HashMap<>();
        for (CrossVenue v : venues) {
            var m = v.market();
            if (m.quote().equals(anchorCurrency) && !m.base().equals(anchorCurrency)) {
                anchorVenuesByCurrency.computeIfAbsent(m.base(), k -> new ArrayList<>()).add(v);
            } else if (m.base().equals(anchorCurrency) && !m.quote().equals(anchorCurrency)) {
                anchorVenuesByCurrency.computeIfAbsent(m.quote(), k -> new ArrayList<>()).add(v);
            }
        }

        List<String> spokes = new ArrayList<>(anchorVenuesByCurrency.keySet());
        List<CrossTriangle> triangles = new ArrayList<>();
        for (int i = 0; i < spokes.size(); i++) {
            for (int j = i + 1; j < spokes.size(); j++) {
                String x = spokes.get(i);
                String y = spokes.get(j);
                List<CrossVenue> direct = byPair.get(pairKey(x, y));
                if (direct == null) {
                    continue;
                }
                triangles.add(new CrossTriangle(
                    anchorCurrency, x, y,
                    anchorVenuesByCurrency.get(x),
                    direct,
                    anchorVenuesByCurrency.get(y)
                ));
            }
        }
        return triangles;
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "/" + b : b + "/" + a;
    }
}
