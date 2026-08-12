---
sprint: 17
titulo: "TrackedAssets dinámico — descubrir pares, no 11 elegidos a mano"
etapa: 2
---

# Sprint 0017 — TrackedAssets dinámico — descubrir pares, no 11 elegidos a mano

## Objetivo
`TrackedAssets.all(...)` (hipótesis 01, usada por `SpreadWatcher`/`OverlapCheck`) devolvía una lista fija de 11 activos elegidos a mano desde el Sprint 0007 — nunca se había revisado si hay más pares reales compartidos entre 2+ de los 4 exchanges que simplemente no estaban en esa lista. Mismo principio que ya se aplicó a lo triangular (`TriangleFinder`/`CrossTriangleFinder`, Sprint 0009/0012): descubrir de la API real, no hardcodear.

## Alcance
- `BudaConnector.fetchMarkets()` / `YobitConnector.fetchMarkets()` — nuevos, mismo patrón que Poloniex/NotBank.
- `TrackedAssets.discover(List<CrossVenue>)` + `TrackedAssets.all(...)` reescrito para llamarlo.
- Dos refactors de arquitectura previos, no evitables: mover `CrossVenue` a `marketdata`, extraer `MinNotional.forCurrency` de `TriangleSpread`.
- _(Fuera de alcance: `SpreadWatcher`/`OverlapCheck` no cambian de firma; el umbral de `MinNotional` en sí no se toca.)_

## Decisiones
- **Monedas de cotización soportadas: USDT, USDC, CLP, BTC** — las mismas para las que `MinNotional` ya tiene un umbral de nocional verificado. Se excluyen a propósito COP/PEN/ARS/BRL (existen en NotBank/Buda): usar el default de USDT para ellas asumiría que valen lo mismo, que no es cierto. Queda como decisión explícita, no un olvido — si se confirma un umbral para alguna, se suma a `MinNotional` y `TrackedAssets` la toma automáticamente.
- **El orden de base/quote importa, a diferencia de un triángulo.** `TriangleFinder`/`CrossTriangleFinder` tratan un ciclo como intercambiable en cualquier dirección; acá "BTC/USDT" y "USDT/BTC" (si existiera) son productos distintos — no se normaliza el par.
- **Un activo entra a la lista final solo si aparece en 2+ exchanges.** Con uno solo no hay nada que comparar — mismo criterio que ya usaba la lista hardcodeada.
- **`CrossVenue` se mueve a `marketdata`, `MinNotional` se extrae de `TriangleSpread`.** Ninguno de los dos es específico de lo triangular — `CrossVenue` es "un mercado en un exchange concreto" y `MinNotional` es un umbral de liquidez, ambos neutrales. Con `TrackedAssets` como consumidor nuevo, dejarlos en `triangular` hubiera invertido la dependencia entre paquetes (`marketdata` es la capa de base). Cambio mecánico — mover + actualizar imports — no reescribe lógica.

## Tareas
- [x] Mover `CrossVenue` a `marketdata`, extraer `MinNotional.forCurrency` (5 archivos actualizados, `MinNotionalTest` nuevo)
- [x] `BudaConnector.fetchMarkets()` / `YobitConnector.fetchMarkets()` + tests con JSON real capturado en vivo
- [x] `TrackedAssets.discover(...)` + `all(...)` reescrito + `TrackedAssetsTest` con `CrossVenue` sintéticos
- [x] Verificación en vivo (`OverlapCheck`): confirmar más de 11 activos y que los 11 originales siguen

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.OverlapCheck` y confirmar la cantidad de secciones `=== activo ===` impresas.

**Debe cumplir:**
- [x] `mvn test` en verde, incluidos los tests nuevos
- [x] La corrida en vivo encuentra más de 11 activos
- [x] Los 11 activos originales (BTC/USDT, ETH/USDT, LTC/USDT, DOGE/USDT, SHIB/USDT, AAVE/USDT, GRAM/USDT, XTZ/USDT, BTC/CLP, ETH/CLP, LTC/BTC) siguen todos presentes

## Hallazgos
- **Medido, no proyectado:** la corrida en vivo encontró **67 activos** compartidos entre 2+ exchanges, frente a los 11 elegidos a mano hasta el Sprint 0016 — más de 6x la superficie de búsqueda de la hipótesis 01, sin cablear nada nuevo a mano.
- **Dos pares de YoBit sin liquidez bid, no un bug:** `comp_btc` y `shib_btc` devuelven la respuesta de `depth` sin la clave `"bids"` (no un array vacío) — confirmado contra la API real. El parser ya lo trata como error capturado por `ParallelFetch`, sin crashear la corrida; mismo tratamiento que cualquier otro error de exchange. No requiere cambio de código, queda documentado como una característica real de mercados ilíquidos.

## Cierre
Con esto, la hipótesis 01 (spot cross-exchange) deja de ser la de menor cobertura entre las 4 — pasa de 11 a 67 activos candidatos, con el mismo mecanismo de descubrimiento en vivo que ya tenían la 02 y la 03. Las 4 hipótesis quedan con superficie de búsqueda determinada por lo que realmente ofrecen los exchanges, no por lo que alguien pensó en cablear a mano.

Sigue pendiente, sin tocar en este sprint: confirmar el tier real de NotBank, la fee del perpetuo de Poloniex contra una fuente más dura, el rate limit real de cada exchange, y la lista de alternativas ya guardada en el backlog (triangular en YoBit, herramienta de salud de mercado, Buda/YoBit como candidatos de spot en cash-and-carry, investigar un 5to exchange) — más la corrida nocturna con las 4 hipótesis juntas, todavía no ejecutada.
