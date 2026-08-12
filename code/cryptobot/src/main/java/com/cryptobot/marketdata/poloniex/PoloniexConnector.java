package com.cryptobot.marketdata.poloniex;

import com.cryptobot.marketdata.ExchangeApiException;
import com.cryptobot.marketdata.ExchangeConnector;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PriceLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only connector for Poloniex's public spot market data API — no auth,
 * no key. Symbol format: "{BASE}_{QUOTE}", e.g. "LTC_USDT", "BTC_USDT".
 *
 * Verified live 2026-08 against https://api.poloniex.com/markets/{symbol}/orderBook —
 * see docs/entorno.md for the endpoint contract.
 */
public class PoloniexConnector implements ExchangeConnector {

    private static final String BASE_URL = "https://api.poloniex.com";
    private static final int DEFAULT_DEPTH = 20;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PoloniexConnector() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String exchangeName() {
        return "Poloniex";
    }

    @Override
    public OrderBook fetchOrderBook(String symbol) {
        var uri = URI.create(BASE_URL + "/markets/" + symbol + "/orderBook?limit=" + DEFAULT_DEPTH);
        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a Poloniex para " + symbol, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a Poloniex interrumpida para " + symbol, e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "Poloniex respondió " + response.statusCode() + " para " + symbol + ": " + response.body());
        }

        return parseOrderBook(symbol, response.body());
    }

    /**
     * Lista todos los mercados activos (estado {@code NORMAL}) — hace falta
     * para descubrir triángulos reales en vez de tenerlos hardcodeados
     * (Sprint 0009). Se llama una sola vez, no por ciclo — la lista de
     * mercados cambia rara vez.
     */
    public List<Market> fetchMarkets() {
        var uri = URI.create(BASE_URL + "/markets");
        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a Poloniex para listar mercados", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a Poloniex interrumpida al listar mercados", e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "Poloniex respondió " + response.statusCode() + " al listar mercados: " + response.body());
        }

        return parseMarkets(response.body());
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    List<Market> parseMarkets(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (IOException e) {
            throw new ExchangeApiException("Respuesta de Poloniex no es JSON válido para /markets: " + json, e);
        }
        if (!root.isArray()) {
            throw new ExchangeApiException("Respuesta de Poloniex para /markets no es un array: " + json);
        }

        List<Market> markets = new ArrayList<>();
        for (JsonNode m : root) {
            if (!"NORMAL".equals(m.path("state").asText())) {
                continue;
            }
            markets.add(new Market(
                m.get("baseCurrencyName").asText(),
                m.get("quoteCurrencyName").asText(),
                m.get("symbol").asText()));
        }
        return markets;
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    OrderBook parseOrderBook(String symbol, String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (IOException e) {
            throw new ExchangeApiException("Respuesta de Poloniex no es JSON válido para " + symbol + ": " + json, e);
        }

        List<PriceLevel> bids = parseLevels(root.get("bids"));
        List<PriceLevel> asks = parseLevels(root.get("asks"));
        Instant timestamp = Instant.ofEpochMilli(root.get("ts").asLong());

        return new OrderBook("Poloniex", symbol, timestamp, bids, asks);
    }

    /**
     * Poloniex returns bids/asks as a flat array: [price, qty, price, qty, ...],
     * both as strings (never parse prices as double).
     */
    private List<PriceLevel> parseLevels(JsonNode flatArray) {
        if (flatArray == null || !flatArray.isArray()) {
            throw new ExchangeApiException("Respuesta de Poloniex sin bids/asks esperados");
        }
        List<PriceLevel> levels = new ArrayList<>(flatArray.size() / 2);
        for (int i = 0; i + 1 < flatArray.size(); i += 2) {
            BigDecimal price = new BigDecimal(flatArray.get(i).asText());
            BigDecimal quantity = new BigDecimal(flatArray.get(i + 1).asText());
            levels.add(new PriceLevel(price, quantity));
        }
        return levels;
    }
}
