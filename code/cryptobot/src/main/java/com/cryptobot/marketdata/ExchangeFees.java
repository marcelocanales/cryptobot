package com.cryptobot.marketdata;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Fee de taker real por exchange, como fracción (0.0020 = 0,20%) — no como
 * porcentaje. El modelo de ejecución asumido en todo el proyecto es
 * taker-taker (dos órdenes de mercado, una por pata — ver docs/roadmap.md,
 * backlog "Ejecución maker + taker"), así que solo la fee de taker importa
 * para el spread neto hoy. Cada valor está documentado, con su fuente, en
 * docs/entorno.md — no cambiar acá sin actualizar ahí también.
 */
public final class ExchangeFees {

    // Poloniex, Buda y YoBit cobran la misma fee sin importar el par.
    private static final Map<String, BigDecimal> FLAT_TAKER_FEE = Map.of(
        "Poloniex", new BigDecimal("0.0020"),
        "Buda", new BigDecimal("0.0080"),
        "YoBit", new BigDecimal("0.0020")
    );

    // NotBank: fee real (no estimada), confirmada en vivo contra su propia
    // API pública de tarifas — GET https://api.notbank.exchange/api/nb/instruments/fees
    // (Sprint 0008). No es plana: depende de si la moneda de cotización es
    // una fiat/stablecoin o una cripto real, y del volumen de 30 días. Se
    // usa el tier base (0-10.000 USD de volumen) — el esperable para una
    // cuenta de exploración, sin trading real todavía.
    private static final BigDecimal NOTBANK_CRYPTO_FIAT_TAKER = new BigDecimal("0.0049");
    private static final BigDecimal NOTBANK_CRYPTO_CRYPTO_TAKER = new BigDecimal("0.0014");
    private static final Set<String> FIAT_LIKE_QUOTES = Set.of("USDT", "USDC", "CLP", "COP", "PEN");

    private ExchangeFees() {
    }

    /**
     * @param quoteCurrency moneda de cotización del par (ej. "USDT", "CLP", "BTC") —
     *                      solo importa para NotBank, el resto cobra igual sin importar el par.
     */
    public static BigDecimal takerFee(String exchangeName, String quoteCurrency) {
        if ("NotBank".equals(exchangeName)) {
            return FIAT_LIKE_QUOTES.contains(quoteCurrency) ? NOTBANK_CRYPTO_FIAT_TAKER : NOTBANK_CRYPTO_CRYPTO_TAKER;
        }
        BigDecimal fee = FLAT_TAKER_FEE.get(exchangeName);
        if (fee == null) {
            throw new IllegalArgumentException("Sin fee de taker conocida para " + exchangeName);
        }
        return fee;
    }
}
