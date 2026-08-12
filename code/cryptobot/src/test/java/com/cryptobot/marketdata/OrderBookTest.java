package com.cryptobot.marketdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderBookTest {

    @Test
    void ignoresStaleTinyOrderAheadOfTheRealBook() {
        // Caso real: Poloniex XTZ_USDT, 2026-08-12. El primer bid (0.36,
        // cantidad 0.0278 -> ~$0.01 nocional) es una orden vieja y chica muy
        // lejos del resto del libro, agrupado cerca de 0.19.
        var bids = List.of(
            new PriceLevel(new BigDecimal("0.3600"), new BigDecimal("0.0278")),
            new PriceLevel(new BigDecimal("0.1941"), new BigDecimal("102.1623")),
            new PriceLevel(new BigDecimal("0.1940"), new BigDecimal("123.7113"))
        );
        var book = new OrderBook("Poloniex", "XTZ_USDT", Instant.now(), bids, List.of());

        // Sin filtro: la orden vieja "gana" y engaña.
        assertEquals(new BigDecimal("0.3600"), book.bestBid().price());

        // Con un mínimo nocional que sí distingue la orden vieja (~$0,01)
        // del resto del libro (~$19-24), se ignora y se usa el primer nivel
        // que representa liquidez real.
        PriceLevel realBest = book.bestBidAbove(BigDecimal.valueOf(15));
        assertEquals(new BigDecimal("0.1941"), realBest.price());
    }

    @Test
    void returnsNullWhenNoLevelHasEnoughLiquidity() {
        var bids = List.of(new PriceLevel(new BigDecimal("100"), new BigDecimal("0.01")));
        var book = new OrderBook("Test", "X_Y", Instant.now(), bids, List.of());

        assertNull(book.bestBidAbove(BigDecimal.valueOf(50)));
    }
}
