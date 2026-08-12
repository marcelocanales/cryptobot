package com.cryptobot.triangular;

import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PriceLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriangleSpreadTest {

    // El ejemplo ilustrativo de docs/estrategias/02-triangular-intra-exchange.md:
    // BTC/USDT=50.000, ETH/BTC=0,05, ETH/USDT=2.510 -> +0,4% bruto antes de fees.
    private static final Triangle TRIANGLE = new Triangle(
        "USDT", "BTC", "ETH",
        new Market("BTC", "USDT", "BTC_USDT"),
        new Market("ETH", "BTC", "ETH_BTC"),
        new Market("ETH", "USDT", "ETH_USDT")
    );

    private static OrderBook book(String symbol, BigDecimal bid, BigDecimal ask) {
        var qty = new BigDecimal("10"); // suficiente para pasar cualquier umbral de nocional del test
        return new OrderBook("Poloniex", symbol, Instant.now(),
            List.of(new PriceLevel(bid, qty)), List.of(new PriceLevel(ask, qty)));
    }

    @Test
    void reproducesTheIllustrativeExampleFromTheStrategyDoc() {
        Map<String, OrderBook> books = Map.of(
            "BTC_USDT", book("BTC_USDT", new BigDecimal("49990"), new BigDecimal("50000")),
            "ETH_BTC", book("ETH_BTC", new BigDecimal("0.0499"), new BigDecimal("0.05")),
            "ETH_USDT", book("ETH_USDT", new BigDecimal("2510"), new BigDecimal("2511"))
        );

        TriangleSpread.Result r = TriangleSpread.evaluateForward("Poloniex", TRIANGLE, books).orElseThrow();

        // bruto: 1/50000 (compra BTC) / 0.05 (compra ETH) * 2510 (vende ETH) = 1.004 -> +0.4%
        assertEquals(0, new BigDecimal("0.4").compareTo(r.grossPct().setScale(1, java.math.RoundingMode.HALF_UP)));
        // con 3 fees de Poloniex (0.20% cada una) el bruto de 0.4% no alcanza a cubrirlas -> neto negativo
        assertTrue(r.netPct().signum() < 0, "esperaba neto negativo, fue " + r.netPct());
    }

    @Test
    void missingLiquidityOnAnyLegIsEmpty() {
        Map<String, OrderBook> books = Map.of(
            "BTC_USDT", book("BTC_USDT", new BigDecimal("49990"), new BigDecimal("50000")),
            "ETH_BTC", book("ETH_BTC", new BigDecimal("0.0499"), new BigDecimal("0.05"))
            // falta ETH_USDT
        );

        Optional<TriangleSpread.Result> result = TriangleSpread.evaluateForward("Poloniex", TRIANGLE, books);

        assertTrue(result.isEmpty());
    }

    @Test
    void legsRecordWhichSymbolAndSideEachStepUsed() {
        Map<String, OrderBook> books = Map.of(
            "BTC_USDT", book("BTC_USDT", new BigDecimal("49990"), new BigDecimal("50000")),
            "ETH_BTC", book("ETH_BTC", new BigDecimal("0.0499"), new BigDecimal("0.05")),
            "ETH_USDT", book("ETH_USDT", new BigDecimal("2510"), new BigDecimal("2511"))
        );

        TriangleSpread.Result r = TriangleSpread.evaluateForward("Poloniex", TRIANGLE, books).orElseThrow();

        // USDT->BTC (compra, ask) -> BTC->ETH (compra, ask) -> ETH->USDT (venta, bid)
        assertEquals(3, r.legs().size());
        assertEquals(new TriangleSpread.Leg("BTC_USDT", "ask", new BigDecimal("50000")), r.legs().get(0));
        assertEquals(new TriangleSpread.Leg("ETH_BTC", "ask", new BigDecimal("0.05")), r.legs().get(1));
        assertEquals(new TriangleSpread.Leg("ETH_USDT", "bid", new BigDecimal("2510")), r.legs().get(2));
    }

    @Test
    void backwardDirectionUsesTheOppositeLegOrder() {
        Map<String, OrderBook> books = Map.of(
            "BTC_USDT", book("BTC_USDT", new BigDecimal("49990"), new BigDecimal("50000")),
            "ETH_BTC", book("ETH_BTC", new BigDecimal("0.0499"), new BigDecimal("0.05")),
            "ETH_USDT", book("ETH_USDT", new BigDecimal("2510"), new BigDecimal("2511"))
        );

        TriangleSpread.Result forward = TriangleSpread.evaluateForward("Poloniex", TRIANGLE, books).orElseThrow();
        TriangleSpread.Result backward = TriangleSpread.evaluateBackward("Poloniex", TRIANGLE, books).orElseThrow();

        assertEquals(List.of("USDT", "BTC", "ETH", "USDT"), forward.path());
        assertEquals(List.of("USDT", "ETH", "BTC", "USDT"), backward.path());
    }
}
