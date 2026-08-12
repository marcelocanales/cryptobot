package com.cryptobot.watch;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Detecta niveles de precio "congelados" — el mismo valor exacto, ciclo tras
 * ciclo, más tiempo del que un mercado vivo debería quedarse quieto.
 *
 * Encontrado necesario en la corrida nocturna del Sprint 0003: XTZ en
 * Poloniex quedó en 0.1200/0.4999 exacto durante 7 horas seguidas — órdenes
 * viejas abandonadas, no un book vivo — mientras NotBank se movía con
 * normalidad. El filtro de liquidez mínima (Sprint 0003) no lo detecta,
 * porque el problema no es el tamaño, es que no cambia.
 *
 * No es prueba de que un book es falso — un mercado real y tranquilo
 * también puede quedarse quieto un rato. Es una señal para desconfiar, no
 * un descarte automático: por eso no oculta la fila, la marca.
 */
public class StalenessTracker {

    private final int staleAfterCycles;
    private final Map<String, BigDecimal> lastPrice = new HashMap<>();
    private final Map<String, Integer> unchangedCycles = new HashMap<>();

    public StalenessTracker(int staleAfterCycles) {
        if (staleAfterCycles < 1) {
            throw new IllegalArgumentException("staleAfterCycles debe ser >= 1");
        }
        this.staleAfterCycles = staleAfterCycles;
    }

    /**
     * Registra una observación de precio para {@code key} y dice si ya
     * lleva {@code staleAfterCycles} ciclos o más sin cambiar.
     */
    public boolean observe(String key, BigDecimal price) {
        BigDecimal previous = lastPrice.put(key, price);
        if (previous != null && previous.compareTo(price) == 0) {
            int cycles = unchangedCycles.merge(key, 1, Integer::sum);
            return cycles >= staleAfterCycles;
        }
        unchangedCycles.put(key, 0);
        return false;
    }
}
