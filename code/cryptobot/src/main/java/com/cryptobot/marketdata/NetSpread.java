package com.cryptobot.marketdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Spread neto entre dos exchanges para una dirección de operación (comprar
 * en uno, vender en otro) — bruto menos la fee de taker de ambas patas
 * (modelo de ejecución taker-taker, ver docs/roadmap.md). Antes esta cuenta
 * vivía duplicada, casi textual, entre {@code OverlapCheck} y
 * {@code SpreadWatcher} — Sprint 0007.
 */
public final class NetSpread {

    public record Result(String buyExchange, String sellExchange, PriceLevel buyAt, PriceLevel sellAt,
                          BigDecimal grossPct, BigDecimal feesPct, BigDecimal netPct) {
        public boolean isPositive() {
            return netPct.signum() > 0;
        }
    }

    private NetSpread() {
    }

    /**
     * @return vacío si falta un nivel de precio en algún lado (sin liquidez
     * suficiente ya filtrada aguas arriba, ej. con {@code bestBidAbove}).
     */
    public static Optional<Result> evaluate(String buyExchange, String sellExchange,
                                             PriceLevel buyAt, PriceLevel sellAt) {
        if (buyAt == null || sellAt == null) {
            return Optional.empty();
        }
        BigDecimal grossPct = percent(sellAt.price().subtract(buyAt.price()), buyAt.price());
        BigDecimal feesPct = ExchangeFees.takerFee(buyExchange).add(ExchangeFees.takerFee(sellExchange))
            .multiply(BigDecimal.valueOf(100));
        BigDecimal netPct = grossPct.subtract(feesPct);
        return Optional.of(new Result(buyExchange, sellExchange, buyAt, sellAt, grossPct, feesPct, netPct));
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal base) {
        return amount.divide(base, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }
}
