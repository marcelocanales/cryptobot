package com.cryptobot.marketdata.yobit;

import com.cryptobot.marketdata.ExchangeApiException;
import com.cryptobot.marketdata.ExchangeConnector;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PriceLevel;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Read-only connector for YoBit's public spot market data API — no auth, no key.
 * Symbol format: "{base}_{quote}" en minúsculas, ej. "ltc_usdt", "btc_usdt".
 * A diferencia de Poloniex/Buda, YoBit manda precio y cantidad como número JSON,
 * no como string — hay que parsearlos con BigDecimal exacto (nunca vía double,
 * ver {@link #objectMapper}).
 *
 * Verificado en vivo 2026-08 contra https://yobit.net/api/3/depth/{symbol}?limit=N.
 * La respuesta no trae timestamp propio — se usa el momento de la respuesta.
 *
 * Ojo con los errores: YoBit responde HTTP 200 incluso para un par inválido,
 * con {"success":0,"error":"..."} en el body — no es un libro vacío.
 */
public class YobitConnector implements ExchangeConnector {

    private static final String BASE_URL = "https://yobit.net/api/3";
    private static final int DEFAULT_DEPTH = 20;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public YobitConnector() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper()
            // Sin esto, un número flotante del JSON se parsea primero a double
            // (perdiendo precisión) y recién después a BigDecimal. Con esto,
            // Jackson arma el BigDecimal directo desde el texto del token.
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    @Override
    public String exchangeName() {
        return "YoBit";
    }

    @Override
    public OrderBook fetchOrderBook(String symbol) {
        var uri = URI.create(BASE_URL + "/depth/" + symbol + "?limit=" + DEFAULT_DEPTH);
        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a YoBit para " + symbol, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a YoBit interrumpida para " + symbol, e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "YoBit respondió " + response.statusCode() + " para " + symbol + ": " + response.body());
        }

        return parseOrderBook(symbol, response.body());
    }

    /**
     * Lista todos los pares no ocultos — hace falta para descubrir activos
     * compartidos entre exchanges en vez de tenerlos hardcodeados (Sprint
     * 0017). La key de cada par ya es el símbolo nativo (ej. "ltc_usdt") —
     * confirmado en el Sprint 0007 que los 9.033 pares tienen exactamente
     * un underscore, separar en base/quote es seguro.
     */
    public List<Market> fetchMarkets() {
        var uri = URI.create(BASE_URL + "/info");
        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a YoBit para listar pares", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a YoBit interrumpida al listar pares", e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "YoBit respondió " + response.statusCode() + " al listar pares: " + response.body());
        }

        return parseMarkets(response.body());
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    List<Market> parseMarkets(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (IOException e) {
            throw new ExchangeApiException("Respuesta de YoBit no es JSON válido para /info: " + json, e);
        }
        JsonNode pairs = root.get("pairs");
        if (pairs == null) {
            throw new ExchangeApiException("Respuesta de YoBit sin pares esperados: " + json);
        }

        List<Market> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = pairs.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue().path("hidden").asInt(0) == 1) {
                continue;
            }
            String[] parts = entry.getKey().split("_", 2);
            if (parts.length != 2) {
                continue; // no debería pasar (confirmado en vivo, Sprint 0007), defensivo igual
            }
            result.add(new Market(parts[0].toUpperCase(), parts[1].toUpperCase(), entry.getKey()));
        }
        return result;
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    OrderBook parseOrderBook(String symbol, String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (IOException e) {
            throw new ExchangeApiException("Respuesta de YoBit no es JSON válido para " + symbol + ": " + json, e);
        }

        JsonNode success = root.get("success");
        if (success != null && success.asInt() == 0) {
            String error = root.path("error").asText("(sin detalle)");
            throw new ExchangeApiException("YoBit rechazó el símbolo " + symbol + ": " + error);
        }

        JsonNode pairBook = root.get(symbol);
        if (pairBook == null) {
            throw new ExchangeApiException("Respuesta de YoBit sin datos para " + symbol + ": " + json);
        }

        List<PriceLevel> bids = parseLevels(pairBook.get("bids"));
        List<PriceLevel> asks = parseLevels(pairBook.get("asks"));

        return new OrderBook("YoBit", symbol, Instant.now(), bids, asks);
    }

    /**
     * YoBit devuelve bids/asks como array de pares [price, qty], ambos como
     * número JSON (no string) — ver {@link #objectMapper}.
     */
    private List<PriceLevel> parseLevels(JsonNode pairsArray) {
        if (pairsArray == null || !pairsArray.isArray()) {
            throw new ExchangeApiException("Respuesta de YoBit sin bids/asks esperados");
        }
        List<PriceLevel> levels = new ArrayList<>(pairsArray.size());
        for (JsonNode pair : pairsArray) {
            BigDecimal price = pair.get(0).decimalValue();
            BigDecimal quantity = pair.get(1).decimalValue();
            levels.add(new PriceLevel(price, quantity));
        }
        return levels;
    }
}
