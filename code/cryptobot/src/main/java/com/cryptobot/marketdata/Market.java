package com.cryptobot.marketdata;

/**
 * Un mercado listado en un exchange — activo base, moneda de cotización, y
 * el símbolo nativo con el que se pide su order book. Resultado de listar
 * TODOS los mercados de un exchange (no un símbolo elegido a mano) — hace
 * falta para descubrir triángulos reales en vez de tenerlos hardcodeados.
 */
public record Market(String base, String quote, String symbol) {
}
