package com.cryptobot.marketdata;

import java.math.BigDecimal;

/**
 * A single price/quantity level of an order book.
 * BigDecimal, never double — this is money.
 */
public record PriceLevel(BigDecimal price, BigDecimal quantity) {
}
