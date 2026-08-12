package com.cryptobot;

import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.triangular.Triangle;
import com.cryptobot.triangular.TriangleFinder;
import com.cryptobot.triangular.TriangleSpread;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sprint 0009 — foto en vivo (no continua todavía) de todos los triángulos
 * reales descubiertos en Poloniex, anclados en USDT. No hay una lista
 * hardcodeada de qué activos "deberían" formar un triángulo — se descubren
 * a partir de {@code GET /markets}, igual que {@code OverlapCheck} hace con
 * los pares de spot cross-exchange.
 */
public class TriangleCheck {

    private static final String EXCHANGE = "Poloniex";
    private static final String ANCHOR = "USDT";

    public static void main(String[] args) {
        var poloniex = new PoloniexConnector();

        List<Market> markets = poloniex.fetchMarkets();
        List<Triangle> triangles = TriangleFinder.find(markets, ANCHOR);
        System.out.println("Triángulos encontrados en " + EXCHANGE + " (ancla " + ANCHOR + "): " + triangles.size());
        System.out.println();

        Set<String> symbols = new HashSet<>();
        for (Triangle t : triangles) {
            symbols.add(t.marketAB().symbol());
            symbols.add(t.marketBC().symbol());
            symbols.add(t.marketCA().symbol());
        }

        Map<String, OrderBook> books = new HashMap<>();
        int fetchErrors = 0;
        for (String symbol : symbols) {
            try {
                books.put(symbol, poloniex.fetchOrderBook(symbol));
            } catch (RuntimeException e) {
                fetchErrors++;
                System.out.println("  ERROR (" + symbol + "): " + e.getMessage());
            }
        }
        System.out.println("Order books únicos: " + symbols.size() + " pedidos, " + books.size()
            + " obtenidos (" + fetchErrors + " errores)");
        System.out.println();

        int positivos = 0;
        for (Triangle t : triangles) {
            System.out.println("=== " + t.currencyA() + "-" + t.currencyB() + "-" + t.currencyC() + " ===");
            positivos += report(t, books, true);
            positivos += report(t, books, false);
            System.out.println();
        }

        System.out.println(positivos + " de " + (triangles.size() * 2) + " direcciones con neto positivo");
    }

    private static int report(Triangle t, Map<String, OrderBook> books, boolean forward) {
        Optional<TriangleSpread.Result> result = forward
            ? TriangleSpread.evaluateForward(EXCHANGE, t, books)
            : TriangleSpread.evaluateBackward(EXCHANGE, t, books);

        if (result.isEmpty()) {
            System.out.println("  (" + (forward ? "directo" : "inverso") + "): sin liquidez suficiente en alguna pata");
            return 0;
        }

        TriangleSpread.Result r = result.get();
        String path = String.join(" -> ", r.path());
        String verdict = r.isPositive() ? "NETO POSITIVO" : "sin arbitraje neto";
        System.out.println("  " + path + ": bruto " + r.grossPct() + "%, neto " + r.netPct() + "% — " + verdict);
        return r.isPositive() ? 1 : 0;
    }
}
