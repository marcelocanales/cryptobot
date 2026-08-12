package com.cryptobot;

import com.cryptobot.marketdata.NetSpread;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PriceLevel;
import com.cryptobot.marketdata.TrackedAsset;
import com.cryptobot.marketdata.TrackedAssets;
import com.cryptobot.marketdata.buda.BudaConnector;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.marketdata.yobit.YobitConnector;

import java.util.List;
import java.util.Optional;

/**
 * Sprint 0005/0007 — spread neto en vivo (foto única, no continua) entre
 * todos los exchanges que cotizan cada activo de {@link TrackedAssets}, en
 * todas las combinaciones posibles — no solo las elegidas a mano. Misma
 * fuente de "qué activo vive en qué exchange" que usa {@code SpreadWatcher}
 * para la corrida continua.
 */
public class OverlapCheck {

    public static void main(String[] args) {
        var poloniex = new PoloniexConnector();
        var notBank = new NotBankConnector();
        var buda = new BudaConnector();
        var yobit = new YobitConnector();

        List<TrackedAsset> assets = TrackedAssets.all(poloniex, notBank, buda, yobit);

        for (TrackedAsset asset : assets) {
            System.out.println("=== " + asset.label() + " ===");
            checkAsset(asset);
            System.out.println();
        }
    }

    private record VenueBook(String exchange, OrderBook book) {
    }

    private static void checkAsset(TrackedAsset asset) {
        List<VenueBook> books = asset.venues().stream()
            .map(venue -> {
                try {
                    return new VenueBook(venue.exchangeName(), venue.connector().fetchOrderBook(venue.symbol()));
                } catch (RuntimeException e) {
                    System.out.println("  ERROR (" + venue.exchangeName() + "): " + e.getMessage());
                    return null;
                }
            })
            .filter(vb -> vb != null)
            .toList();

        for (int i = 0; i < books.size(); i++) {
            for (int j = i + 1; j < books.size(); j++) {
                VenueBook a = books.get(i);
                VenueBook b = books.get(j);
                PriceLevel askA = a.book().bestAskAbove(asset.minNotional());
                PriceLevel bidA = a.book().bestBidAbove(asset.minNotional());
                PriceLevel askB = b.book().bestAskAbove(asset.minNotional());
                PriceLevel bidB = b.book().bestBidAbove(asset.minNotional());

                checkDirection(a.exchange(), b.exchange(), asset.quoteCurrency(), askA, bidB);
                checkDirection(b.exchange(), a.exchange(), asset.quoteCurrency(), askB, bidA);
            }
        }
    }

    /**
     * Neto, no bruto: al spread bruto se le resta la fee de taker de ambas
     * patas (modelo de ejecución taker-taker, ver docs/roadmap.md).
     */
    private static void checkDirection(String buyExchange, String sellExchange, String quoteCurrency,
                                        PriceLevel buyAt, PriceLevel sellAt) {
        String label = "Comprar en " + buyExchange + ", vender en " + sellExchange;
        Optional<NetSpread.Result> result = NetSpread.evaluate(buyExchange, sellExchange, quoteCurrency, buyAt, sellAt);
        if (result.isEmpty()) {
            System.out.println("  " + label + ": sin liquidez suficiente para el nocional mínimo");
            return;
        }

        NetSpread.Result r = result.get();
        String verdict = r.isPositive() ? "spread NETO positivo — arbitraje real" : "sin arbitraje neto";
        System.out.println("  " + label + ": comprar a " + r.buyAt().price() + ", vender a " + r.sellAt().price()
            + " -> bruto " + r.grossPct() + "%, fees " + r.feesPct() + "%, neto " + r.netPct() + "% — " + verdict);
    }
}
