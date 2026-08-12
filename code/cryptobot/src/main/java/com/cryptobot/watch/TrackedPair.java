package com.cryptobot.watch;

/**
 * A pair to watch, with its symbol spelled the way each exchange expects it
 * (Poloniex: "LTC_USDT", NotBank: "LTCUSDT" — same asset, different format).
 */
public record TrackedPair(String label, String poloniexSymbol, String notbankSymbol) {
}
