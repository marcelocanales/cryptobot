package com.cryptobot;

import com.cryptobot.marketdata.ExchangeConnector;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.ParallelFetch;
import com.cryptobot.triangular.Triangle;
import com.cryptobot.triangular.TriangleFinder;
import com.cryptobot.triangular.TriangleSpread;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cuerpo compartido de {@link TriangleCheck} — foto en vivo (no continua) de
 * los triángulos reales de un exchange, anclados en una moneda. Extraído en
 * el Sprint 0018 para que un segundo exchange (YoBit) lo reuse sin duplicar
 * la orquestación: {@link TriangleFinder}/{@link TriangleSpread} ya eran
 * agnósticos de exchange desde el Sprint 0009/0010, solo faltaba que el main
 * también lo fuera.
 */
public final class TriangleCheckRunner {

    private TriangleCheckRunner() {
    }

    public static void run(ExchangeConnector connector, String exchangeName, String anchor) {
        List<Market> markets = connector.fetchMarkets();
        List<Triangle> triangles = TriangleFinder.find(markets, anchor);
        System.out.println("Triángulos encontrados en " + exchangeName + " (ancla " + anchor + "): " + triangles.size());
        System.out.println();

        Set<String> symbols = new HashSet<>();
        for (Triangle t : triangles) {
            symbols.add(t.marketAB().symbol());
            symbols.add(t.marketBC().symbol());
            symbols.add(t.marketCA().symbol());
        }

        List<ParallelFetch.FetchTask<String, OrderBook>> fetchTasks = new ArrayList<>();
        for (String symbol : symbols) {
            fetchTasks.add(new ParallelFetch.FetchTask<>(symbol, exchangeName, () -> connector.fetchOrderBook(symbol)));
        }
        ParallelFetch.Outcome<String, OrderBook> outcome = ParallelFetch.fetchAll(fetchTasks);
        Map<String, OrderBook> books = outcome.results();
        for (Map.Entry<String, String> entry : outcome.errors().entrySet()) {
            System.out.println("  ERROR (" + entry.getKey() + "): " + entry.getValue());
        }
        System.out.println("Order books únicos: " + symbols.size() + " pedidos, " + books.size()
            + " obtenidos (" + outcome.errors().size() + " errores)");
        System.out.println();

        int positivos = 0;
        for (Triangle t : triangles) {
            System.out.println("=== " + t.currencyA() + "-" + t.currencyB() + "-" + t.currencyC() + " ===");
            positivos += report(exchangeName, t, books, true);
            positivos += report(exchangeName, t, books, false);
            System.out.println();
        }

        System.out.println(positivos + " de " + (triangles.size() * 2) + " direcciones con neto positivo");
    }

    private static int report(String exchangeName, Triangle t, Map<String, OrderBook> books, boolean forward) {
        Optional<TriangleSpread.Result> result = forward
            ? TriangleSpread.evaluateForward(exchangeName, t, books)
            : TriangleSpread.evaluateBackward(exchangeName, t, books);

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
