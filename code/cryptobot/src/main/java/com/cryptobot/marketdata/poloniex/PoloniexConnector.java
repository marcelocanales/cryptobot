package com.cryptobot.marketdata.poloniex;

import com.cryptobot.marketdata.ExchangeApiException;
import com.cryptobot.marketdata.ExchangeConnector;
import com.cryptobot.marketdata.Market;
import com.cryptobot.marketdata.OrderBook;
import com.cryptobot.marketdata.PerpQuote;
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
    private static final String FUTURES_BASE_URL = "https://api.poloniex.com/v3";
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

    /**
     * Lista los símbolos de perpetuos activos (Sprint 0015) — descubiertos
     * contra {@code GET /v3/market/tickers}, no hardcodeados. Todos los
     * perpetuos de Poloniex son {@code {BASE}_USDT_PERP}.
     */
    public List<String> fetchPerpSymbols() {
        String body = fetchBody(FUTURES_BASE_URL + "/market/tickers", "listar perpetuos");
        return parsePerpSymbols(body);
    }

    /**
     * Combina precio (mark, mejor bid/ask) y funding rate actual de un
     * perpetuo — dos llamadas reales, {@code /v3/market/tickers} y
     * {@code /v3/market/fundingRate}, ninguna documentada como "la misma
     * cosa" en la API, hay que pedirlas por separado.
     */
    public PerpQuote fetchPerpQuote(String symbol) {
        String tickerBody = fetchBody(FUTURES_BASE_URL + "/market/tickers?symbol=" + symbol, "ticker de " + symbol);
        String fundingBody = fetchBody(FUTURES_BASE_URL + "/market/fundingRate?symbol=" + symbol,
            "funding rate de " + symbol);
        return parsePerpQuote(symbol, tickerBody, fundingBody);
    }

    private String fetchBody(String url, String context) {
        var request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a Poloniex (" + context + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a Poloniex interrumpida (" + context + ")", e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "Poloniex respondió " + response.statusCode() + " (" + context + "): " + response.body());
        }
        return response.body();
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    List<String> parsePerpSymbols(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (IOException e) {
            throw new ExchangeApiException("Respuesta de Poloniex no es JSON válido para tickers de futuros: " + json, e);
        }
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            throw new ExchangeApiException("Respuesta de Poloniex sin data esperada para tickers de futuros: " + json);
        }
        List<String> symbols = new ArrayList<>();
        for (JsonNode ticker : data) {
            String symbol = ticker.get("s").asText();
            if (symbol.endsWith("_USDT_PERP")) {
                symbols.add(symbol);
            }
        }
        return symbols;
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    PerpQuote parsePerpQuote(String symbol, String tickerJson, String fundingJson) {
        JsonNode tickerRoot;
        JsonNode fundingRoot;
        try {
            tickerRoot = objectMapper.readTree(tickerJson);
            fundingRoot = objectMapper.readTree(fundingJson);
        } catch (IOException e) {
            throw new ExchangeApiException("Respuesta de Poloniex no es JSON válido para el perpetuo " + symbol, e);
        }

        JsonNode tickerData = tickerRoot.path("data");
        if (!tickerData.isArray() || tickerData.isEmpty()) {
            throw new ExchangeApiException(
                "Respuesta de Poloniex sin ticker para el perpetuo " + symbol + ": " + tickerJson);
        }
        JsonNode ticker = tickerData.get(0);
        JsonNode funding = fundingRoot.path("data");

        BigDecimal markPrice = new BigDecimal(ticker.get("mPx").asText());
        PriceLevel bestBid = new PriceLevel(
            new BigDecimal(ticker.get("bPx").asText()), new BigDecimal(ticker.get("bSz").asText()));
        PriceLevel bestAsk = new PriceLevel(
            new BigDecimal(ticker.get("aPx").asText()), new BigDecimal(ticker.get("aSz").asText()));
        BigDecimal fundingRatePct = new BigDecimal(funding.get("fR").asText()).multiply(BigDecimal.valueOf(100));
        Instant fundingTime = Instant.ofEpochMilli(funding.get("fT").asLong());
        Instant nextFundingTime = Instant.ofEpochMilli(funding.get("nFT").asLong());

        return new PerpQuote(symbol, markPrice, bestBid, bestAsk, fundingRatePct, fundingTime, nextFundingTime);
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
