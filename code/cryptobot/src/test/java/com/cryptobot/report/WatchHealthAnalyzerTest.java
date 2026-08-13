package com.cryptobot.report;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchHealthAnalyzerTest {

    private static WatchHealthAnalyzer.Result analyze(String csv) throws IOException {
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            return WatchHealthAnalyzer.analyze(reader);
        }
    }

    @Test
    void countsRowsAndCycles() throws IOException {
        String csv = """
            timestamp,label,stale,flag,error
            2026-08-13T00:00:00Z,A,,,
            2026-08-13T00:00:00Z,B,,,
            2026-08-13T00:00:30Z,A,,,
            2026-08-13T00:00:30Z,B,,,
            """;

        WatchHealthAnalyzer.Result r = analyze(csv);

        assertEquals(4, r.rowCount());
        assertEquals(2, r.cycleCount());
        assertEquals(0, r.malformedRowCount());
    }

    @Test
    void flagsUnusualGapAgainstTheMeasuredMedian() throws IOException {
        // 3 ciclos cada 30s, salvo el último salto de 300s (10x) que debe marcarse.
        String csv = """
            timestamp,label,stale,flag,error
            2026-08-13T00:00:00Z,A,,,
            2026-08-13T00:00:30Z,A,,,
            2026-08-13T00:01:00Z,A,,,
            2026-08-13T00:06:00Z,A,,,
            """;

        WatchHealthAnalyzer.Result r = analyze(csv);

        assertEquals(4, r.cycleCount());
        assertTrue(r.cycleStats().isPresent());
        assertEquals(Duration.ofSeconds(30), r.cycleStats().get().median());
        assertEquals(1, r.gaps().size());
        assertEquals(Duration.ofSeconds(300), r.gaps().get(0).duration());
    }

    @Test
    void countsNonBlankFlagsOnly() throws IOException {
        String csv = """
            timestamp,label,stale,flag,error
            2026-08-13T00:00:00Z,A,,REVISAR,
            2026-08-13T00:00:00Z,B,,IMPLAUSIBLE,
            2026-08-13T00:00:30Z,A,,REVISAR,
            2026-08-13T00:00:30Z,B,,,
            """;

        WatchHealthAnalyzer.Result r = analyze(csv);

        assertEquals(2L, r.flagCounts().get("REVISAR"));
        assertEquals(1L, r.flagCounts().get("IMPLAUSIBLE"));
        assertEquals(2, r.flagCounts().size());
    }

    @Test
    void groupsErrorMessagesByStrippingTheKeyPrefix() throws IOException {
        String csv = """
            timestamp,label,stale,flag,error
            2026-08-13T00:00:00Z,A,,,comp_btc: sin bids/asks esperados
            2026-08-13T00:00:00Z,B,,,shib_btc: sin bids/asks esperados
            2026-08-13T00:00:30Z,A,,,mensaje sin dos puntos
            """;

        WatchHealthAnalyzer.Result r = analyze(csv);

        assertEquals(3, r.errorRowCount());
        assertEquals(2L, r.errorMessageCounts().get("sin bids/asks esperados"));
        assertEquals(1L, r.errorMessageCounts().get("mensaje sin dos puntos"));
    }

    @Test
    void countsStaleTokensAcrossPipeSeparatedLists() throws IOException {
        String csv = """
            timestamp,label,stale,flag,error
            2026-08-13T00:00:00Z,A,Poloniex:BTC_USDT:bid,,
            2026-08-13T00:00:00Z,B,Poloniex:BTC_USDT:bid|NotBank:ETH_USDT:ask,,
            2026-08-13T00:00:30Z,A,,,
            """;

        WatchHealthAnalyzer.Result r = analyze(csv);

        assertEquals(2L, r.staleTokenCounts().get("Poloniex:BTC_USDT:bid"));
        assertEquals(1L, r.staleTokenCounts().get("NotBank:ETH_USDT:ask"));
    }

    @Test
    void unquotesAnEscapedErrorFieldWithAnEmbeddedComma() throws IOException {
        // Mismo formato que escapeCsv ya escribe: comillas dobles alrededor,
        // comillas internas duplicadas — acá el mensaje trae comas y comillas.
        String csv = """
            timestamp,label,stale,flag,error
            2026-08-13T00:00:00Z,A,,,"Buda respondió 404: {""message"":""Not found"",""code"":""not_found""}"
            """;

        WatchHealthAnalyzer.Result r = analyze(csv);

        assertEquals(1, r.errorRowCount());
        assertTrue(r.errorMessageCounts().keySet().stream()
            .anyMatch(m -> m.contains("\"message\":\"Not found\"") && !m.contains("\"\"")));
    }

    @Test
    void missingRequiredColumnThrows() {
        String csv = """
            timestamp,label,flag,error
            2026-08-13T00:00:00Z,A,,
            """;

        assertThrows(IllegalArgumentException.class, () -> analyze(csv));
    }
}
