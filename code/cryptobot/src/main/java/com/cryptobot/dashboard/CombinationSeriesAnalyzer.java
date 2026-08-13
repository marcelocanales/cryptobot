package com.cryptobot.dashboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrupa las filas de un CSV de watcher por combinación (según las
 * columnas de identidad de {@link WatcherFormats.WatcherFormat}) y traza
 * su métrica principal en el tiempo cada vez que aparece marcada
 * {@code REVISAR}. {@code WatchHealthAnalyzer} (Sprint 0019) ya cubre
 * timestamp/stale/flag/error de forma genérica; esto cubre lo que varía
 * por hipótesis: identidad de la combinación y métrica principal.
 *
 * {@code consistencyPct} (veces marcada / veces que apareció en total) es
 * la misma pregunta que se respondió a mano la noche del Sprint 0026 para
 * distinguir señal de ruido: ZEC cerca del 100% (persistente, señal real)
 * vs. MANA ~10% (el book fino de Poloniex hace que {@code REVISAR}
 * aparezca solo por casualidad de qué nivel de precio cruzó el umbral de
 * nocional ese ciclo, no una señal real).
 */
public final class CombinationSeriesAnalyzer {

    // Medido en vivo contra la corrida nocturna del Sprint 0026:
    // spread-watch, el archivo con más volumen, solo tuvo 33 combinaciones
    // marcadas REVISAR alguna vez en ~200.000 filas — un TOP_N=20 alcanzaba
    // a tapar justo los casos de baja consistencia (como MANA, el ejemplo
    // de ruido de esa misma noche) detrás de combinaciones siempre-100%.
    // 50 deja margen real sin perder la protección de "sin cap silencioso"
    // (omittedCount sigue reportado si algún día se supera).
    static final int TOP_N = 50;

    // Mismo umbral que CrossTriangleCheck/CrossTriangleWatcher usan desde
    // el Sprint 0012 para marcar IMPLAUSIBLE — acá como aviso informativo,
    // no descarta datos. Relevante en particular para spread-watch, que
    // todavía no tiene ese guardia (backlog) y ya mostró un caso real
    // (TAO, 118.620%) la noche del Sprint 0026.
    private static final BigDecimal IMPLAUSIBLE_THRESHOLD = new BigDecimal("50");

    private static final String REVISAR = "REVISAR";

    public record Point(Instant timestamp, BigDecimal value) {
    }

    public record Combination(
        String label,
        long appearances,
        long flaggedCount,
        double consistencyPct,
        boolean possibleTickerCollision,
        List<Point> series
    ) {
    }

    public record Result(List<Combination> combinations, long omittedCount) {
    }

    private CombinationSeriesAnalyzer() {
    }

    public static Result analyze(BufferedReader reader, WatcherFormats.WatcherFormat format) throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new IllegalArgumentException("Archivo vacío, sin header");
        }
        List<String> header = List.of(headerLine.split(",", -1));
        Map<String, Integer> columnIndex = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columnIndex.put(header.get(i), i);
        }
        List<String> required = new ArrayList<>(format.identityColumns());
        required.add(format.metricColumn());
        required.add("timestamp");
        required.add("flag");
        for (String column : required) {
            if (!columnIndex.containsKey(column)) {
                throw new IllegalArgumentException("Falta la columna \"" + column + "\" en el header: " + headerLine);
            }
        }
        int[] identityIdx = format.identityColumns().stream().mapToInt(columnIndex::get).toArray();
        int metricIdx = columnIndex.get(format.metricColumn());
        int timestampIdx = columnIndex.get("timestamp");
        int flagIdx = columnIndex.get("flag");
        int columnCount = header.size();

        Map<String, Long> appearances = new LinkedHashMap<>();
        Map<String, Long> flaggedCount = new HashMap<>();
        Map<String, List<Point>> series = new HashMap<>();

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split(",", columnCount);
            if (fields.length < columnCount) {
                continue;
            }

            String label = identityLabel(fields, identityIdx);
            appearances.merge(label, 1L, Long::sum);

            if (!REVISAR.equals(fields[flagIdx])) {
                continue;
            }
            String rawMetric = fields[metricIdx];
            if (rawMetric.isBlank()) {
                continue;
            }
            BigDecimal value;
            try {
                value = new BigDecimal(rawMetric);
            } catch (NumberFormatException e) {
                continue;
            }
            Instant timestamp = Instant.parse(fields[timestampIdx]);

            flaggedCount.merge(label, 1L, Long::sum);
            series.computeIfAbsent(label, k -> new ArrayList<>()).add(new Point(timestamp, value));
        }

        List<Combination> combinations = new ArrayList<>();
        for (Map.Entry<String, Long> entry : appearances.entrySet()) {
            String label = entry.getKey();
            long total = entry.getValue();
            long flagged = flaggedCount.getOrDefault(label, 0L);
            if (flagged == 0) {
                continue;
            }
            List<Point> points = series.getOrDefault(label, List.of());
            boolean implausible = points.stream()
                .anyMatch(p -> p.value().abs().compareTo(IMPLAUSIBLE_THRESHOLD) > 0);
            double consistencyPct = 100.0 * flagged / total;
            combinations.add(new Combination(label, total, flagged, consistencyPct, implausible, points));
        }

        combinations.sort(Comparator.comparingLong(Combination::flaggedCount).reversed());

        long omittedCount = Math.max(0, combinations.size() - TOP_N);
        List<Combination> top = combinations.size() > TOP_N ? combinations.subList(0, TOP_N) : combinations;

        return new Result(top, omittedCount);
    }

    private static String identityLabel(String[] fields, int[] identityIdx) {
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < identityIdx.length; i++) {
            if (i > 0) {
                label.append(" / ");
            }
            label.append(fields[identityIdx[i]]);
        }
        return label.toString();
    }
}
