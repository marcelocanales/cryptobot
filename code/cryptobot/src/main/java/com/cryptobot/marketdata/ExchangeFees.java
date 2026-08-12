package com.cryptobot.marketdata;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Fee de taker real por exchange, como fracción (0.0020 = 0,20%) — no como
 * porcentaje. El modelo de ejecución asumido en todo el proyecto es
 * taker-taker (dos órdenes de mercado, una por pata — ver docs/roadmap.md,
 * backlog "Ejecución maker + taker"), así que solo la fee de taker importa
 * para el spread neto hoy. Cada valor está documentado, con su fuente, en
 * docs/entorno.md — no cambiar acá sin actualizar ahí también.
 */
public final class ExchangeFees {

    private static final Map<String, BigDecimal> TAKER_FEE = Map.of(
        "Poloniex", new BigDecimal("0.0020"),
        "NotBank", new BigDecimal("0.0060"),
        "Buda", new BigDecimal("0.0080"),
        "YoBit", new BigDecimal("0.0020")
    );

    private ExchangeFees() {
    }

    public static BigDecimal takerFee(String exchangeName) {
        BigDecimal fee = TAKER_FEE.get(exchangeName);
        if (fee == null) {
            throw new IllegalArgumentException("Sin fee de taker conocida para " + exchangeName);
        }
        return fee;
    }
}
