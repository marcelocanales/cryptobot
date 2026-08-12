package com.cryptobot.triangular;

import com.cryptobot.marketdata.ExchangeFees;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PriceLevel;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spread de un {@link CrossTriangle} — mismo principio de composición que
 * {@link TriangleSpread} (partir de 1 unidad, convertir pata por pata,
 * descontar fee), pero acá cada pata puede tener más de un exchange
 * candidato: se evalúan todos y se toma el que dé mejor resultado **neto**
 * (precio ya descontada la fee de ese exchange) — no el de mejor precio
 * bruto, que puede perder por tener una fee más alta (Sprint 0012).
 */
public final class CrossTriangleSpread {

    private static final MathContext MC = new MathContext(20);

    public record Leg(String exchange, String symbol, String side, BigDecimal price) {
    }

    public record Result(List<String> path, List<Leg> legs, BigDecimal grossPct, BigDecimal netPct) {
        public boolean isPositive() {
            return netPct.signum() > 0;
        }
    }

    private record LegChoice(BigDecimal rate, BigDecimal netRate, Leg leg) {
    }

    private CrossTriangleSpread() {
    }

    /** Recorre el ciclo A→B→C→A. */
    public static Optional<Result> evaluateForward(CrossTriangle t, Map<String, OrderBook> books) {
        return evaluate(
            List.of(t.currencyA(), t.currencyB(), t.currencyC()),
            List.of(t.venuesAB(), t.venuesBC(), t.venuesCA()),
            books);
    }

    /** Recorre el ciclo A→C→B→A. */
    public static Optional<Result> evaluateBackward(CrossTriangle t, Map<String, OrderBook> books) {
        return evaluate(
            List.of(t.currencyA(), t.currencyC(), t.currencyB()),
            List.of(t.venuesCA(), t.venuesBC(), t.venuesAB()),
            books);
    }

    private static Optional<Result> evaluate(List<String> fromCurrencies, List<List<CrossVenue>> legOptions,
                                              Map<String, OrderBook> books) {
        BigDecimal gross = BigDecimal.ONE;
        BigDecimal net = BigDecimal.ONE;
        List<Leg> legs = new ArrayList<>();

        for (int i = 0; i < legOptions.size(); i++) {
            LegChoice best = bestChoice(fromCurrencies.get(i), legOptions.get(i), books);
            if (best == null) {
                return Optional.empty();
            }
            gross = gross.multiply(best.rate(), MC);
            net = net.multiply(best.netRate(), MC);
            legs.add(best.leg());
        }

        List<String> path = List.of(fromCurrencies.get(0), fromCurrencies.get(1), fromCurrencies.get(2), fromCurrencies.get(0));
        return Optional.of(new Result(path, legs, toPct(gross), toPct(net)));
    }

    /** @return el candidato con mejor resultado neto, o null si ninguno tiene liquidez suficiente. */
    private static LegChoice bestChoice(String from, List<CrossVenue> candidates, Map<String, OrderBook> books) {
        LegChoice best = null;
        for (CrossVenue candidate : candidates) {
            Market market = candidate.market();
            OrderBook book = books.get(bookKey(candidate.exchangeName(), market.symbol()));
            if (book == null) {
                continue;
            }
            BigDecimal minNotional = TriangleSpread.minNotionalFor(market.quote());

            BigDecimal rate;
            String side;
            BigDecimal price;
            if (from.equals(market.base())) {
                PriceLevel bid = book.bestBidAbove(minNotional);
                if (bid == null) continue;
                rate = bid.price();
                side = "bid";
                price = bid.price();
            } else if (from.equals(market.quote())) {
                PriceLevel ask = book.bestAskAbove(minNotional);
                if (ask == null) continue;
                rate = BigDecimal.ONE.divide(ask.price(), MC);
                side = "ask";
                price = ask.price();
            } else {
                continue; // el mercado no conecta esta moneda — no debería pasar si el finder está bien
            }

            BigDecimal fee = ExchangeFees.takerFee(candidate.exchangeName(), market.quote());
            BigDecimal netRate = rate.multiply(BigDecimal.ONE.subtract(fee), MC);

            if (best == null || netRate.compareTo(best.netRate()) > 0) {
                best = new LegChoice(rate, netRate, new Leg(candidate.exchangeName(), market.symbol(), side, price));
            }
        }
        return best;
    }

    public static String bookKey(String exchangeName, String symbol) {
        return exchangeName + "|" + symbol;
    }

    private static BigDecimal toPct(BigDecimal amount) {
        return amount.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100), MC)
            .round(new MathContext(8));
    }
}
