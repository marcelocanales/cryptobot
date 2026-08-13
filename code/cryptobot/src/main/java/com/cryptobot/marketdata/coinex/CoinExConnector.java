package com.cryptobot.marketdata.coinex;

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
 * Read-only connector for CoinEx's public spot market data API — no auth,
 * no key. Symbol format: "{BASE}{QUOTE}" concatenado, ej. "BTCUSDT" — a
 * diferencia de YoBit, {@code GET /v2/spot/market} trae {@code base_ccy}/
 * {@code quote_ccy} como campos separados, no hay que adivinar dónde corta
 * el símbolo.
 *
 * Verificado en vivo 2026-08 contra https://api.coinex.com/v2/spot/depth y
 * https://api.coinex.com/v2/spot/market — Sprint 0021 (5to exchange,
 * elegido tras evaluar Latoken/CoinEx/Bitrue, ver docs/entorno.md).
 *
 * Ojo: como YoBit, un parámetro inválido responde HTTP 200 con
 * {@code "code" != 0} — no un book vacío, hay que chequear el campo.
 * {@code limit} debe ser uno de un enum fijo (5/10/20/50 confirmados
 * válidos en vivo).
 */
public class CoinExConnector implements ExchangeConnector {

    private static final String BASE_URL = "https://api.coinex.com/v2";
    private static final int DEFAULT_DEPTH = 20;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CoinExConnector() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String exchangeName() {
        return "CoinEx";
    }

    @Override
    public OrderBook fetchOrderBook(String symbol) {
        var uri = URI.create(BASE_URL + "/spot/depth?market=" + symbol + "&limit=" + DEFAULT_DEPTH + "&interval=0");
        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a CoinEx para " + symbol, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a CoinEx interrumpida para " + symbol, e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "CoinEx respondió " + response.statusCode() + " para " + symbol + ": " + response.body());
        }

        return parseOrderBook(symbol, response.body());
    }

    /**
     * Lista todos los mercados activos ({@code status == "online"}) — hace
     * falta para descubrir activos compartidos entre exchanges en vez de
     * tenerlos hardcodeados, mismo patrón que los otros 4 conectores.
     */
    public List<Market> fetchMarkets() {
        var uri = URI.create(BASE_URL + "/spot/market");
        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a CoinEx para listar mercados", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a CoinEx interrumpida al listar mercados", e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "CoinEx respondió " + response.statusCode() + " al listar mercados: " + response.body());
        }

        return parseMarkets(response.body());
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    List<Market> parseMarkets(String json) {
        JsonNode root = readTree(json, "al listar mercados");
        checkCode(root, json, "al listar mercados");

        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new ExchangeApiException("Respuesta de CoinEx sin lista de mercados esperada: " + json);
        }

        List<Market> result = new ArrayList<>();
        for (JsonNode m : data) {
            if (!"online".equals(m.path("status").asText())) {
                continue;
            }
            result.add(new Market(m.get("base_ccy").asText(), m.get("quote_ccy").asText(), m.get("market").asText()));
        }
        return result;
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    OrderBook parseOrderBook(String symbol, String json) {
        JsonNode root = readTree(json, "para " + symbol);
        checkCode(root, json, "para " + symbol);

        JsonNode depth = root.path("data").path("depth");
        if (depth.isMissingNode()) {
            throw new ExchangeApiException("Respuesta de CoinEx sin depth para " + symbol + ": " + json);
        }

        List<PriceLevel> bids = parseLevels(depth.get("bids"));
        List<PriceLevel> asks = parseLevels(depth.get("asks"));
        Instant timestamp = Instant.ofEpochMilli(depth.get("updated_at").asLong());

        return new OrderBook("CoinEx", symbol, timestamp, bids, asks);
    }

    /**
     * CoinEx devuelve bids/asks como array de pares [price, qty], ambos
     * strings (nunca parsear precios como double).
     */
    private List<PriceLevel> parseLevels(JsonNode pairsArray) {
        if (pairsArray == null || !pairsArray.isArray()) {
            throw new ExchangeApiException("Respuesta de CoinEx sin bids/asks esperados");
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
            throw new ExchangeApiException("Respuesta de CoinEx no es JSON válido " + context + ": " + json, e);
        }
    }

    /**
     * CoinEx responde HTTP 200 incluso para un parámetro inválido — el
     * error real está en "code" (0 = OK), igual que YoBit con "success".
     */
    private void checkCode(JsonNode root, String json, String context) {
        int code = root.path("code").asInt(-1);
        if (code != 0) {
            String message = root.path("message").asText("(sin detalle)");
            throw new ExchangeApiException("CoinEx rechazó la consulta " + context + " (code " + code + "): " + message);
        }
    }
}
