package com.cryptobot.marketdata;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Foto de un contrato perpetuo — precio (mark, mejor bid/ask) y funding rate
 * actual, con la hora del funding actual y del siguiente (de ahí se deriva
 * el intervalo real, no se asume — ver {@link #fundingInterval()}).
 */
public record PerpQuote(String symbol, BigDecimal markPrice, PriceLevel bestBid, PriceLevel bestAsk,
                         BigDecimal fundingRatePct, Instant fundingTime, Instant nextFundingTime) {

    public Duration fundingInterval() {
        return Duration.between(fundingTime, nextFundingTime);
    }
}
