package com.cryptobot.report;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lógica pura de análisis de un CSV de watcher — sin tocar disco, sin
 * conocer el formato específico de ninguna hipótesis. Funciona sobre
 * cualquiera de los 5 formatos existentes (SpreadWatcher/TriangleWatcher/
 * YobitTriangleWatcher/CrossTriangleWatcher/CashAndCarryWatcher) porque
 * todos comparten las columnas timestamp/stale/flag/error por nombre,
 * aunque el resto de las columnas sea totalmente distinto por hipótesis —
 * ver docs/sprints/sprint_0019.md.
 */
public final class WatchHealthAnalyzer {

    // Heurística de partida, no confirmada — mismo tratamiento que
    // ParallelFetch.MAX_CONCURRENT_PER_EXCHANGE. Un gap entre ciclos
    // consecutivos que supera 3x la mediana MEDIDA de este mismo archivo
    // (no un valor fijo como "30s") se marca como hueco.
    static final int GAP_MULTIPLIER = 3;

    private static final List<String> REQUIRED_COLUMNS = List.of("timestamp", "stale", "flag", "error");

    public record CycleStats(Duration median, Duration min, Duration max) {
    }

    public record Gap(Instant fromCycle, Instant toCycle, Duration duration) {
    }

    public record Result(
        long rowCount,
        long malformedRowCount,
        long cycleCount,
        Optional<CycleStats> cycleStats,
        List<Gap> gaps,
        Map<String, Long> flagCounts,
        long errorRowCount,
        Map<String, Long> errorMessageCounts,
        Map<String, Long> staleTokenCounts
    ) {
    }

    private WatchHealthAnalyzer() {
    }

    public static Result analyze(BufferedReader reader) throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new IllegalArgumentException("Archivo vacío, sin header");
        }
        List<String> header = List.of(headerLine.split(",", -1));
        Map<String, Integer> columnIndex = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columnIndex.put(header.get(i), i);
        }
        for (String required : REQUIRED_COLUMNS) {
            if (!columnIndex.containsKey(required)) {
                throw new IllegalArgumentException("Falta la columna \"" + required + "\" en el header: " + headerLine);
            }
        }
        int tsIdx = columnIndex.get("timestamp");
        int staleIdx = columnIndex.get("stale");
        int flagIdx = columnIndex.get("flag");
        int errorIdx = columnIndex.get("error");
        int columnCount = header.size();

        long rowCount = 0;
        long malformedRowCount = 0;
        long cycleCount = 0;
        String lastTimestamp = null;
        Instant lastCycleInstant = null;
        List<Gap> allGaps = new ArrayList<>();
        Map<String, Long> flagCounts = new HashMap<>();
        long errorRowCount = 0;
        Map<String, Long> errorMessageCounts = new HashMap<>();
        Map<String, Long> staleTokenCounts = new HashMap<>();

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split(",", columnCount);
            if (fields.length < columnCount) {
                malformedRowCount++;
                continue;
            }
            rowCount++;

            String ts = fields[tsIdx];
            if (!ts.equals(lastTimestamp)) {
                Instant current = Instant.parse(ts);
                if (lastCycleInstant != null) {
                    allGaps.add(new Gap(lastCycleInstant, current, Duration.between(lastCycleInstant, current)));
                }
                lastCycleInstant = current;
                lastTimestamp = ts;
                cycleCount++;
            }

            String flag = fields[flagIdx];
            if (!flag.isBlank()) {
                flagCounts.merge(flag, 1L, Long::sum);
            }

            String stale = fields[staleIdx];
            if (!stale.isBlank()) {
                for (String token : stale.split("\\|")) {
                    if (!token.isBlank()) {
                        staleTokenCounts.merge(token, 1L, Long::sum);
                    }
                }
            }

            String error = unquote(fields[errorIdx]);
            if (!error.isBlank()) {
                errorRowCount++;
                errorMessageCounts.merge(stripKeyPrefix(error), 1L, Long::sum);
            }
        }

        Optional<CycleStats> cycleStats = Optional.empty();
        List<Gap> flaggedGaps = List.of();
        if (!allGaps.isEmpty()) {
            List<Duration> durations = allGaps.stream().map(Gap::duration).sorted().toList();
            Duration median = durations.get(durations.size() / 2);
            Duration min = durations.get(0);
            Duration max = durations.get(durations.size() - 1);
            cycleStats = Optional.of(new CycleStats(median, min, max));

            Duration threshold = median.multipliedBy(GAP_MULTIPLIER);
            flaggedGaps = allGaps.stream().filter(g -> g.duration().compareTo(threshold) > 0).toList();
        }

        return new Result(rowCount, malformedRowCount, cycleCount, cycleStats, flaggedGaps,
            flagCounts, errorRowCount, errorMessageCounts, staleTokenCounts);
    }

    /**
     * La columna {@code error} es la única que puede venir citada (comillas
     * dobles si el mensaje trae coma o comilla — mismo formato que
     * {@code escapeCsv} ya escribe en los 5 watchers).
     */
    private static String unquote(String field) {
        if (field.length() >= 2 && field.startsWith("\"") && field.endsWith("\"")) {
            return field.substring(1, field.length() - 1).replace("\"\"", "\"");
        }
        return field;
    }

    /**
     * Cada watcher escribe el error como "&lt;símbolo-o-clave&gt;: &lt;mensaje&gt;"
     * — se agrupa por el mensaje sin la clave, así el mismo patrón repetido
     * en muchos símbolos cuenta como uno solo, no uno por símbolo.
     */
    private static String stripKeyPrefix(String error) {
        int idx = error.indexOf(": ");
        return idx >= 0 ? error.substring(idx + 2) : error;
    }
}
