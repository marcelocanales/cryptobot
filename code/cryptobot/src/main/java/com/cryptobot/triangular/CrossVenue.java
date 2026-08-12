package com.cryptobot.triangular;

import com.cryptobot.marketdata.ExchangeConnector;
import com.cryptobot.marketdata.Market;

/**
 * Un mercado concreto, en un exchange concreto — la unidad con la que
 * {@link CrossTriangleFinder} arma triángulos que pueden repartirse entre
 * varios exchanges (Sprint 0012). Análogo a {@code TrackedAsset.Venue}, pero
 * acá hace falta también el {@link Market} (base/quote), no solo el símbolo.
 */
public record CrossVenue(ExchangeConnector connector, Market market) {
    public String exchangeName() {
        return connector.exchangeName();
    }
}
