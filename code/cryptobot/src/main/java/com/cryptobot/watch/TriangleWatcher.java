package com.cryptobot.watch;

import com.cryptobot.marketdata.poloniex.PoloniexConnector;

import java.io.IOException;

/**
 * Corre en loop, análogo a {@link SpreadWatcher} pero para arbitraje
 * triangular intra-exchange (Sprint 0009 → 0010): descubre los triángulos
 * reales de Poloniex una sola vez al arrancar (no cambian en el tiempo que
 * dura una corrida), y en cada ciclo evalúa las dos direcciones de cada
 * uno, con el mismo detector de precio congelado que ya probó su valor con
 * XTZ en el Sprint 0004. Orquestación real en {@link TriangleWatchRunner}
 * (Sprint 0018 — extraída para reusarla con otros exchanges, ver
 * {@code YobitTriangleWatcher}).
 *
 * Uso: mvn exec:java -Dexec.mainClass=com.cryptobot.watch.TriangleWatcher
 * Parar con Ctrl+C — cada ciclo se guarda (flush) antes del siguiente.
 */
public class TriangleWatcher {

    public static void main(String[] args) throws IOException {
        TriangleWatchRunner.run(new PoloniexConnector(), "Poloniex", "USDT", "triangle-watch");
    }
}
