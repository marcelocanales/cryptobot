package com.cryptobot.report;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CLI — imprime el reporte de {@link WatchHealthAnalyzer} de uno o más CSV
 * de watcher. Un archivo que falla imprime su error y sigue con el
 * siguiente, no aborta toda la corrida. Sprint 0019.
 *
 * Uso: mvn exec:java -Dexec.mainClass=com.cryptobot.report.WatchHealthReport
 *      -Dexec.args="data/archivo1.csv data/archivo2.csv"
 */
public class WatchHealthReport {

    private static final int TOP_N = 15;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Uso: mvn exec:java -Dexec.mainClass=com.cryptobot.report.WatchHealthReport "
                + "-Dexec.args=\"data/archivo1.csv [data/archivo2.csv ...]\"");
            return;
        }

        for (String path : args) {
            System.out.println("=== " + path + " ===");
            try (BufferedReader reader = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
                print(WatchHealthAnalyzer.analyze(reader));
            } catch (IOException | IllegalArgumentException e) {
                System.out.println("  ERROR: " + e.getMessage());
            }
            System.out.println();
        }
    }

    private static void print(WatchHealthAnalyzer.Result r) {
        System.out.println("Filas: " + r.rowCount()
            + (r.malformedRowCount() > 0 ? " (" + r.malformedRowCount() + " filas mal formadas, descartadas)" : ""));

        System.out.print("Ciclos: " + r.cycleCount());
        if (r.cycleStats().isPresent()) {
            WatchHealthAnalyzer.CycleStats stats = r.cycleStats().get();
            System.out.println(" (intervalo medido: mediana " + formatDuration(stats.median())
                + ", min " + formatDuration(stats.min()) + ", max " + formatDuration(stats.max()) + ")");
        } else {
            System.out.println();
        }

        System.out.println("Huecos de tiempo (> " + WatchHealthAnalyzer.GAP_MULTIPLIER + "x la mediana medida): "
            + r.gaps().size());
        for (WatchHealthAnalyzer.Gap gap : r.gaps()) {
            System.out.println("  " + gap.fromCycle() + " -> " + gap.toCycle()
                + " (" + formatDuration(gap.duration()) + ")");
        }

        System.out.println("Flags:");
        if (r.flagCounts().isEmpty()) {
            System.out.println("  (ninguno)");
        } else {
            printSortedDesc(r.flagCounts(), Integer.MAX_VALUE);
        }

        double errorPct = r.rowCount() == 0 ? 0.0 : 100.0 * r.errorRowCount() / r.rowCount();
        System.out.printf("Errores: %d de %d filas (%.1f%%)%n", r.errorRowCount(), r.rowCount(), errorPct);
        if (!r.errorMessageCounts().isEmpty()) {
            System.out.println("  Top mensajes:");
            printSortedDesc(r.errorMessageCounts(), TOP_N);
        }

        if (!r.staleTokenCounts().isEmpty()) {
            System.out.println("Stale (top símbolos/lados más congelados):");
            printSortedDesc(r.staleTokenCounts(), TOP_N);
        }
    }

    private static void printSortedDesc(Map<String, Long> counts, int limit) {
        List<Map.Entry<String, Long>> sorted = counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .collect(Collectors.toList());

        int shown = Math.min(limit, sorted.size());
        for (int i = 0; i < shown; i++) {
            Map.Entry<String, Long> e = sorted.get(i);
            System.out.println("  " + e.getValue() + "x  " + e.getKey());
        }
        if (sorted.size() > shown) {
            System.out.println("  ... y " + (sorted.size() - shown) + " más");
        }
    }

    private static String formatDuration(Duration d) {
        long totalSeconds = d.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + "h" + minutes + "m" + seconds + "s";
        }
        if (minutes > 0) {
            return minutes + "m" + seconds + "s";
        }
        return seconds + "s";
    }
}
