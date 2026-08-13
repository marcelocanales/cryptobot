package com.cryptobot.dashboard;

import java.util.List;
import java.util.Optional;

/**
 * Registro estático de los 6 formatos de CSV que escriben los watchers —
 * qué columnas identifican una combinación recurrente (activo+exchanges,
 * o triángulo+dirección) y cuál es la columna de la métrica principal a
 * trazar en el tiempo. {@code WatchHealthAnalyzer} (Sprint 0019) ya cubre
 * timestamp/stale/flag/error de forma genérica para los 6 sin necesitar
 * este registro; esto cubre lo que sí varía por hipótesis. Sprint 0026.
 */
public final class WatcherFormats {

    public record WatcherFormat(String filePrefix, List<String> identityColumns, String metricColumn) {
    }

    private static final List<WatcherFormat> ALL = List.of(
        new WatcherFormat("spread-watch",
            List.of("asset", "buy_exchange", "sell_exchange"), "net_pct"),
        new WatcherFormat("triangle-watch",
            List.of("triangle", "direction"), "net_pct"),
        new WatcherFormat("yobit-triangle-watch",
            List.of("triangle", "direction"), "net_pct"),
        new WatcherFormat("cross-triangle-watch",
            List.of("triangle", "tag", "direction"), "net_pct"),
        new WatcherFormat("cash-and-carry-watch",
            List.of("asset", "spot_exchange", "perp_exchange"), "annualized_funding_pct"),
        new WatcherFormat("funding-cross-exchange-watch",
            List.of("asset", "short_exchange", "long_exchange"), "annualized_differential_pct")
    );

    private WatcherFormats() {
    }

    public static List<WatcherFormat> all() {
        return ALL;
    }

    /**
     * Ninguno de los 6 prefijos es prefijo de otro (confirmado a mano
     * contra los nombres reales) — un match de {@code startsWith} alcanza,
     * sin ambigüedad entre formatos.
     */
    public static Optional<WatcherFormat> forFileName(String fileName) {
        return ALL.stream().filter(f -> fileName.startsWith(f.filePrefix())).findFirst();
    }
}
