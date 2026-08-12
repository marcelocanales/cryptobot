package com.cryptobot.marketdata;

import com.cryptobot.marketdata.buda.BudaConnector;
import com.cryptobot.marketdata.notbank.NotBankConnector;
import com.cryptobot.marketdata.poloniex.PoloniexConnector;
import com.cryptobot.marketdata.yobit.YobitConnector;

import java.math.BigDecimal;
import java.util.List;

import static com.cryptobot.marketdata.TrackedAsset.Venue;

/**
 * Registro único de qué activo cotiza en qué exchange, con qué símbolo —
 * usado tanto por {@code OverlapCheck} (foto única) como por
 * {@code SpreadWatcher} (corrida continua). Antes esta información vivía
 * duplicada entre los dos (8 pares en uno, 11 en el otro, sin ser el mismo
 * conjunto) — Sprint 0007.
 *
 * Universo de activos y sus exchanges, verificado en vivo:
 * - BTC/ETH/LTC/DOGE/SHIB cotizan en USDT en Poloniex, NotBank y YoBit.
 * - AAVE/GRAM/XTZ cotizan en USDT en Poloniex y NotBank — YoBit no los
 *   lista (confirmado contra {@code GET /api/3/info} en el Sprint 0007).
 * - BTC/ETH/LTC-BTC cotizan en Buda, y NotBank los tiene sin necesidad de
 *   convertir moneda (BTC-CLP, ETH-CLP, LTC-BTC — ver Sprint 0005).
 */
public final class TrackedAssets {

    // Umbral de valor nocional mínimo por moneda de cotización — un "mejor
    // precio" que no lo alcanza puede ser una orden vieja y chica aislada,
    // no liquidez real (XTZ en Poloniex, Sprint 0003/0004). ~USD 50
    // equivalente en cada moneda: USDT 1:1, CLP ~950/USD, BTC ~64000 USD/BTC.
    private static final BigDecimal MIN_NOTIONAL_USDT = new BigDecimal("50");
    private static final BigDecimal MIN_NOTIONAL_CLP = new BigDecimal("47500");
    private static final BigDecimal MIN_NOTIONAL_BTC = new BigDecimal("0.00078");

    private TrackedAssets() {
    }

    public static List<TrackedAsset> all(PoloniexConnector poloniex, NotBankConnector notbank,
                                          BudaConnector buda, YobitConnector yobit) {
        return List.of(
            new TrackedAsset("BTC/USDT", MIN_NOTIONAL_USDT, List.of(
                new Venue(poloniex, "BTC_USDT"),
                new Venue(notbank, "BTCUSDT"),
                new Venue(yobit, "btc_usdt")
            )),
            new TrackedAsset("ETH/USDT", MIN_NOTIONAL_USDT, List.of(
                new Venue(poloniex, "ETH_USDT"),
                new Venue(notbank, "ETHUSDT"),
                new Venue(yobit, "eth_usdt")
            )),
            new TrackedAsset("LTC/USDT", MIN_NOTIONAL_USDT, List.of(
                new Venue(poloniex, "LTC_USDT"),
                new Venue(notbank, "LTCUSDT"),
                new Venue(yobit, "ltc_usdt")
            )),
            new TrackedAsset("DOGE/USDT", MIN_NOTIONAL_USDT, List.of(
                new Venue(poloniex, "DOGE_USDT"),
                new Venue(notbank, "DOGEUSDT"),
                new Venue(yobit, "doge_usdt")
            )),
            new TrackedAsset("SHIB/USDT", MIN_NOTIONAL_USDT, List.of(
                new Venue(poloniex, "SHIB_USDT"),
                new Venue(notbank, "SHIBUSDT"),
                new Venue(yobit, "shib_usdt")
            )),
            new TrackedAsset("AAVE/USDT", MIN_NOTIONAL_USDT, List.of(
                new Venue(poloniex, "AAVE_USDT"),
                new Venue(notbank, "AAVEUSDT")
            )),
            new TrackedAsset("GRAM/USDT", MIN_NOTIONAL_USDT, List.of(
                new Venue(poloniex, "GRAM_USDT"),
                new Venue(notbank, "GRAMUSDT")
            )),
            new TrackedAsset("XTZ/USDT", MIN_NOTIONAL_USDT, List.of(
                new Venue(poloniex, "XTZ_USDT"),
                new Venue(notbank, "XTZUSDT")
            )),
            new TrackedAsset("BTC/CLP", MIN_NOTIONAL_CLP, List.of(
                new Venue(buda, "btc-clp"),
                new Venue(notbank, "BTCCLP")
            )),
            new TrackedAsset("ETH/CLP", MIN_NOTIONAL_CLP, List.of(
                new Venue(buda, "eth-clp"),
                new Venue(notbank, "ETHCLP")
            )),
            new TrackedAsset("LTC/BTC", MIN_NOTIONAL_BTC, List.of(
                new Venue(buda, "ltc-btc"),
                new Venue(notbank, "LTCBTC")
            ))
        );
    }
}
