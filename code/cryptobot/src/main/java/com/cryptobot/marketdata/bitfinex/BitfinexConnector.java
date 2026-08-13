package com.cryptobot.marketdata.bitfinex;

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
import java.util.Comparator;
import java.util.List;

/**
 * Read-only connector for Bitfinex's public spot market data API — no auth,
 * no key. Símbolo nativo con prefijo {@code t} (ej. "tBTCUSD", "tAAVE:USD")
 * — {@link Market#symbol()} ya guarda esa forma, lista para pasar directo a
 * {@link #fetchOrderBook(String)}.
 *
 * Verificado en vivo 2026-08 contra https://api-pub.bitfinex.com/v2/book y
 * https://api-pub.bitfinex.com/v2/conf/pub:list:pair:exchange — Sprint 0023
 * (6to exchange; fee cero permanente desde 2025-12-17, ver docs/entorno.md).
 *
 * Dos particularidades de formato, ambas confirmadas en vivo:
 * - El book es un **array plano combinado** de [precio, count, cantidad] —
 *   cantidad positiva es bid, negativa es ask (valor absoluto para el
 *   tamaño real). No viene separado en bids/asks como el resto de los
 *   conectores — hay que partirlo y ordenar cada lado a mano.
 * - {@code UST} es el ticker de Bitfinex para Tether/USDT (confirmado
 *   contra {@code GET /v2/conf/pub:map:currency:label} → "Tether USDt", no
 *   confundir con TerraUST) — se normaliza a "USDT" al armar cada
 *   {@link Market} para que cruce con los mercados USDT de los demás
 *   exchanges en {@code TrackedAssets} (agrupa por string exacto).
 */
public class BitfinexConnector implements ExchangeConnector {

    private static final String BASE_URL = "https://api-pub.bitfinex.com/v2";
    private static final int DEFAULT_DEPTH = 25;
    private static final String SYMBOL_PREFIX = "t";
    private static final String BITFINEX_USDT_TICKER = "UST";
    private static final String CANONICAL_USDT = "USDT";

    // Funding de perpetuos en grilla fija de 8h (0:00/8:00/16:00 UTC) —
    // confirmado tanto en la documentación de Bitfinex (liquidación 3
    // veces al día) como cruzando un NEXT_FUNDING_EVT_TIMESTAMP_MS real
    // contra esa grilla (Sprint 0024). La API no expone la hora de INICIO
    // del período actual, solo la del próximo — a diferencia de Poloniex
    // (que sí mide fT/nFT real de la API), acá `fundingTime` se DERIVA de
    // `nextFundingTime - 8h`, no se mide independientemente cada vez.
    private static final Duration FUNDING_INTERVAL = Duration.ofHours(8);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BitfinexConnector() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String exchangeName() {
        return "Bitfinex";
    }

    @Override
    public OrderBook fetchOrderBook(String symbol) {
        var uri = URI.create(BASE_URL + "/book/" + symbol + "/P0?len=" + DEFAULT_DEPTH);
        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a Bitfinex para " + symbol, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a Bitfinex interrumpida para " + symbol, e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "Bitfinex respondió " + response.statusCode() + " para " + symbol + ": " + response.body());
        }

        return parseOrderBook(symbol, response.body());
    }

    /**
     * Lista todos los pares — hace falta para descubrir activos compartidos
     * entre exchanges en vez de tenerlos hardcodeados, mismo patrón que los
     * otros 5 conectores. Filtra los pares de sandbox ({@code TEST*}).
     */
    public List<Market> fetchMarkets() {
        var uri = URI.create(BASE_URL + "/conf/pub:list:pair:exchange");
        var request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExchangeApiException("No se pudo conectar a Bitfinex para listar mercados", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a Bitfinex interrumpida al listar mercados", e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "Bitfinex respondió " + response.statusCode() + " al listar mercados: " + response.body());
        }

        return parseMarkets(response.body());
    }

    /**
     * Lista los símbolos de perpetuos activos (Sprint 0024) — descubiertos
     * contra {@code GET /conf/pub:list:pair:futures}, no hardcodeados.
     * Filtra los pares de sandbox ({@code TEST*}). Devuelve el símbolo con
     * el prefijo {@code t} ya puesto, listo para pasar directo a
     * {@link #fetchPerpQuote(String)}.
     */
    public List<String> fetchPerpSymbols() {
        String body = fetchBody(BASE_URL + "/conf/pub:list:pair:futures", "al listar perpetuos");
        return parsePerpSymbols(body);
    }

    /**
     * Combina precio (mark, mejor bid/ask) y funding rate actual de un
     * perpetuo — dos llamadas reales, {@code /ticker} y
     * {@code /status/deriv}, ninguna documentada como "la misma cosa",
     * hay que pedirlas por separado (mismo criterio que
     * {@code PoloniexConnector.fetchPerpQuote}).
     */
    public PerpQuote fetchPerpQuote(String symbol) {
        String tickerBody = fetchBody(BASE_URL + "/ticker/" + symbol, "ticker de " + symbol);
        String derivBody = fetchBody(BASE_URL + "/status/deriv?keys=" + symbol, "status/deriv de " + symbol);
        return parsePerpQuote(symbol, tickerBody, derivBody);
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
            throw new ExchangeApiException("No se pudo conectar a Bitfinex (" + context + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Consulta a Bitfinex interrumpida (" + context + ")", e);
        }

        if (response.statusCode() != 200) {
            throw new ExchangeApiException(
                "Bitfinex respondió " + response.statusCode() + " (" + context + "): " + response.body());
        }
        return response.body();
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    List<String> parsePerpSymbols(String json) {
        JsonNode root = readTree(json, "al listar perpetuos");
        checkError(root, json, "al listar perpetuos");

        JsonNode pairs = root.path(0);
        if (!pairs.isArray()) {
            throw new ExchangeApiException("Respuesta de Bitfinex sin lista de perpetuos esperada: " + json);
        }

        List<String> result = new ArrayList<>();
        for (JsonNode p : pairs) {
            String pair = p.asText();
            if (pair.contains("TEST")) {
                continue;
            }
            result.add(SYMBOL_PREFIX + pair);
        }
        return result;
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    PerpQuote parsePerpQuote(String symbol, String tickerJson, String derivJson) {
        JsonNode tickerRoot = readTree(tickerJson, "ticker de " + symbol);
        checkError(tickerRoot, tickerJson, "ticker de " + symbol);
        JsonNode derivRoot = readTree(derivJson, "status/deriv de " + symbol);
        checkError(derivRoot, derivJson, "status/deriv de " + symbol);

        JsonNode derivEntries = derivRoot;
        if (!derivEntries.isArray() || derivEntries.isEmpty()) {
            throw new ExchangeApiException("Respuesta de Bitfinex sin status/deriv para " + symbol + ": " + derivJson);
        }
        JsonNode deriv = derivEntries.get(0);

        PriceLevel bestBid = new PriceLevel(tickerRoot.get(0).decimalValue(), tickerRoot.get(1).decimalValue());
        PriceLevel bestAsk = new PriceLevel(tickerRoot.get(2).decimalValue(), tickerRoot.get(3).decimalValue());
        BigDecimal markPrice = deriv.get(15).decimalValue();
        BigDecimal fundingRatePct = deriv.get(12).decimalValue().multiply(BigDecimal.valueOf(100));
        Instant nextFundingTime = Instant.ofEpochMilli(deriv.get(8).asLong());
        Instant fundingTime = nextFundingTime.minus(FUNDING_INTERVAL);

        return new PerpQuote(symbol, markPrice, bestBid, bestAsk, fundingRatePct, fundingTime, nextFundingTime);
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    List<Market> parseMarkets(String json) {
        JsonNode root = readTree(json, "al listar mercados");
        checkError(root, json, "al listar mercados");

        JsonNode pairs = root.path(0);
        if (!pairs.isArray()) {
            throw new ExchangeApiException("Respuesta de Bitfinex sin lista de pares esperada: " + json);
        }

        List<Market> result = new ArrayList<>();
        for (JsonNode p : pairs) {
            String pair = p.asText();
            if (pair.contains("TEST")) {
                continue;
            }
            String base;
            String quote;
            if (pair.contains(":")) {
                String[] parts = pair.split(":", 2);
                base = parts[0];
                quote = parts[1];
            } else if (pair.length() == 6) {
                base = pair.substring(0, 3);
                quote = pair.substring(3);
            } else {
                continue; // no debería pasar (confirmado en vivo, Sprint 0023), defensivo igual
            }
            result.add(new Market(normalize(base), normalize(quote), SYMBOL_PREFIX + pair));
        }
        return result;
    }

    private static String normalize(String currency) {
        return BITFINEX_USDT_TICKER.equals(currency) ? CANONICAL_USDT : currency;
    }

    // package-private, no private: testeado directo con JSON real, sin mockear HTTP.
    OrderBook parseOrderBook(String symbol, String json) {
        JsonNode root = readTree(json, "para " + symbol);
        checkError(root, json, "para " + symbol);

        if (!root.isArray()) {
            throw new ExchangeApiException("Respuesta de Bitfinex sin book esperado para " + symbol + ": " + json);
        }

        List<PriceLevel> bids = new ArrayList<>();
        List<PriceLevel> asks = new ArrayList<>();
        for (JsonNode level : root) {
            BigDecimal price = level.get(0).decimalValue();
            BigDecimal amount = level.get(2).decimalValue();
            if (amount.signum() > 0) {
                bids.add(new PriceLevel(price, amount));
            } else if (amount.signum() < 0) {
                asks.add(new PriceLevel(price, amount.negate()));
            }
        }
        bids.sort(Comparator.comparing(PriceLevel::price).reversed());
        asks.sort(Comparator.comparing(PriceLevel::price));

        return new OrderBook("Bitfinex", symbol, Instant.now(), bids, asks);
    }

    private JsonNode readTree(String json, String context) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new ExchangeApiException("Respuesta de Bitfinex no es JSON válido " + context + ": " + json, e);
        }
    }

    /**
     * Bitfinex devuelve HTTP no-200 para la mayoría de los errores (a
     * diferencia de YoBit/CoinEx), pero además puede traer el cuerpo
     * {@code ["error", code, "mensaje"]} — se chequea por las dudas.
     */
    private void checkError(JsonNode root, String json, String context) {
        if (root.isArray() && root.size() >= 1 && "error".equals(root.path(0).asText())) {
            String message = root.path(2).asText("(sin detalle)");
            throw new ExchangeApiException("Bitfinex rechazó la consulta " + context + ": " + message);
        }
    }
}
