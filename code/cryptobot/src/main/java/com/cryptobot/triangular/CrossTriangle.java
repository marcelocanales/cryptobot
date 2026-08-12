package com.cryptobot.triangular;

import java.util.List;

/**
 * Un ciclo de tres monedas, como {@link Triangle}, pero repartido entre
 * exchanges (Sprint 0012): cada pata tiene una **lista** de exchanges
 * candidatos que la ofrecen — puede haber uno solo (el par existe en un
 * único exchange) o varios (se elige el de mejor precio al evaluar, ver
 * {@link CrossTriangleSpread}).
 */
public record CrossTriangle(String currencyA, String currencyB, String currencyC,
                             List<CrossVenue> venuesAB, List<CrossVenue> venuesBC, List<CrossVenue> venuesCA) {

    /**
     * "Por necesidad" (true): ningún exchange individual ofrece las 3 patas
     * juntas — el triangular intra-exchange (Sprint 0009/0010) no podría
     * operar este ciclo bajo ninguna circunstancia. "Por optimización"
     * (false): al menos un exchange sí las tiene las 3, cross-exchange solo
     * puede mejorar alguna pata. Ver docs/estrategias/03-triangular-cross-exchange.md.
     */
    public boolean isNecessityCycle() {
        for (CrossVenue ab : venuesAB) {
            for (CrossVenue bc : venuesBC) {
                for (CrossVenue ca : venuesCA) {
                    if (ab.exchangeName().equals(bc.exchangeName()) && bc.exchangeName().equals(ca.exchangeName())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
