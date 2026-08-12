package com.cryptobot;

import com.cryptobot.marketdata.ExchangeConnector;
import com.cryptobot.marketdata.ExchangeFees;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PriceLevel;
import com.cryptobot.marketdata.buda.BudaConnector;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.marketdata.yobit.YobitConnector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Sprint 0005 — spread real en los pares donde Buda y/o YoBit overlapean
 * con Poloniex/NotBank. A diferencia de Main (Sprint 0002, LTC/USDT en
 * Poloniex vs. NotBank), acá el universo de activos de cada exchange manda
 * qué comparación es posible sin conversión de moneda:
 *
 * - YoBit cotiza BTC/ETH/LTC/DOGE/SHIB directo en USDT — comparable tal
 *   cual contra Poloniex y NotBank.
 * - Buda casi no tiene pares en USDT para altcoins (ver docs/entorno.md) —
 *   pero comparte BTC-CLP, ETH-CLP y LTC-BTC con NotBank, sin necesidad de
 *   convertir por el tipo de cambio USDT-CLP.
 */
public class OverlapCheck {

    // Umbral de valor nocional mínimo por moneda de cotización — un "mejor precio"
    // que no lo alcanza puede ser una orden vieja y chica aislada, no liquidez real
    // (mismo problema que XTZ en Poloniex, Sprint 0003/0004). ~USD 50 equivalente
    // en cada moneda: USDT 1:1, CLP ~950/USD, BTC ~64000 USD/BTC.
    private static final BigDecimal MIN_NOTIONAL_USDT = new BigDecimal("50");
    private static final BigDecimal MIN_NOTIONAL_CLP = new BigDecimal("47500");
    private static final BigDecimal MIN_NOTIONAL_BTC = new BigDecimal("0.00078");

    private record Pair(String label, ExchangeConnector connA, String symbolA,
                         ExchangeConnector connB, String symbolB, BigDecimal minNotional) {
    }

    public static void main(String[] args) {
        var poloniex = new PoloniexConnector();
        var notBank = new NotBankConnector();
        var buda = new BudaConnector();
        var yobit = new YobitConnector();

        List<Pair> pairs = List.of(
            new Pair("BTC/USDT — YoBit vs. Poloniex", yobit, "btc_usdt", poloniex, "BTC_USDT", MIN_NOTIONAL_USDT),
            new Pair("BTC/USDT — YoBit vs. NotBank", yobit, "btc_usdt", notBank, "BTCUSDT", MIN_NOTIONAL_USDT),
            new Pair("ETH/USDT — YoBit vs. Poloniex", yobit, "eth_usdt", poloniex, "ETH_USDT", MIN_NOTIONAL_USDT),
            new Pair("ETH/USDT — YoBit vs. NotBank", yobit, "eth_usdt", notBank, "ETHUSDT", MIN_NOTIONAL_USDT),
            new Pair("LTC/USDT — YoBit vs. Poloniex", yobit, "ltc_usdt", poloniex, "LTC_USDT", MIN_NOTIONAL_USDT),
            new Pair("LTC/USDT — YoBit vs. NotBank", yobit, "ltc_usdt", notBank, "LTCUSDT", MIN_NOTIONAL_USDT),
            new Pair("DOGE/USDT — YoBit vs. Poloniex", yobit, "doge_usdt", poloniex, "DOGE_USDT", MIN_NOTIONAL_USDT),
            new Pair("SHIB/USDT — YoBit vs. Poloniex", yobit, "shib_usdt", poloniex, "SHIB_USDT", MIN_NOTIONAL_USDT),
            new Pair("BTC/CLP — Buda vs. NotBank", buda, "btc-clp", notBank, "BTCCLP", MIN_NOTIONAL_CLP),
            new Pair("ETH/CLP — Buda vs. NotBank", buda, "eth-clp", notBank, "ETHCLP", MIN_NOTIONAL_CLP),
            new Pair("LTC/BTC — Buda vs. NotBank", buda, "ltc-btc", notBank, "LTCBTC", MIN_NOTIONAL_BTC)
        );

        for (Pair pair : pairs) {
            System.out.println("=== " + pair.label() + " ===");
            try {
                OrderBook bookA = pair.connA().fetchOrderBook(pair.symbolA());
                OrderBook bookB = pair.connB().fetchOrderBook(pair.symbolB());

                PriceLevel askA = bookA.bestAskAbove(pair.minNotional());
                PriceLevel bidA = bookA.bestBidAbove(pair.minNotional());
                PriceLevel askB = bookB.bestAskAbove(pair.minNotional());
                PriceLevel bidB = bookB.bestBidAbove(pair.minNotional());

                checkDirection(bookA.exchange(), bookB.exchange(), askA, bidB);
                checkDirection(bookB.exchange(), bookA.exchange(), askB, bidA);
            } catch (RuntimeException e) {
                System.out.println("  ERROR: " + e.getMessage());
            }
            System.out.println();
        }
    }

    /**
     * Neto, no bruto: al spread bruto se le resta la fee de taker de ambas
     * patas (el modelo de ejecución asumido es taker-taker — dos órdenes de
     * mercado, ver docs/roadmap.md). Un spread bruto positivo que no cubre
     * las dos fees no es arbitraje, es ruido — desde el Sprint 0002 esto se
     * venía descartando a mano, ahora lo calcula el propio programa.
     */
    private static void checkDirection(String buyExchange, String sellExchange, PriceLevel buyAt, PriceLevel sellAt) {
        String label = "Comprar en " + buyExchange + ", vender en " + sellExchange;
        if (buyAt == null || sellAt == null) {
            System.out.println("  " + label + ": sin liquidez suficiente para el nocional mínimo");
            return;
        }
        BigDecimal grossPct = percent(sellAt.price().subtract(buyAt.price()), buyAt.price());
        BigDecimal feesPct = ExchangeFees.takerFee(buyExchange).add(ExchangeFees.takerFee(sellExchange))
            .multiply(BigDecimal.valueOf(100));
        BigDecimal netPct = grossPct.subtract(feesPct);

        String verdict = netPct.signum() > 0 ? "spread NETO positivo — arbitraje real" : "sin arbitraje neto";
        System.out.println("  " + label + ": comprar a " + buyAt.price() + ", vender a " + sellAt.price()
            + " -> bruto " + grossPct + "%, fees " + feesPct + "%, neto " + netPct + "% — " + verdict);
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal base) {
        return amount.divide(base, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }
}
