package com.cryptobot.marketdata.buda;

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
 * Read-only connector for Buda.com's public spot market data API — no auth,
 * no key. Symbol format: "{base}-{quote}" en minúsculas, ej. "ltc-clp", "btc-clp".
 * Universo de activos acotado: BTC, ETH, BCH, LTC, USDC, USDT, SOL — casi todo
 * cotizado en CLP/COP/PEN, no en USDT (ver docs/entorno.md).
 *
 * Verificado en vivo 2026-08 contra https://www.buda.com/api/v2/markets/{symbol}/order_book.
 * La respuesta no trae timestamp propio — se usa el momento de la respuesta.
 */
public class BudaConnector implements ExchangeConnector {

    private static final String BASE_URL = "https://www.buda.com/api/v2";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BudaConnector() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String exchangeName() {
        return "Buda";
    }

    @Override
    public OrderBook fetchOrderBook(String symbol) {
        var uri = URI.create(BASE_URL + "/markets/" + symbol + "/order_book");
        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a Buda para " + symbol, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a Buda interrumpida para " + symbol, e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "Buda respondió " + response.statusCode() + " para " + symbol + ": " + response.body());
        }

        return parseOrderBook(symbol, response.body());
    }

    /**
     * Lista todos los mercados activos — hace falta para descubrir activos
     * compartidos entre exchanges en vez de tenerlos hardcodeados (Sprint
     * 0017), mismo propósito que {@code PoloniexConnector.fetchMarkets()}
     * desde el Sprint 0009.
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
            throw new ExchangeApiException("No se pudo conectar a Buda para listar mercados", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a Buda interrumpida al listar mercados", e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "Buda respondió " + response.statusCode() + " al listar mercados: " + response.body());
        }

        return parseMarkets(response.body());
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    List<Market> parseMarkets(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (IOException e) {
            throw new ExchangeApiException("Respuesta de Buda no es JSON válido para /markets: " + json, e);
        }
        JsonNode markets = root.get("markets");
        if (markets == null || !markets.isArray()) {
            throw new ExchangeApiException("Respuesta de Buda sin lista de mercados esperada: " + json);
        }

        List<Market> result = new ArrayList<>();
        for (JsonNode m : markets) {
            if (m.path("disabled").asBoolean(false)) {
                continue;
            }
            result.add(new Market(
                m.get("base_currency").asText(),
                m.get("quote_currency").asText(),
                m.get("name").asText()));
        }
        return result;
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    OrderBook parseOrderBook(String symbol, String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (IOException e) {
            throw new ExchangeApiException("Respuesta de Buda no es JSON válido para " + symbol + ": " + json, e);
        }

        JsonNode orderBook = root.get("order_book");
        if (orderBook == null) {
            throw new ExchangeApiException("Respuesta de Buda sin order_book para " + symbol + ": " + json);
        }

        List<PriceLevel> bids = parseLevels(orderBook.get("bids"));
        List<PriceLevel> asks = parseLevels(orderBook.get("asks"));

        return new OrderBook("Buda", symbol, Instant.now(), bids, asks);
    }

    /**
     * Buda devuelve bids/asks como array de pares [price, qty], ambos strings
     * (nunca parsear precios como double).
     */
    private List<PriceLevel> parseLevels(JsonNode pairsArray) {
        if (pairsArray == null || !pairsArray.isArray()) {
            throw new ExchangeApiException("Respuesta de Buda sin bids/asks esperados");
        }
        List<PriceLevel> levels = new ArrayList<>(pairsArray.size());
        for (JsonNode pair : pairsArray) {
            BigDecimal price = new BigDecimal(pair.get(0).asText());
            BigDecimal quantity = new BigDecimal(pair.get(1).asText());
            levels.add(new PriceLevel(price, quantity));
        }
        return levels;
    }
}
