package com.cryptobot;

import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PriceLevel;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;

import java.math.RoundingMode;

/**
 * Sprint 0002 — primer paso: traer un order book real de Poloniex y mostrar
 * el spread ejecutable (ask/bid real, no "último precio"). NotBank se suma
 * en el siguiente paso del sprint, una vez confirmado su contrato de API.
 */
public class Main {

    public static void main(String[] args) {
        String symbol = args.length > 0 ? args[0] : "LTC_USDT";

        var poloniex = new PoloniexConnector();
        OrderBook book = poloniex.fetchOrderBook(symbol);

        PriceLevel bestBid = book.bestBid();
        PriceLevel bestAsk = book.bestAsk();

        System.out.println(book.exchange() + " " + book.symbol() + " @ " + book.timestamp());
        System.out.println("  Mejor bid (vender acá): " + bestBid.price() + " (cantidad: " + bestBid.quantity() + ")");
        System.out.println("  Mejor ask (comprar acá): " + bestAsk.price() + " (cantidad: " + bestAsk.quantity() + ")");

        var spread = bestAsk.price().subtract(bestBid.price());
        var spreadPct = spread
            .divide(bestBid.price(), 6, RoundingMode.HALF_UP)
            .multiply(java.math.BigDecimal.valueOf(100));
        System.out.println("  Spread: " + spread + " (" + spreadPct + "%)");
    }
}
