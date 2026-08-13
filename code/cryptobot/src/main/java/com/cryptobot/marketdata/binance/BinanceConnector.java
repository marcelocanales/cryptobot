package com.cryptobot.marketdata.binance;

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
 * Read-only connector for Binance's public spot market data API — no auth,
 * no key. Símbolo: {@code {BASE}{QUOTE}} concatenado (ej. {@code ZECUSDT}),
 * mismo formato que CoinEx. A diferencia de CoinEx/YoBit, un símbolo o
 * parámetro inválido responde HTTP no-200 (confirmado en vivo: 400 con
 * {@code {"code":-1121,"msg":"Invalid symbol."}}) — no hace falta el chequeo
 * de un campo "code" con HTTP 200, alcanza con el status code.
 *
 * Verificado en vivo 2026-08-13 contra {@code GET /api/v3/depth} y
 * {@code GET /api/v3/exchangeInfo} — Sprint 0028 (7mo exchange, fase 1 del
 * plan de la Etapa 3, ver docs/etapa3-plan.md). La respuesta de depth **no
 * trae timestamp propio** (solo {@code lastUpdateId}, un número de
 * secuencia, no un momento) — mismo tratamiento que {@code BudaConnector}:
 * se usa el momento de la respuesta.
 */
public class BinanceConnector implements ExchangeConnector {

    private static final String BASE_URL = "https://api.binance.com/api/v3";
    private static final int DEFAULT_DEPTH = 100;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BinanceConnector() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String exchangeName() {
        return "Binance";
    }

    @Override
    public OrderBook fetchOrderBook(String symbol) {
        var uri = URI.create(BASE_URL + "/depth?symbol=" + symbol + "&limit=" + DEFAULT_DEPTH);
        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a Binance para " + symbol, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a Binance interrumpida para " + symbol, e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "Binance respondió " + response.statusCode() + " para " + symbol + ": " + response.body());
        }

        return parseOrderBook(symbol, response.body());
    }

    /**
     * Lista todos los mercados activos ({@code status == "TRADING"}) — mismo
     * patrón que los otros 6 conectores. Ojo: la respuesta completa de
     * Binance es grande (~17,5MB, 3.681 símbolos totales, 1.378 en
     * TRADING — verificado en vivo) porque no hay forma de filtrar del lado
     * del servidor; se llama una sola vez al arranque, no por ciclo.
     */
    @Override
    public List<Market> fetchMarkets() {
        var uri = URI.create(BASE_URL + "/exchangeInfo");
        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a Binance para listar mercados", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a Binance interrumpida al listar mercados", e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "Binance respondió " + response.statusCode() + " al listar mercados: " + response.body());
        }

        return parseMarkets(response.body());
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    List<Market> parseMarkets(String json) {
        JsonNode root = readTree(json, "al listar mercados");
        JsonNode symbols = root.path("symbols");
        if (!symbols.isArray()) {
            throw new ExchangeApiException("Respuesta de Binance sin lista de símbolos esperada: " + json);
        }

        List<Market> result = new ArrayList<>();
        for (JsonNode s : symbols) {
            if (!"TRADING".equals(s.path("status").asText())) {
                continue;
            }
            result.add(new Market(s.get("baseAsset").asText(), s.get("quoteAsset").asText(), s.get("symbol").asText()));
        }
        return result;
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    OrderBook parseOrderBook(String symbol, String json) {
        JsonNode root = readTree(json, "para " + symbol);

        List<PriceLevel> bids = parseLevels(root.get("bids"));
        List<PriceLevel> asks = parseLevels(root.get("asks"));

        return new OrderBook("Binance", symbol, Instant.now(), bids, asks);
    }

    /**
     * Binance devuelve bids/asks como array de pares [price, qty], ambos
     * strings (nunca parsear precios como double).
     */
    private List<PriceLevel> parseLevels(JsonNode pairsArray) {
        if (pairsArray == null || !pairsArray.isArray()) {
            throw new ExchangeApiException("Respuesta de Binance sin bids/asks esperados");
        }
        List<PriceLevel> levels = new ArrayList<>(pairsArray.size());
        for (JsonNode pair : pairsArray) {
            BigDecimal price = new BigDecimal(pair.get(0).asText());
            BigDecimal quantity = new BigDecimal(pair.get(1).asText());
            levels.add(new PriceLevel(price, quantity));
        }
        return levels;
    }

    private JsonNode readTree(String json, String context) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new ExchangeApiException("Respuesta de Binance no es JSON válido " + context + ": " + json, e);
        }
    }
}
