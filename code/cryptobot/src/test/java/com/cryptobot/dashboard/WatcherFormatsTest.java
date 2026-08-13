package com.cryptobot.dashboard;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de contrato: fija los headers reales de los 6 CSV (confirmados con
 * {@code head -1} sobre los archivos de la corrida nocturna del Sprint
 * 0026, no de memoria) y confirma que las columnas declaradas en
 * {@link WatcherFormats} existen ahí — si un watcher cambia su CSV algún
 * día, este test avisa en vez de romperse calladamente en producción.
 */
class WatcherFormatsTest {

    private static final Map<String, String> REAL_HEADERS = Map.of(
        "spread-watch",
        "timestamp,asset,buy_exchange,sell_exchange,buy_price,sell_price,gross_pct,fees_pct,net_pct,stale,flag,error",
        "triangle-watch",
        "timestamp,triangle,direction,path,gross_pct,net_pct,stale,flag,error",
        "yobit-triangle-watch",
        "timestamp,triangle,direction,path,gross_pct,net_pct,stale,flag,error",
        "cross-triangle-watch",
        "timestamp,triangle,tag,direction,path,gross_pct,net_pct,stale,flag,error",
        "cash-and-carry-watch",
        "timestamp,asset,spot_exchange,spot_ask,perp_exchange,perp_bid,basis_pct,funding_rate_pct,"
            + "funding_interval_hours,annualized_funding_pct,entry_fees_pct,breakeven_periods,stale,flag,error",
        "funding-cross-exchange-watch",
        "timestamp,asset,short_exchange,short_annualized_pct,long_exchange,long_annualized_pct,"
            + "annualized_differential_pct,entry_fees_pct,breakeven_hours,stale,flag,error"
    );

    @Test
    void everyDeclaredColumnExistsInItsRealHeader() {
        for (WatcherFormats.WatcherFormat format : WatcherFormats.all()) {
            String headerLine = REAL_HEADERS.get(format.filePrefix());
            assertTrue(headerLine != null, "No hay header real registrado para " + format.filePrefix());
            Set<String> columns = Set.of(headerLine.split(","));
            for (String identityColumn : format.identityColumns()) {
                assertTrue(columns.contains(identityColumn),
                    format.filePrefix() + ": falta la columna de identidad \"" + identityColumn + "\"");
            }
            assertTrue(columns.contains(format.metricColumn()),
                format.filePrefix() + ": falta la columna de métrica \"" + format.metricColumn() + "\"");
        }
    }

    @Test
    void allSixWatchersAreRegisteredAndPrefixesDoNotCollide() {
        assertEquals(6, WatcherFormats.all().size());
        for (String prefix : REAL_HEADERS.keySet()) {
            String fileName = prefix + "-2026-08-13T042008Z.csv";
            Optional<WatcherFormats.WatcherFormat> matched = WatcherFormats.forFileName(fileName);
            assertTrue(matched.isPresent(), "No matcheó ningún formato para " + fileName);
            assertEquals(prefix, matched.get().filePrefix(), "Matcheó el prefijo equivocado para " + fileName);
        }
    }
}
