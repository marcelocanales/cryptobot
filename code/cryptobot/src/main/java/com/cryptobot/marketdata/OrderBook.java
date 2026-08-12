package com.cryptobot.marketdata;

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
}
