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
 * Spread de un ciclo triangular — a diferencia de {@link com.cryptobot.marketdata.NetSpread}
 * (que resta un % de fees a una diferencia de precios, válido para 2 patas),
 * acá se **compone** el monto real a través de las 3 conversiones: se parte
 * de 1 unidad de la moneda ancla, en cada pata se convierte (bid si se
 * vende la base del mercado, ask si se compra) y se descuenta la fee de esa
 * pata, y al final se compara el monto resultante contra 1.
 */
public final class TriangleSpread {

    // Mismos umbrales que TrackedAssets/OverlapCheck (Sprint 0006).
    private static final BigDecimal MIN_NOTIONAL_USDT = new BigDecimal("50");
    private static final BigDecimal MIN_NOTIONAL_BTC = new BigDecimal("0.00078");
    private static final BigDecimal MIN_NOTIONAL_CLP = new BigDecimal("47500");
    private static final MathContext MC = new MathContext(20);

    public record Result(List<String> path, List<Leg> legs, BigDecimal grossPct, BigDecimal netPct) {
        public boolean isPositive() {
            return netPct.signum() > 0;
        }
    }

    /** Qué símbolo y lado del book (bid/ask) usó una pata del ciclo, y a qué precio. */
    public record Leg(String symbol, String side, BigDecimal price) {
    }

    private TriangleSpread() {
    }

    /** Recorre el ciclo A→B→C→A. */
    public static Optional<Result> evaluateForward(String exchangeName, Triangle t, Map<String, OrderBook> books) {
        return evaluate(exchangeName,
            List.of(t.currencyA(), t.currencyB(), t.currencyC()),
            List.of(t.marketAB(), t.marketBC(), t.marketCA()),
            books);
    }

    /** Recorre el ciclo A→C→B→A (la otra dirección posible). */
    public static Optional<Result> evaluateBackward(String exchangeName, Triangle t, Map<String, OrderBook> books) {
        return evaluate(exchangeName,
            List.of(t.currencyA(), t.currencyC(), t.currencyB()),
            List.of(t.marketCA(), t.marketBC(), t.marketAB()),
            books);
    }

    private static Optional<Result> evaluate(String exchangeName, List<String> fromCurrencies,
                                              List<Market> legMarkets, Map<String, OrderBook> books) {
        BigDecimal gross = BigDecimal.ONE;
        BigDecimal net = BigDecimal.ONE;
        List<Leg> legs = new ArrayList<>();

        for (int i = 0; i < legMarkets.size(); i++) {
            Market market = legMarkets.get(i);
            String from = fromCurrencies.get(i);
            OrderBook book = books.get(market.symbol());
            if (book == null) {
                return Optional.empty();
            }

            BigDecimal minNotional = minNotionalFor(market.quote());
            BigDecimal rate;
            if (from.equals(market.base())) {
                PriceLevel bid = book.bestBidAbove(minNotional);
                if (bid == null) return Optional.empty();
                rate = bid.price();
                legs.add(new Leg(market.symbol(), "bid", bid.price()));
            } else {
                PriceLevel ask = book.bestAskAbove(minNotional);
                if (ask == null) return Optional.empty();
                rate = BigDecimal.ONE.divide(ask.price(), MC);
                legs.add(new Leg(market.symbol(), "ask", ask.price()));
            }

            BigDecimal fee = ExchangeFees.takerFee(exchangeName, market.quote());
            gross = gross.multiply(rate, MC);
            net = net.multiply(rate, MC).multiply(BigDecimal.ONE.subtract(fee), MC);
        }

        List<String> path = List.of(fromCurrencies.get(0), fromCurrencies.get(1), fromCurrencies.get(2), fromCurrencies.get(0));
        return Optional.of(new Result(path, legs, toPct(gross), toPct(net)));
    }

    private static BigDecimal toPct(BigDecimal amount) {
        return amount.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100), MC)
            .round(new MathContext(8));
    }

    /**
     * Público: {@code TriangleWatcher} y {@code CrossTriangleSpread} lo reusan
     * para el mismo umbral. CLP se suma en el Sprint 0012 — con NotBank en el
     * universo de exchanges, una pata puede terminar cotizada en CLP (ej.
     * BTCCLP), no solo en USDT o BTC.
     */
    public static BigDecimal minNotionalFor(String currency) {
        return switch (currency) {
            case "BTC" -> MIN_NOTIONAL_BTC;
            case "CLP" -> MIN_NOTIONAL_CLP;
            default -> MIN_NOTIONAL_USDT;
        };
    }
}
