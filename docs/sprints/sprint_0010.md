---
sprint: 10
titulo: "TriangleWatcher — monitoreo continuo triangular"
etapa: 2
---

# Sprint 0010 — TriangleWatcher — monitoreo continuo triangular

## Objetivo
Mismo salto que `SpreadWatcher` fue para la hipótesis 01 (Sprint 0002→0003): pasar de una sola foto (`TriangleCheck`, Sprint 0009) a una corrida continua para la hipótesis 02, con el mismo detector de precio congelado que ya demostró su valor.

## Alcance
- `TriangleWatcher`: corre en loop, descubre los triángulos una vez, registra cada ciclo en CSV.
- Reusa `StalenessTracker` tal cual (sin cambios) — solo hizo falta que `TriangleSpread` expusiera qué pata usó cada símbolo/lado.
- _(Fuera de alcance: correrlo toda la noche todavía — eso es la siguiente decisión operativa, no de este sprint; extensión a YoBit sigue pendiente de un sprint futuro.)_

## Decisiones
- **`TriangleSpread.Result` gana `legs()`.** Antes solo devolvía el path y los porcentajes — no alcanzaba para saber, después del hecho, qué símbolo y lado (bid/ask) participó en cada pata. Sin eso, `TriangleWatcher` no podría cruzar el resultado de una dirección con el estado de staleness de sus patas. Cambio aditivo — no rompió los tests existentes de `TriangleSpreadTest` (Sprint 0009), se sumó uno nuevo para cubrir el campo.
- **`minNotionalFor` pasa de privado a público en `TriangleSpread`.** Antes de este sprint solo lo usaba la propia clase; `TriangleWatcher` necesita el mismo umbral para decidir, al observar staleness, si un nivel de precio cuenta como "el mejor real" o hay que ignorarlo — mismo criterio en los dos lugares, una sola fuente.
- **Mercados descubiertos una sola vez por corrida, no por ciclo.** Los mercados de un exchange no cambian en las horas que dura una corrida — pedirlos de nuevo cada 30s sería desperdiciar una llamada sin necesidad, mismo criterio que `NotBankConnector` ya aplica a su mapa de instrumentos.

## Tareas
- [x] `TriangleSpread.Result.legs()` + test
- [x] `TriangleWatcher`: descubrimiento único al arrancar, loop de 30s, CSV largo, staleness reusado
- [x] Verificación en vivo: 2 ciclos cortos, confirmar estructura del CSV (9 columnas, ambas direcciones por triángulo)

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.watch.TriangleWatcher` y confirmar el CSV en `data/triangle-watch-*.csv`.

**Debe cumplir:**
- [x] Los triángulos se descubren una sola vez, no en cada ciclo
- [x] Cada order book único se pide una sola vez por ciclo, aunque participe en varios triángulos
- [x] Una pata marcada `stale` en un ciclo se refleja en la fila de la dirección que la usa

## Cierre

Con esto, las dos hipótesis abiertas (01 y 02) tienen la misma capacidad de correr sin supervisión y dejar un registro continuo, con el mismo estándar de detección de datos falsos. Ninguna de las dos tiene veredicto todavía — sigue siendo la decisión pendiente, no de este sprint.

Siguiente paso natural: una corrida más larga de `TriangleWatcher` (análoga a la que se hizo con `SpreadWatcher`) para ver si el detector de staleness encuentra algo parecido a lo que encontró en la 01 (mercados finos que no se mueven) antes de escribir cualquier conclusión — y, más adelante, extender el mecanismo a YoBit.
