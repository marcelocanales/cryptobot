---
sprint: 13
titulo: "CrossTriangleWatcher — monitoreo continuo cross-exchange"
etapa: 2
---

# Sprint 0013 — CrossTriangleWatcher — monitoreo continuo cross-exchange

## Objetivo
Mismo salto que `TriangleWatcher` fue para la hipótesis 02 (Sprint 0009→0010): pasar de una sola foto (`CrossTriangleCheck`, Sprint 0012) a una corrida continua para la hipótesis 03, antes de sacar cualquier conclusión — una foto no alcanza para saber si hay arbitraje, aparece y desaparece.

## Alcance
- `CrossTriangleWatcher`: corre en loop, descubre los 69 triángulos una vez, registra cada ciclo en CSV.
- Reusa `StalenessTracker` tal cual — sin cambios de código en él.
- Reusa el umbral de implausibilidad del Sprint 0012 (choque de tickers) — un resultado extremo se marca `IMPLAUSIBLE` en el CSV, no `REVISAR`.
- _(Fuera de alcance: correrlo un tramo largo todavía — eso es la siguiente decisión operativa, no de este sprint.)_

## Decisiones
- **Mismo patrón que `TriangleWatcher`, sin inventar nada nuevo.** Descubrimiento único al arrancar (los mercados no cambian en el rato que dura una corrida), un fetch por order book único por ciclo aunque participe en varios triángulos, CSV largo.
- **El guard de implausibilidad viaja con la corrida continua.** Sin él, cada ciclo repetiría el falso +2.158.104% de BOB (Sprint 0012) 168 veces por hora — se marca `IMPLAUSIBLE` en vez de `REVISAR`, y no se cuenta como dirección positiva en el resumen de consola.

## Tareas
- [x] `CrossTriangleWatcher`: descubrimiento único, loop de 30s, CSV largo, staleness y guard de implausibilidad reusados
- [x] Verificación en vivo: 1 ciclo completo (138 filas + header), confirmado el guard marcando `USDT-A-BTC` como `IMPLAUSIBLE`

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.watch.CrossTriangleWatcher` y confirmar el CSV en `data/cross-triangle-watch-*.csv`.

**Debe cumplir:**
- [x] Los triángulos se descubren una sola vez, no en cada ciclo
- [x] Cada order book único se pide una sola vez por ciclo
- [x] Un resultado implausible se marca como tal, no se reporta como señal real

## Cierre

Con esto, las tres hipótesis (01, 02, 03) tienen la misma capacidad de correr sin supervisión con el mismo estándar de detección de datos falsos (liquidez insuficiente, precio congelado, y ahora también implausibilidad por choque de tickers). Ninguna tiene veredicto todavía.

Siguiente paso: dejarlo corriendo un tramo (Marcelo decide cuánto) y revisar los hallazgos — mismo ritual que las corridas anteriores.
