package com.cryptobot.watch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StalenessTrackerTest {

    @Test
    void firstObservationIsNeverStale() {
        var tracker = new StalenessTracker(3);
        assertFalse(tracker.observe("Poloniex:XTZ:ask", new BigDecimal("0.4999")));
    }

    @Test
    void becomesStaleOnlyAfterTheConfiguredNumberOfUnchangedCycles() {
        var tracker = new StalenessTracker(3);
        var price = new BigDecimal("0.4999");

        assertFalse(tracker.observe("k", price)); // primera vez: establece el precio base
        assertFalse(tracker.observe("k", price)); // 1ra repetición
        assertFalse(tracker.observe("k", price)); // 2da repetición
        assertTrue(tracker.observe("k", price));  // 3ra repetición -> stale
        assertTrue(tracker.observe("k", price));  // sigue stale mientras no cambie
    }

    @Test
    void aPriceChangeResetsTheCounter() {
        var tracker = new StalenessTracker(2);

        tracker.observe("k", new BigDecimal("1.00"));                // establece
        assertFalse(tracker.observe("k", new BigDecimal("1.00")));   // 1ra repetición
        assertTrue(tracker.observe("k", new BigDecimal("1.00")));    // 2da repetición -> stale

        assertFalse(tracker.observe("k", new BigDecimal("1.01")));   // cambió: resetea, nuevo precio base
        assertFalse(tracker.observe("k", new BigDecimal("1.01")));   // 1ra repetición del nuevo precio
        assertTrue(tracker.observe("k", new BigDecimal("1.01")));    // 2da repetición -> stale de nuevo
    }

    @Test
    void treatsDifferentScalesOfTheSameNumberAsUnchanged() {
        // "0.4999" y "0.49990" son el mismo número — BigDecimal.equals()
        // los trataría como distintos (por la escala), compareTo() no.
        var tracker = new StalenessTracker(2);
        tracker.observe("k", new BigDecimal("0.4999"));                // establece
        assertFalse(tracker.observe("k", new BigDecimal("0.49990")));  // 1ra repetición (misma magnitud)
        assertTrue(tracker.observe("k", new BigDecimal("0.4999")));    // 2da repetición -> stale
    }

    @Test
    void tracksEachKeyIndependently() {
        var tracker = new StalenessTracker(2);
        var price = new BigDecimal("1.00");

        tracker.observe("Poloniex:XTZ:ask", price);                       // establece
        tracker.observe("Poloniex:XTZ:ask", price);                       // 1ra repetición
        assertTrue(tracker.observe("Poloniex:XTZ:ask", price));           // 2da repetición -> stale

        // Una key distinta no hereda el conteo de la otra.
        assertFalse(tracker.observe("NotBank:XTZ:ask", price));
    }
}
