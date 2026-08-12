package com.cryptobot.triangular;

import com.cryptobot.marketdata.Market;

/**
 * Un ciclo de tres monedas dentro del mismo exchange, con el mercado real
 * que conecta cada par consecutivo — ej. USDT-BTC-ETH con los mercados
 * BTC/USDT, ETH/BTC y ETH/USDT. Ver docs/estrategias/02-triangular-intra-exchange.md.
 */
public record Triangle(String currencyA, String currencyB, String currencyC,
                        Market marketAB, Market marketBC, Market marketCA) {
}
