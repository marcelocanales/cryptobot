package com.cryptobot.triangular;

import com.cryptobot.marketdata.Market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Descubre triángulos reales a partir de la lista de mercados de un
 * exchange — no hay una lista hardcodeada de qué activos "deberían"
 * formar un triángulo, se deriva de los mercados que efectivamente existen.
 * Genérico: no depende de ningún exchange en particular.
 */
public final class TriangleFinder {

    private TriangleFinder() {
    }

    /**
     * @param anchorCurrency moneda "eje" del triángulo (ej. "USDT") — se buscan
     *                       pares de monedas que coticen contra ella y además
     *                       tengan mercado directo entre sí.
     */
    public static List<Triangle> find(List<Market> markets, String anchorCurrency) {
        Map<String, Market> byPair = new HashMap<>();
        for (Market m : markets) {
            byPair.put(pairKey(m.base(), m.quote()), m);
        }

        Map<String, Market> anchorMarketByCurrency = new HashMap<>();
        for (Market m : markets) {
            if (m.quote().equals(anchorCurrency) && !m.base().equals(anchorCurrency)) {
                anchorMarketByCurrency.put(m.base(), m);
            } else if (m.base().equals(anchorCurrency) && !m.quote().equals(anchorCurrency)) {
                anchorMarketByCurrency.put(m.quote(), m);
            }
        }

        List<String> spokes = new ArrayList<>(anchorMarketByCurrency.keySet());
        List<Triangle> triangles = new ArrayList<>();
        for (int i = 0; i < spokes.size(); i++) {
            for (int j = i + 1; j < spokes.size(); j++) {
                String x = spokes.get(i);
                String y = spokes.get(j);
                Market direct = byPair.get(pairKey(x, y));
                if (direct == null) {
                    continue;
                }
                triangles.add(new Triangle(
                    anchorCurrency, x, y,
                    anchorMarketByCurrency.get(x),
                    direct,
                    anchorMarketByCurrency.get(y)
                ));
            }
        }
        return triangles;
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "/" + b : b + "/" + a;
    }
}
