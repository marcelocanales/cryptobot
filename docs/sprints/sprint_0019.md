---
sprint: 19
titulo: "WatchHealthReport — reporte automático de salud sobre los CSV"
etapa: 2
---

# Sprint 0019 — WatchHealthReport: reporte automático de salud sobre los CSV

## Objetivo
Cada corrida larga de un watcher terminaba con el mismo análisis a mano: cuántos ciclos hubo, si hubo huecos de tiempo, cuántas filas `REVISAR`/`IMPLAUSIBLE`, cuántos errores y de qué tipo, y qué símbolo/lado aparece más seguido en `stale`. Los 5 formatos de CSV que ya existen comparten 4 columnas por nombre (`timestamp`, `stale`, `flag`, `error`) aunque el resto sea totalmente distinto por hipótesis — confirmado leyendo el código real de los 5 headers, no asumido. Una sola herramienta genérica alcanza para los 5.

## Alcance
- `com.cryptobot.report.WatchHealthAnalyzer`: lógica pura de análisis, testeable sin tocar disco.
- `com.cryptobot.report.WatchHealthReport`: CLI que imprime el reporte de uno o más archivos.
- _(Fuera de alcance: auto-descubrimiento del último CSV por tipo en `data/`, agregación entre archivos, dependencia nueva de parseo CSV — ver Decisiones.)_

## Decisiones
- **Java, en `code/cryptobot/`** — decidido explícitamente con Marcelo antes de planificar. El análisis venía haciéndose a mano en Python, pero eso no ata la herramienta formal al mismo lenguaje; el proyecto es un módulo único Java/Maven y sumar un segundo lenguaje para esto sería una complejidad de más, sobre todo no siendo la zona de confort de Marcelo.
- **Ciclos y huecos, medidos del propio archivo, no asumidos.** Cada watcher tiene su propio intervalo nominal (30s en los 5 actuales) — en vez de hardcodearlo, se calcula la mediana real de los gaps entre timestamps distintos del archivo, y un gap se marca "hueco" si supera 3x esa mediana. Funciona igual para cualquier watcher futuro con otro intervalo, sin tocar la herramienta.
- **Sin dependencia nueva de parseo CSV.** La columna `error` es la única que puede venir citada (mismo formato que `escapeCsv` ya escribe en los 5 watchers, y siempre es la última columna) — alcanza con `line.split(",", cantidadDeColumnas)` + desescapar el último campo. Agregar `commons-csv` para esto hubiera sido una abstracción de más para un formato propio y simple.
- **Errores agrupados por mensaje, no por fila exacta.** Cada watcher escribe el error como `"<clave>: <mensaje>"` — se separa en el primer `": "` y se agrupa por el mensaje. Confirmado útil en la verificación real: 1.308 filas de "sin liquidez suficiente en alguna pata" (que no tiene prefijo, se agrupa entera) y 80 de "sin bids/asks esperados" repetidas en decenas de símbolos de YoBit distintos — sin esto hubieran aparecido como docenas de mensajes "distintos".

## Tareas
- [x] `WatchHealthAnalyzer` + `WatchHealthAnalyzerTest` (7 casos: conteo de filas/ciclos, detección de hueco vs. mediana medida, flags sin contar vacíos, agrupamiento de errores con y sin prefijo, conteo de tokens `stale`, campo `error` citado con coma y comillas embebidas, columna faltante en el header)
- [x] `WatchHealthReport` (CLI)
- [x] `mvn test` en verde
- [x] Verificación en vivo: 2 ciclos reales de `YobitTriangleWatcher` (1.446 filas), reporte cruzado a mano contra `grep`/`wc` — coincide exacto

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.report.WatchHealthReport -Dexec.args="data/archivo.csv"` sobre cualquier CSV de watcher ya generado.

**Debe cumplir:**
- [x] Funciona igual sobre los 5 formatos de CSV existentes, sin conocer cuál es cuál
- [x] Los números del reporte coinciden con un conteo manual (`grep`/`wc`) sobre un CSV real
- [x] Un archivo inválido no aborta el reporte de los demás

## Hallazgos
- **Verificado exacto contra el archivo real:** 1.446 filas, 1.308 "sin liquidez suficiente en alguna pata", 80 "sin bids/asks esperados", 2 ciclos — los 4 números coinciden con `grep -c`/`wc -l`/`cut | sort -u | wc -l` corridos a mano sobre el mismo CSV.
- **Hallazgo operativo nuevo, medido de paso:** cada ciclo real de `YobitTriangleWatcher` tardó **~112s**, no los 30s nominales — con 393 books del mismo exchange, el semáforo de `ParallelFetch` (8 concurrentes por exchange, Sprint 0014) domina el tiempo total en vez del intervalo configurado. Confirma con un dato medido la sospecha ya anotada en el backlog sobre YoBit y concurrencia — no bloqueante para este sprint, pero relevante para cuando se revise el rate limit real de cada exchange.

## Cierre
La herramienta queda lista para usarse en la revisión de la corrida nocturna (con las 5 hipótesis juntas), que sigue siendo el próximo hito operativo grande. De paso, el hallazgo de los ~112s/ciclo en YoBit es información nueva y real para cuando se revise el rate limit — no se tocó nada de `ParallelFetch` en este sprint, queda anotado.

Sigue pendiente: confirmar el tier real de NotBank, la fee del perpetuo de Poloniex, el rate limit real de cada exchange (ahora con un dato concreto más para YoBit), ancla BTC/ETH en YoBit, sumar Buda/YoBit a cash-and-carry, investigar un 5to exchange — y la corrida nocturna, todavía no ejecutada.
