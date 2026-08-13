---
sprint: 25
titulo: "FundingCrossExchangeWatcher — monitoreo continuo de la hipótesis 05"
etapa: 2
---

# Sprint 0025 — FundingCrossExchangeWatcher — monitoreo continuo de la hipótesis 05

## Objetivo
`FundingCrossExchangeCheck` (Sprint 0024) es una sola foto — midió que la hipótesis 05 tiene diferencial positivo en 12 de 12 activos evaluables, pero la pregunta central del catálogo es **persistencia**, no existencia. Mismo salto que cash-and-carry tuvo del Sprint 0015 al 0016: pasar de una foto a una corrida continua.

## Alcance
- `FundingCrossExchangeWatcher` (en `com.cryptobot.watch`): loop de 30s, CSV largo, staleness acotado a precios (no funding).
- _(Fuera de alcance: correrlo un tramo largo todavía — es la próxima decisión operativa, junto con la corrida nocturna que ahora suma esta 6ta hipótesis a las 5 existentes.)_

## Decisiones
- **Mismo criterio de staleness que `CashAndCarryWatcher`**: se vigila el bid del exchange corto y el ask del exchange largo (las patas de precio que de hecho importan para la liquidez de cada lado), no el funding rate — que cambia por diseño cada 8h, marcarlo "congelado" dentro de esa ventana generaría un falso positivo constante, no una alerta útil.
- **`flag=REVISAR` en cualquier diferencial anualizado positivo** — no un umbral de horas de breakeven. Mismo criterio ya usado en `TriangleWatcher`/`CrossTriangleWatcher` (cualquier neto positivo se marca), evita inventar un umbral sin datos históricos que lo justifiquen.
- **CSV con los valores ya anualizados de `FundingCrossExchangeSpread.Result`** (`short_annualized_pct`/`long_annualized_pct`/`annualized_differential_pct`), sin desglosar la tasa cruda por período de cada pata — evita reabrir y re-testear la clase ya verificada en el Sprint 0024 por una granularidad de reporte que no es central para la pregunta de persistencia.

## Tareas
- [x] `FundingCrossExchangeWatcher`: descubrimiento único, loop de 30s, CSV largo, staleness acotado a precios
- [x] `mvn test` en verde
- [x] Verificación en vivo: 4 ciclos completos, 15 activos, CSV de 12 columnas consistentes en las 60 filas
- [x] `WatchHealthReport` (Sprint 0019) corrido sobre el CSV resultante, sin cambios de código

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.watch.FundingCrossExchangeWatcher` y confirmar el CSV en `data/funding-cross-exchange-watch-*.csv`.

**Debe cumplir:**
- [x] Los activos con perpetuo en 2+ exchanges se descubren una sola vez, no en cada ciclo
- [x] El funding rate no se marca `stale` aunque no cambie entre ciclos
- [x] Cada fila (con datos, sin liquidez, o error) tiene la misma cantidad de columnas (12, confirmado con `awk` sobre las 60 filas reales)

## Hallazgos
- **APT cambió de signo entre el Sprint 0024 y esta corrida** — diferencial negativo en la foto del 0024, +10,95% anualizado acá. No es un bug: es exactamente la clase de movimiento que este watcher existe para capturar, confirmado en la práctica, no solo en teoría.
- Los otros 11 activos con señal se mantuvieron estables en magnitud y signo a lo largo de los 4 ciclos (~2 minutos) — esperable dado que el funding no cambia dentro de su propia ventana de 8h.
- `WatchHealthReport` procesó el nuevo formato de CSV sin ningún cambio de código — 6to formato distinto que confirma el diseño "genérico por nombre de columna" del Sprint 0019.

## Cierre
Las 6 hipótesis que se pudieron construir con los exchanges conectados tienen ahora la misma capacidad de correr sin supervisión: `SpreadWatcher` (01), `TriangleWatcher`/`YobitTriangleWatcher` (02), `CrossTriangleWatcher` (03), `CashAndCarryWatcher` (04) y ahora `FundingCrossExchangeWatcher` (05). Ninguna tiene veredicto final todavía — la 05 es la más cerca de tenerlo, con la corrida nocturna pendiente.

Sigue pendiente: correr `FundingCrossExchangeWatcher` un tramo largo (confirmar si el hallazgo del Sprint 0024 persiste), Bitfinex en cash-and-carry (04), CoinEx como 3er candidato de funding cross-exchange, futuros/funding de CoinEx, CoinEx/Bitfinex en triangular, Buda en cash-and-carry con conversión de moneda, el rate limit real de cada exchange — y la corrida nocturna, ahora con 6 hipótesis candidatas en vez de 5.
