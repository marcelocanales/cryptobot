package com.cryptobot.marketdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExchangeFeesTest {

    @Test
    void flatFeesIgnoreTheQuoteCurrency() {
        assertEquals(new BigDecimal("0.0020"), ExchangeFees.takerFee("Poloniex", "USDT"));
        assertEquals(new BigDecimal("0.0020"), ExchangeFees.takerFee("Poloniex", "BTC"));
        assertEquals(new BigDecimal("0.0080"), ExchangeFees.takerFee("Buda", "CLP"));
        assertEquals(new BigDecimal("0.0020"), ExchangeFees.takerFee("YoBit", "USDT"));
        assertEquals(new BigDecimal("0.0020"), ExchangeFees.takerFee("CoinEx", "USDT"));
    }

    @Test
    void notBankFeeDependsOnWhetherTheQuoteIsFiatLikeOrCrypto() {
        // Tier base, confirmado en vivo contra la API real de NotBank (Sprint 0008).
        assertEquals(new BigDecimal("0.0049"), ExchangeFees.takerFee("NotBank", "USDT"));
        assertEquals(new BigDecimal("0.0049"), ExchangeFees.takerFee("NotBank", "CLP"));
        assertEquals(new BigDecimal("0.0014"), ExchangeFees.takerFee("NotBank", "BTC"));
    }

    @Test
    void unknownExchangeIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> ExchangeFees.takerFee("Binance", "USDT"));
    }

    @Test
    void perpFeeOnlyKnownForPoloniex() {
        // Confirmado contra la cuenta real de Marcelo (VIP 0, USDT-M Perpetual
        // Futures), Sprint 0022 — no un supuesto de contenido de soporte.
        assertEquals(new BigDecimal("0.0006"), ExchangeFees.perpTakerFee("Poloniex"));
        assertThrows(IllegalArgumentException.class, () -> ExchangeFees.perpTakerFee("NotBank"));
    }
}
