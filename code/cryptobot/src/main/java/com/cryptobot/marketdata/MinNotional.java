package com.cryptobot.marketdata;

import java.math.BigDecimal;

/**
 * Umbral de valor nocional mínimo por moneda de cotización — un "mejor
 * precio" que no lo alcanza puede ser una orden vieja y chica aislada, no
 * liquidez real (XTZ en Poloniex, Sprint 0003/0004). Nació en
 * {@code TriangleSpread} (Sprint 0009/0012), se movió acá en el Sprint
 * 0017 porque ya lo usaban 5 consumidores fuera de `triangular` y
 * `marketdata` es la capa de base — no correspondía que dependieran de un
 * paquete más arriba en la jerarquía.
 */
public final class MinNotional {

    private static final BigDecimal USDT = new BigDecimal("50");
    private static final BigDecimal BTC = new BigDecimal("0.00078");
    private static final BigDecimal CLP = new BigDecimal("47500");

    private MinNotional() {
    }

    public static BigDecimal forCurrency(String currency) {
        return switch (currency) {
            case "BTC" -> BTC;
            case "CLP" -> CLP;
            default -> USDT;
        };
    }
}
