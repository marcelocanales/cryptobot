package com.cryptobot.triangular;

import com.cryptobot.marketdata.ExchangeFees;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PriceLevel;

import java.math.BigDecimal;
import java.math.MathContext;
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

    // Mismos umbrales que TrackedAssets — Poloniex solo tiene patas en USDT o BTC
    // entre los 17 triángulos anclados en USDT que existen hoy (Sprint 0009).
    private static final BigDecimal MIN_NOTIONAL_USDT = new BigDecimal("50");
    private static final BigDecimal MIN_NOTIONAL_BTC = new BigDecimal("0.00078");
    private static final MathContext MC = new MathContext(20);

    public record Result(List<String> path, BigDecimal grossPct, BigDecimal netPct) {
        public boolean isPositive() {
            return netPct.signum() > 0;
        }
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
            } else {
                PriceLevel ask = book.bestAskAbove(minNotional);
                if (ask == null) return Optional.empty();
                rate = BigDecimal.ONE.divide(ask.price(), MC);
            }

            BigDecimal fee = ExchangeFees.takerFee(exchangeName, market.quote());
            gross = gross.multiply(rate, MC);
            net = net.multiply(rate, MC).multiply(BigDecimal.ONE.subtract(fee), MC);
        }

        List<String> path = List.of(fromCurrencies.get(0), fromCurrencies.get(1), fromCurrencies.get(2), fromCurrencies.get(0));
        return Optional.of(new Result(path, toPct(gross), toPct(net)));
    }

    private static BigDecimal toPct(BigDecimal amount) {
        return amount.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100), MC)
            .round(new MathContext(8));
    }

    private static BigDecimal minNotionalFor(String currency) {
        return "BTC".equals(currency) ? MIN_NOTIONAL_BTC : MIN_NOTIONAL_USDT;
    }
}
