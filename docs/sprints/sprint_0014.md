---
sprint: 14
titulo: "Fetches en paralelo (ParallelFetch, aplicado a todo)"
etapa: 2
---

# Sprint 0014 — Fetches en paralelo (ParallelFetch, aplicado a todo)

## Objetivo
`CrossTriangleWatcher` reveló el problema con datos reales: 168 order books, uno a la vez, hicieron el primer ciclo notoriamente más lento que los otros watchers — ni siquiera llegaba a completarse en 120 segundos. Ningún conector ni watcher del proyecto usaba concurrencia. Marcelo pidió pensarlo como refactor transversal, no un parche puntual — y preguntó específicamente si se puede llamar a distintos exchanges al mismo tiempo.

## Alcance
- `ParallelFetch`: utilidad compartida y genérica, con virtual threads (JDK 21).
- Aplicada a los 6 puntos del proyecto que pedían order books: `SpreadWatcher`, `OverlapCheck`, `TriangleWatcher`, `TriangleCheck`, `CrossTriangleWatcher`, `CrossTriangleCheck` — y por consistencia también `Main`.
- _(Fuera de alcance: reescribir los conectores a async — no hace falta con virtual threads; streaming por WebSocket en vez de polling REST — eso sí sería un paso real hacia alta frecuencia, pero es un cambio de arquitectura más grande, no de este sprint.)_

## Decisiones
- **Virtual threads, no reescribir conectores.** JDK 21 (ya la versión del proyecto) permite correr muchas llamadas bloqueantes en paralelo sin volverlas async — cada `ExchangeConnector` sigue exactamente igual (`HttpClient.send()`, síncrono). La concurrencia se agrega solo en la capa que ya tenía el `for` de fetches.
- **Sin límite entre exchanges, acotado dentro de cada uno.** Distintos exchanges tienen APIs y rate limits independientes — no hay riesgo en pedirles a todos a la vez. Dentro de un mismo exchange sí hay riesgo real: ninguno de los 4 tiene su rate limit confirmado (`entorno.md` no lo documenta), y pedir 130+ books de golpe contra el mismo exchange podría terminar en 429s o bloqueos, peor que la lentitud actual. Se usa un `Semaphore` por exchange, límite `8` — conservador y no medido, anotado en el backlog para ajustar con datos reales.
- **Mismo patrón mecánico en los 6 call sites.** Armar la lista de `FetchTask` a partir de lo que ya se iteraba, un solo `fetchAll()` por ciclo/corrida, y mover el trabajo por-ítem (staleness, filas de error) a iterar sobre `Outcome.results()`/`errors()` en vez de hacerlo inline dentro del fetch — la lógica de negocio no cambió en ningún archivo, solo cómo se obtienen los books.
- **Efecto secundario positivo en `SpreadWatcher`:** antes, cada fila de un ciclo tenía un timestamp levemente distinto (se computaba por activo, según cuánto había avanzado el `for` secuencial). Ahora todas las filas de un mismo ciclo comparten el mismo timestamp — más correcto: un ciclo es una sola foto, no una serie de fotos consecutivas.

## Tareas
- [x] `ParallelFetch` + test (separación results/errors, límite de concurrencia medido con contador atómico, exchanges distintos no se bloquean entre sí)
- [x] Refactor de los 6 watchers/checks + `Main`
- [x] Verificación en vivo de cada uno, con tiempos medidos

## Hallazgos de la verificación en vivo

| Herramienta | Books | Exchanges | Antes | Después |
| --- | :---: | :---: | --- | --- |
| `CrossTriangleCheck` | 168 | 2 | no completaba 1 ciclo en 120s | **12,3s** totales |
| `TriangleCheck` | 40 | 1 | — | **8,7s** totales |
| `OverlapCheck` | 27 | 4 | — | **2,8s** totales |

En los tres casos, el resultado (bruto/neto por combinación) salió idéntico al de las corridas secuenciales anteriores — el refactor cambió la velocidad, no la lógica. Confirmado también en `SpreadWatcher`, `TriangleWatcher` y `CrossTriangleWatcher` con corridas cortas: mismo formato de CSV, mismo número de filas por ciclo.

## Sprint Review
**Cómo probar:** `mvn test` (incluye `ParallelFetchTest`); correr cualquiera de los 6 watchers/checks y comparar contra los tiempos de la tabla de arriba.

**Debe cumplir:**
- [x] Ningún resultado cambia por el refactor — mismos números que la versión secuencial
- [x] Un fetch que falla no cancela a los demás
- [x] El límite de concurrencia por exchange se respeta (verificado con test, no solo revisado a ojo)

## Cierre

Con esto, el proyecto entero corre sus fetches en paralelo, con un único punto de control (`ParallelFetch`) para la política de concurrencia — si mañana hace falta bajarle el límite a un exchange específico, o subírselo a otro, es un cambio en un solo lugar, no seis.

Queda anotado, a propósito, que esto es un paso hacia "más rápido", no hacia "alta frecuencia" de verdad — eso necesitaría reemplazar el polling REST por streams de order book (WebSocket), una decisión de arquitectura más grande que no correspondía meter en este sprint. El límite de concurrencia por exchange (`8`) sigue siendo un supuesto, no un dato medido — queda en el backlog confirmarlo con la práctica.
