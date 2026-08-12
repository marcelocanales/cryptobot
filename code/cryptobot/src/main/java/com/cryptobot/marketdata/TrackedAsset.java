package com.cryptobot.marketdata;

import java.math.BigDecimal;
import java.util.List;

/**
 * Un activo (con su moneda de cotización) y los exchanges donde se puede
 * operar — ej. "BTC/USDT" en Poloniex, NotBank y YoBit; "LTC/BTC" en Buda
 * y NotBank. Fuente única de esta información: antes vivía duplicada (y ya
 * desalineada) entre la lista de pares de {@code SpreadWatcher} y la de
 * {@code OverlapCheck} — ver {@link TrackedAssets}.
 */
public record TrackedAsset(String label, BigDecimal minNotional, List<Venue> venues) {

    /** Moneda de cotización, ej. "USDT" para "BTC/USDT" — usada por {@link ExchangeFees}. */
    public String quoteCurrency() {
        return label.substring(label.indexOf('/') + 1);
    }

    /** Un exchange concreto donde este activo cotiza, con su símbolo nativo. */
    public record Venue(ExchangeConnector connector, String symbol) {
        public String exchangeName() {
            return connector.exchangeName();
        }
    }
}
