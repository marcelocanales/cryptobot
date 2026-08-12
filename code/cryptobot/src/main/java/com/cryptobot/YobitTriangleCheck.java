package com.cryptobot;

import com.cryptobot.marketdata.yobit.YobitConnector;

/**
 * Sprint 0018 — foto en vivo de los triángulos reales de YoBit, anclados en
 * USDT (340 triángulos, 393 order books únicos, medido en vivo antes de
 * construir — ver docs/sprints/sprint_0018.md). Ancla BTC/ETH queda
 * explícitamente fuera de alcance (15.036 candidatos combinados, y el
 * Sprint 0011 ya encontró que los pares cotizados en BTC suelen estar
 * dormidos incluso en un exchange más serio como Poloniex).
 */
public class YobitTriangleCheck {

    public static void main(String[] args) {
        TriangleCheckRunner.run(new YobitConnector(), "YoBit", "USDT");
    }
}
