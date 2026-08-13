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
    // CoinEx es distinto (Sprint 0021): la fee real varía POR MERCADO, no
    // por exchange ni por moneda de cotización (751 mercados al 0,30%, 242
    // al 0,20%, 6 al 0,10%, confirmado en vivo contra GET /v2/spot/market)
    // — acá se usa 0,20% como aproximación, confirmada en vivo para los
    // majors que de hecho se usan vía TrackedAssets (BTCUSDT/ETHUSDT/
    // LTCUSDT), no exacta para cualquier par exótico. Ver docs/entorno.md.
    // Bitfinex: fee CERO real, no una fee sin modelar — permanente desde el
    // 17/12/2025, spot y ~60 perpetuos, sin umbral de volumen (confirmado
    // en el blog oficial de Bitfinex y corroborado por medios independientes,
    // Sprint 0023). No es un placeholder ni un "todavía no sabemos".
    // Binance: 0,10% taker, tier VIP 0 sin descuento por pagar en BNB —
    // confirmado contra binance.com/en/fee/schedule (Sprint 0028). No se
    // asume que la cuenta de Marcelo tiene el descuento activado (bajaría a
    // 0,075%); si se confirma más adelante, se actualiza acá.
    private static final Map<String, BigDecimal> FLAT_TAKER_FEE = Map.of(
        "Poloniex", new BigDecimal("0.0020"),
        "Buda", new BigDecimal("0.0080"),
        "YoBit", new BigDecimal("0.0020"),
        "CoinEx", new BigDecimal("0.0020"),
        "Bitfinex", new BigDecimal("0.0000"),
        "Binance", new BigDecimal("0.0010")
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

    // Fee de taker de PERPETUOS — mercado distinto al spot, con su propia fee
    // incluso en el mismo exchange (Sprint 0015). Hoy solo Poloniex tiene
    // perpetuos entre los exchanges conectados (confirmado en vivo: NotBank
    // 0 instrumentos no-spot, Buda y YoBit solo spot). Confirmado el
    // 2026-08-13 contra la cuenta real de Marcelo (Trading Tier Status,
    // VIP 0, USDT-M Perpetual Futures) — 0,06%, no el 0,075% que se había
    // usado antes por no existir un endpoint público de fees de futuros
    // (se sacó de contenido de soporte/anuncios del exchange, sin poder
    // confirmarlo contra una fuente más dura hasta ahora).
    private static final Map<String, BigDecimal> PERP_TAKER_FEE = Map.of(
        "Poloniex", new BigDecimal("0.0006"),
        "Bitfinex", new BigDecimal("0.0000")
    );

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

    public static BigDecimal perpTakerFee(String exchangeName) {
        BigDecimal fee = PERP_TAKER_FEE.get(exchangeName);
        if (fee == null) {
            throw new IllegalArgumentException(exchangeName + " no tiene perpetuos conectados");
        }
        return fee;
    }
}
