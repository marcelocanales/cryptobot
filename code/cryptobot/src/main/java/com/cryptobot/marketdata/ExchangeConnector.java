package com.cryptobot.marketdata;

/**
 * Read-only market data access for one exchange. No trading, no auth —
 * see docs/metodologia.md: ningún capital real sin autorización explícita.
 */
public interface ExchangeConnector {

    String exchangeName();

    /**
     * @param symbol exchange-native symbol format (each connector documents its own,
     *               e.g. Poloniex uses "LTC_USDT")
     */
    OrderBook fetchOrderBook(String symbol);
}
