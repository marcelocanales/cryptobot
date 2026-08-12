package com.cryptobot.marketdata.notbank;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only connector for NotBank's public market data API — plataforma tipo
 * AlphaPoint (patrón {@code POST /AP/{Function}}, sin auth para los endpoints
 * públicos). Símbolo sin separador, ej. "LTCUSDT" (a diferencia de Poloniex,
 * que usa "LTC_USDT").
 *
 * Host confirmado en vivo (no publicado en la documentación pública):
 * https://api.notbank.exchange — ver docs/entorno.md.
 *
 * GetL2Snapshot no devuelve objetos con nombre, sino filas de array
 * posicional. Formato confirmado a mano contra respuestas reales (y
 * cruzado contra lo que se veía en la UI de NotBank):
 * [MDUpdateId, Accounts, ActionDateTime, ActionType, LastTradePrice,
 *  Orders, Price, InstrumentId, Quantity, Side] — Side: 0 = bid, 1 = ask.
 */
public class NotBankConnector implements ExchangeConnector {

    private static final String BASE_URL = "https://api.notbank.exchange";
    private static final int OMS_ID = 1;
    private static final int DEFAULT_DEPTH = 10;

    private static final int COL_ACTION_DATETIME = 2;
    private static final int COL_PRICE = 6;
    private static final int COL_QUANTITY = 8;
    private static final int COL_SIDE = 9;
    private static final int SIDE_BID = 0;
    private static final int SIDE_ASK = 1;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private Map<String, Integer> instrumentIdsBySymbol;

    public NotBankConnector() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String exchangeName() {
        return "NotBank";
    }

    @Override
    public OrderBook fetchOrderBook(String symbol) {
        int instrumentId = resolveInstrumentId(symbol);
        JsonNode rows = post("/AP/GetL2Snapshot", Map.of(
            "OMSId", OMS_ID,
            "InstrumentId", instrumentId,
            "Depth", DEFAULT_DEPTH
        ));
        return parseOrderBook(symbol, rows);
    }

    OrderBook parseOrderBook(String symbol, JsonNode rows) {
        if (rows == null || !rows.isArray()) {
            throw new ExchangeApiException("Respuesta de NotBank sin filas de L2 esperadas para " + symbol);
        }

        List<PriceLevel> bids = new ArrayList<>();
        List<PriceLevel> asks = new ArrayList<>();
        Instant timestamp = Instant.EPOCH;

        for (JsonNode row : rows) {
            BigDecimal price = new BigDecimal(row.get(COL_PRICE).asText());
            BigDecimal quantity = new BigDecimal(row.get(COL_QUANTITY).asText());
            int side = row.get(COL_SIDE).asInt();
            timestamp = Instant.ofEpochMilli(row.get(COL_ACTION_DATETIME).asLong());

            PriceLevel level = new PriceLevel(price, quantity);
            if (side == SIDE_BID) {
                bids.add(level);
            } else if (side == SIDE_ASK) {
                asks.add(level);
            } else {
                throw new ExchangeApiException("Side desconocido en fila de NotBank: " + side);
            }
        }

        // NotBank no garantiza el orden — lo ordenamos nosotros: mejor bid
        // (más alto) primero, mejor ask (más bajo) primero.
        bids.sort((a, b) -> b.price().compareTo(a.price()));
        asks.sort((a, b) -> a.price().compareTo(b.price()));

        return new OrderBook("NotBank", symbol, timestamp, bids, asks);
    }

    /**
     * Lista todos los mercados activos (no deshabilitados) — hace falta para
     * descubrir triángulos cross-exchange en vez de tenerlos hardcodeados
     * (Sprint 0012), mismo propósito que {@code PoloniexConnector.fetchMarkets()}.
     * Llama a {@code /AP/GetInstruments} de nuevo (no comparte caché con
     * {@link #resolveInstrumentId}) — se usa una sola vez por corrida, no vale
     * la pena complicar el cacheo por eso.
     */
    public List<Market> fetchMarkets() {
        JsonNode instruments = post("/AP/GetInstruments", Map.of("OMSId", OMS_ID));
        return parseMarkets(instruments);
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    List<Market> parseMarkets(JsonNode instruments) {
        if (instruments == null || !instruments.isArray()) {
            throw new ExchangeApiException("Respuesta de NotBank sin lista de instrumentos esperada");
        }

        List<Market> markets = new ArrayList<>();
        for (JsonNode instrument : instruments) {
            if (instrument.path("IsDisable").asBoolean(false)) {
                continue;
            }
            markets.add(new Market(
                instrument.get("Product1Symbol").asText(),
                instrument.get("Product2Symbol").asText(),
                instrument.get("Symbol").asText()));
        }
        return markets;
    }

    private synchronized int resolveInstrumentId(String symbol) {
        if (instrumentIdsBySymbol == null) {
            instrumentIdsBySymbol = fetchInstrumentIds();
        }
        Integer id = instrumentIdsBySymbol.get(symbol);
        if (id == null) {
            throw new ExchangeApiException("Símbolo \"" + symbol + "\" no encontrado en NotBank");
        }
        return id;
    }

    private Map<String, Integer> fetchInstrumentIds() {
        JsonNode instruments = post("/AP/GetInstruments", Map.of("OMSId", OMS_ID));
        if (instruments == null || !instruments.isArray()) {
            throw new ExchangeApiException("Respuesta de NotBank sin lista de instrumentos esperada");
        }
        Map<String, Integer> map = new HashMap<>();
        for (JsonNode instrument : instruments) {
            map.put(instrument.get("Symbol").asText(), instrument.get("InstrumentId").asInt());
        }
        return map;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo serializar el pedido a NotBank para " + path, e);
        }

        var request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson))
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a NotBank (" + path + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a NotBank interrumpida (" + path + ")", e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "NotBank respondió " + response.statusCode() + " en " + path + ": " + response.body());
        }

        try {
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new ExchangeApiException("Respuesta de NotBank no es JSON válido en " + path, e);
        }
    }
}
