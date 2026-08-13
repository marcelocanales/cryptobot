package com.cryptobot;

import com.cryptobot.marketdata.NetSpread;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.ParallelFetch;
import com.cryptobot.marketdata.PriceLevel;
import com.cryptobot.marketdata.TrackedAsset;
import com.cryptobot.marketdata.TrackedAssets;
import com.cryptobot.marketdata.binance.BinanceConnector;
import com.cryptobot.marketdata.bitfinex.BitfinexConnector;
import com.cryptobot.marketdata.buda.BudaConnector;
import com.cryptobot.marketdata.coinex.CoinExConnector;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.marketdata.yobit.YobitConnector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        var coinex = new CoinExConnector();
        var bitfinex = new BitfinexConnector();
        var binance = new BinanceConnector();

        List<TrackedAsset> assets = TrackedAssets.all(poloniex, notBank, buda, yobit, coinex, bitfinex, binance);

        List<ParallelFetch.FetchTask<String, OrderBook>> fetchTasks = new ArrayList<>();
        for (TrackedAsset asset : assets) {
            for (TrackedAsset.Venue venue : asset.venues()) {
                String key = venue.exchangeName() + "|" + venue.symbol();
                fetchTasks.add(new ParallelFetch.FetchTask<>(key, venue.exchangeName(),
                    () -> venue.connector().fetchOrderBook(venue.symbol())));
            }
        }
        ParallelFetch.Outcome<String, OrderBook> outcome = ParallelFetch.fetchAll(fetchTasks);
        for (Map.Entry<String, String> error : outcome.errors().entrySet()) {
            System.out.println("  ERROR (" + error.getKey() + "): " + error.getValue());
        }

        for (TrackedAsset asset : assets) {
            System.out.println("=== " + asset.label() + " ===");
            checkAsset(asset, outcome.results());
            System.out.println();
        }
    }

    private record VenueBook(String exchange, OrderBook book) {
    }

    private static void checkAsset(TrackedAsset asset, Map<String, OrderBook> books) {
        List<VenueBook> venueBooks = new ArrayList<>();
        for (TrackedAsset.Venue venue : asset.venues()) {
            OrderBook book = books.get(venue.exchangeName() + "|" + venue.symbol());
            if (book != null) {
                venueBooks.add(new VenueBook(venue.exchangeName(), book));
            }
        }

        for (int i = 0; i < venueBooks.size(); i++) {
            for (int j = i + 1; j < venueBooks.size(); j++) {
                VenueBook a = venueBooks.get(i);
                VenueBook b = venueBooks.get(j);
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
