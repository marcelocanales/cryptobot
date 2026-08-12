package com.cryptobot.marketdata;

/**
 * Un mercado concreto, en un exchange concreto — la unidad con la que se
 * arman comparaciones o ciclos que pueden repartirse entre varios
 * exchanges (nació en el Sprint 0012 para triángulos cross-exchange, se
 * movió acá en el Sprint 0017 porque {@code TrackedAssets} también lo
 * necesita y no correspondía que `marketdata` dependiera de `triangular`).
 */
public record CrossVenue(ExchangeConnector connector, Market market) {
    public String exchangeName() {
        return connector.exchangeName();
    }
}
