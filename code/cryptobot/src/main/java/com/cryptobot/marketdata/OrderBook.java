package com.cryptobot.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Order book snapshot for one symbol on one exchange.
 * bids/asks come pre-sorted best-first (highest bid first, lowest ask first) —
 * that ordering is the exchange's contract, connectors must preserve it.
 */
public record OrderBook(
    String exchange,
    String symbol,
    Instant timestamp,
    List<PriceLevel> bids,
    List<PriceLevel> asks
) {
    public PriceLevel bestBid() {
        return bids.isEmpty() ? null : bids.get(0);
    }

    public PriceLevel bestAsk() {
        return asks.isEmpty() ? null : asks.get(0);
    }

    /**
     * El top-of-book "de vidriera" puede ser una sola orden chica y vieja,
     * lejos del resto del libro (lo vimos en vivo con XTZ en Poloniex: un
     * bid de 0,36 con 0,0278 de cantidad, mientras el resto del libro
     * estaba agrupado cerca de 0,19). Esto devuelve el mejor nivel cuyo
     * valor nocional (precio × cantidad) alcanza un mínimo — o null si
     * ninguno lo alcanza.
     */
    public PriceLevel bestBidAbove(BigDecimal minNotional) {
        return firstAboveNotional(bids, minNotional);
    }

    public PriceLevel bestAskAbove(BigDecimal minNotional) {
        return firstAboveNotional(asks, minNotional);
    }

    private static PriceLevel firstAboveNotional(List<PriceLevel> levels, BigDecimal minNotional) {
        for (PriceLevel level : levels) {
            if (level.price().multiply(level.quantity()).compareTo(minNotional) >= 0) {
                return level;
            }
        }
        return null;
    }
}
