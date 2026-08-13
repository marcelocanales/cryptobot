package com.cryptobot.dashboard;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Formato sintético genérico (no atado a ningún watcher real, mismo
 * criterio que {@code WatchHealthAnalyzerTest}): identidad
 * {@code asset,exchange}, métrica {@code value}.
 */
class CombinationSeriesAnalyzerTest {

    private static final WatcherFormats.WatcherFormat FORMAT =
        new WatcherFormats.WatcherFormat("synthetic-watch", List.of("asset", "exchange"), "value");

    private static CombinationSeriesAnalyzer.Result analyze(String csv) throws IOException {
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            return CombinationSeriesAnalyzer.analyze(reader, FORMAT);
        }
    }

    @Test
    void persistentCombinationHasHighConsistency() throws IOException {
        String csv = """
            timestamp,asset,exchange,value,stale,flag,error
            2026-08-13T00:00:00Z,ZEC,Poloniex,2.8,,REVISAR,
            2026-08-13T00:00:30Z,ZEC,Poloniex,2.9,,REVISAR,
            2026-08-13T00:01:00Z,ZEC,Poloniex,3.1,,REVISAR,
            2026-08-13T00:01:30Z,ZEC,Poloniex,3.0,,REVISAR,
            """;

        CombinationSeriesAnalyzer.Result r = analyze(csv);

        assertEquals(1, r.combinations().size());
        CombinationSeriesAnalyzer.Combination c = r.combinations().get(0);
        assertEquals("ZEC / Poloniex", c.label());
        assertEquals(4, c.appearances());
        assertEquals(4, c.flaggedCount());
        assertEquals(100.0, c.consistencyPct());
        assertEquals(4, c.series().size());
    }

    @Test
    void intermittentCombinationHasLowConsistency() throws IOException {
        String csv = """
            timestamp,asset,exchange,value,stale,flag,error
            2026-08-13T00:00:00Z,MANA,Poloniex,-7.5,,,
            2026-08-13T00:00:30Z,MANA,Poloniex,-7.5,,,
            2026-08-13T00:01:00Z,MANA,Poloniex,0.9,,REVISAR,
            2026-08-13T00:01:30Z,MANA,Poloniex,-7.5,,,
            2026-08-13T00:02:00Z,MANA,Poloniex,-7.5,,,
            2026-08-13T00:02:30Z,MANA,Poloniex,-7.5,,,
            2026-08-13T00:03:00Z,MANA,Poloniex,-7.5,,,
            2026-08-13T00:03:30Z,MANA,Poloniex,-7.5,,,
            2026-08-13T00:04:00Z,MANA,Poloniex,-7.5,,,
            2026-08-13T00:04:30Z,MANA,Poloniex,0.85,,REVISAR,
            """;

        CombinationSeriesAnalyzer.Result r = analyze(csv);

        assertEquals(1, r.combinations().size());
        CombinationSeriesAnalyzer.Combination c = r.combinations().get(0);
        assertEquals(10, c.appearances());
        assertEquals(2, c.flaggedCount());
        assertEquals(20.0, c.consistencyPct());
        assertEquals(2, c.series().size());
    }

    @Test
    void combinationsNeverFlaggedAreExcludedEntirely() throws IOException {
        String csv = """
            timestamp,asset,exchange,value,stale,flag,error
            2026-08-13T00:00:00Z,BTC,Poloniex,-0.1,,,
            2026-08-13T00:00:30Z,BTC,Poloniex,-0.2,,,
            """;

        CombinationSeriesAnalyzer.Result r = analyze(csv);

        assertTrue(r.combinations().isEmpty());
    }

    @Test
    void implausibleFlagDoesNotCountAsRevisarButCountsAsAppearance() throws IOException {
        String csv = """
            timestamp,asset,exchange,value,stale,flag,error
            2026-08-13T00:00:00Z,TAO,YoBit,118620,,IMPLAUSIBLE,
            2026-08-13T00:00:30Z,TAO,YoBit,-0.3,,,
            """;

        CombinationSeriesAnalyzer.Result r = analyze(csv);

        assertTrue(r.combinations().isEmpty());
    }

    @Test
    void sortsByFlaggedCountDescending() throws IOException {
        String csv = """
            timestamp,asset,exchange,value,stale,flag,error
            2026-08-13T00:00:00Z,A,X,1.0,,REVISAR,
            2026-08-13T00:00:00Z,B,X,1.0,,REVISAR,
            2026-08-13T00:00:30Z,B,X,1.1,,REVISAR,
            2026-08-13T00:01:00Z,B,X,1.2,,REVISAR,
            """;

        CombinationSeriesAnalyzer.Result r = analyze(csv);

        assertEquals(2, r.combinations().size());
        assertEquals("B / X", r.combinations().get(0).label());
        assertEquals(3, r.combinations().get(0).flaggedCount());
        assertEquals("A / X", r.combinations().get(1).label());
    }

    @Test
    void flagsPossibleTickerCollisionWhenMagnitudeExceedsThreshold() throws IOException {
        String csv = """
            timestamp,asset,exchange,value,stale,flag,error
            2026-08-13T00:00:00Z,TAO,YoBit,118620.0,,REVISAR,
            2026-08-13T00:00:00Z,ZEC,Poloniex,2.9,,REVISAR,
            """;

        CombinationSeriesAnalyzer.Result r = analyze(csv);

        var tao = r.combinations().stream().filter(c -> c.label().equals("TAO / YoBit")).findFirst().orElseThrow();
        var zec = r.combinations().stream().filter(c -> c.label().equals("ZEC / Poloniex")).findFirst().orElseThrow();
        assertTrue(tao.possibleTickerCollision());
        assertFalse(zec.possibleTickerCollision());
    }

    @Test
    void truncatesToTopNAndReportsOmittedCount() throws IOException {
        StringBuilder csv = new StringBuilder("timestamp,asset,exchange,value,stale,flag,error\n");
        for (int i = 0; i < CombinationSeriesAnalyzer.TOP_N + 5; i++) {
            csv.append("2026-08-13T00:00:00Z,A").append(i).append(",X,1.0,,REVISAR,\n");
        }

        CombinationSeriesAnalyzer.Result r = analyze(csv.toString());

        assertEquals(CombinationSeriesAnalyzer.TOP_N, r.combinations().size());
        assertEquals(5, r.omittedCount());
    }

    @Test
    void missingRequiredColumnThrows() {
        String csv = """
            timestamp,asset,exchange,stale,flag,error
            2026-08-13T00:00:00Z,ZEC,Poloniex,,REVISAR,
            """;

        assertThrows(IllegalArgumentException.class, () -> analyze(csv));
    }
}
