package com.cryptobot;

import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.ParallelFetch;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.triangular.CrossTriangle;
import com.cryptobot.triangular.CrossTriangleFinder;
import com.cryptobot.triangular.CrossTriangleSpread;
import com.cryptobot.marketdata.CrossVenue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 0012 — foto en vivo (no continua todavía) de triángulos repartidos
 * entre Poloniex y NotBank. A diferencia de {@code TriangleCheck} (Sprint
 * 0009, Poloniex solo), acá cada pata puede tomarse del exchange que dé
 * mejor precio neto — y algunos triángulos pueden no existir completos en
 * ningún exchange individual ("por necesidad", ver
 * docs/estrategias/03-triangular-cross-exchange.md).
 */
public class CrossTriangleCheck {

    private static final String ANCHOR = "USDT";

    public static void main(String[] args) {
        var poloniex = new PoloniexConnector();
        var notbank = new NotBankConnector();

        List<CrossVenue> venues = new ArrayList<>();
        for (Market m : poloniex.fetchMarkets()) {
            venues.add(new CrossVenue(poloniex, m));
        }
        for (Market m : notbank.fetchMarkets()) {
            venues.add(new CrossVenue(notbank, m));
        }

        List<CrossTriangle> triangles = CrossTriangleFinder.find(venues, ANCHOR);
        long necessity = triangles.stream().filter(CrossTriangle::isNecessityCycle).count();
        System.out.println("Triángulos encontrados (Poloniex + NotBank, ancla " + ANCHOR + "): " + triangles.size());
        System.out.println("  Por necesidad (ningún exchange individual las tiene las 3 juntas): " + necessity);
        System.out.println("  Por optimización (al menos un exchange ya las tiene completas): " + (triangles.size() - necessity));
        System.out.println();

        Map<String, CrossVenue> venueByKey = new HashMap<>();
        for (CrossTriangle t : triangles) {
            for (CrossVenue v : allVenues(t)) {
                venueByKey.put(CrossTriangleSpread.bookKey(v.exchangeName(), v.market().symbol()), v);
            }
        }

        List<ParallelFetch.FetchTask<String, OrderBook>> fetchTasks = new ArrayList<>();
        for (Map.Entry<String, CrossVenue> entry : venueByKey.entrySet()) {
            CrossVenue v = entry.getValue();
            fetchTasks.add(new ParallelFetch.FetchTask<>(entry.getKey(), v.exchangeName(),
                () -> v.connector().fetchOrderBook(v.market().symbol())));
        }
        ParallelFetch.Outcome<String, OrderBook> outcome = ParallelFetch.fetchAll(fetchTasks);
        Map<String, OrderBook> books = outcome.results();
        for (Map.Entry<String, String> entry : outcome.errors().entrySet()) {
            System.out.println("  ERROR (" + entry.getKey() + "): " + entry.getValue());
        }
        System.out.println("Order books únicos: " + venueByKey.size() + " pedidos, " + books.size()
            + " obtenidos (" + outcome.errors().size() + " errores)");
        System.out.println();

        int positivos = 0;
        for (CrossTriangle t : triangles) {
            String tag = t.isNecessityCycle() ? "NECESIDAD" : "optimización";
            System.out.println("=== " + t.currencyA() + "-" + t.currencyB() + "-" + t.currencyC() + " [" + tag + "] ===");
            positivos += report(t, books, true);
            positivos += report(t, books, false);
            System.out.println();
        }

        System.out.println(positivos + " de " + (triangles.size() * 2) + " direcciones con neto positivo");
    }

    private static List<CrossVenue> allVenues(CrossTriangle t) {
        List<CrossVenue> all = new ArrayList<>();
        all.addAll(t.venuesAB());
        all.addAll(t.venuesBC());
        all.addAll(t.venuesCA());
        return all;
    }

    private static int report(CrossTriangle t, Map<String, OrderBook> books, boolean forward) {
        Optional<CrossTriangleSpread.Result> result = forward
            ? CrossTriangleSpread.evaluateForward(t, books)
            : CrossTriangleSpread.evaluateBackward(t, books);

        if (result.isEmpty()) {
            System.out.println("  (" + (forward ? "directo" : "inverso") + "): sin liquidez suficiente en alguna pata");
            return 0;
        }

        CrossTriangleSpread.Result r = result.get();
        String path = String.join(" -> ", r.path());
        StringBuilder legsDetail = new StringBuilder();
        for (CrossTriangleSpread.Leg leg : r.legs()) {
            if (legsDetail.length() > 0) legsDetail.append(", ");
            legsDetail.append(leg.exchange()).append(":").append(leg.symbol());
        }

        // Un bruto extremo (visto en vivo: +2.158.104% en USDT-BOB-BTC) no es
        // una oportunidad real — es el mismo ticker de 3 letras usado para dos
        // activos distintos en cada exchange (BOB token en Poloniex vs. BOB
        // boliviano fiat en NotBank). El buscador de triángulos solo compara
        // el código de moneda, no la identidad real del activo — hasta que
        // eso se resuelva (backlog), cualquier resultado > 50% se marca como
        // sospechoso en vez de reportarse como una señal real.
        boolean implausible = r.grossPct().abs().compareTo(BigDecimal.valueOf(50)) > 0;
        String verdict = implausible ? "IMPLAUSIBLE — posible choque de tickers, no es arbitraje real"
            : r.isPositive() ? "NETO POSITIVO" : "sin arbitraje neto";
        System.out.println("  " + path + " [" + legsDetail + "]: bruto " + r.grossPct() + "%, neto " + r.netPct()
            + "% — " + verdict);
        return (!implausible && r.isPositive()) ? 1 : 0;
    }
}
