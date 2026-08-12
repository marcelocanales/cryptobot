package com.cryptobot;

import com.cryptobot.marketdata.ExchangeConnector;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PriceLevel;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Sprint 0002 — spread real cruzado entre Poloniex y NotBank, en un par
 * líquido de control (LTC/USDT), calculado con ask/bid ejecutable — no
 * "último precio". Mismo cálculo que se hizo a mano toda la sesión de
 * exploración manual, ahora programado.
 */
public class Main {

    public static void main(String[] args) {
        OrderBook poloniexBook = new PoloniexConnector().fetchOrderBook("LTC_USDT");
        OrderBook notBankBook = new NotBankConnector().fetchOrderBook("LTCUSDT");

        printBook(poloniexBook);
        System.out.println();
        printBook(notBankBook);
        System.out.println();

        checkDirection("Comprar en " + poloniexBook.exchange() + ", vender en " + notBankBook.exchange(),
            poloniexBook.bestAsk(), notBankBook.bestBid());
        checkDirection("Comprar en " + notBankBook.exchange() + ", vender en " + poloniexBook.exchange(),
            notBankBook.bestAsk(), poloniexBook.bestBid());
    }

    private static void printBook(OrderBook book) {
        PriceLevel bestBid = book.bestBid();
        PriceLevel bestAsk = book.bestAsk();
        BigDecimal spread = bestAsk.price().subtract(bestBid.price());
        BigDecimal spreadPct = percent(spread, bestBid.price());

        System.out.println(book.exchange() + " " + book.symbol() + " @ " + book.timestamp());
        System.out.println("  Mejor bid (vender acá): " + bestBid.price() + " (cantidad: " + bestBid.quantity() + ")");
        System.out.println("  Mejor ask (comprar acá): " + bestAsk.price() + " (cantidad: " + bestAsk.quantity() + ")");
        System.out.println("  Spread propio: " + spread + " (" + spreadPct + "%)");
    }

    private static void checkDirection(String label, PriceLevel buyAt, PriceLevel sellAt) {
        BigDecimal diff = sellAt.price().subtract(buyAt.price());
        BigDecimal diffPct = percent(diff, buyAt.price());
        String verdict = diff.signum() > 0 ? "posible spread bruto" : "pérdida — sin arbitraje";
        System.out.println(label + ": comprar a " + buyAt.price() + ", vender a " + sellAt.price()
            + " -> " + diff + " (" + diffPct + "%) — " + verdict);
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal base) {
        return amount.divide(base, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }
}
