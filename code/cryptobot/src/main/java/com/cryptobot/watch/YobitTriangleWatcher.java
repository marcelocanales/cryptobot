package com.cryptobot.watch;

import com.cryptobot.marketdata.yobit.YobitConnector;

import java.io.IOException;

/**
 * Sprint 0018 — versión continua de {@code YobitTriangleCheck}: 340
 * triángulos de YoBit anclados en USDT, 393 order books únicos (medido en
 * vivo antes de construir — ver docs/sprints/sprint_0018.md). Ancla BTC/ETH
 * queda fuera de alcance a propósito (ver el javadoc de
 * {@code YobitTriangleCheck}).
 *
 * Uso: mvn exec:java -Dexec.mainClass=com.cryptobot.watch.YobitTriangleWatcher
 * Parar con Ctrl+C — cada ciclo se guarda (flush) antes del siguiente.
 */
public class YobitTriangleWatcher {

    public static void main(String[] args) throws IOException {
        TriangleWatchRunner.run(new YobitConnector(), "YoBit", "USDT", "yobit-triangle-watch");
    }
}
