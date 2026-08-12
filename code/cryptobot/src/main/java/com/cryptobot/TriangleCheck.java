package com.cryptobot;

import com.cryptobot.marketdata.poloniex.PoloniexConnector;

/**
 * Sprint 0009 — foto en vivo (no continua todavía) de todos los triángulos
 * reales descubiertos en Poloniex, anclados en USDT. No hay una lista
 * hardcodeada de qué activos "deberían" formar un triángulo — se descubren
 * a partir de {@code GET /markets}, igual que {@code OverlapCheck} hace con
 * los pares de spot cross-exchange. Orquestación real en
 * {@link TriangleCheckRunner} (Sprint 0018 — extraída para reusarla con
 * otros exchanges, ver {@code YobitTriangleCheck}).
 */
public class TriangleCheck {

    public static void main(String[] args) {
        TriangleCheckRunner.run(new PoloniexConnector(), "Poloniex", "USDT");
    }
}
