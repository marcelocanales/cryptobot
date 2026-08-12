---
sprint: 18
titulo: "Triangular en YoBit (ancla USDT)"
etapa: 2
---

# Sprint 0018 — Triangular en YoBit (ancla USDT)

## Objetivo
El catálogo predijo desde el Sprint 0001 que exchanges chicos con muchos pares (menos bots/market makers vigilando cada tasa cruzada) son el caso más prometedor para arbitraje triangular — hasta ahora la hipótesis 02 solo se probó en Poloniex. Sumar YoBit, con la superficie de búsqueda acotada según lo que realmente hace falta construir, no según una estimación vieja del backlog.

## Alcance
- Medir en vivo (sin código nuevo, solo corriendo `TriangleFinder` real contra `YobitConnector.fetchMarkets()`) cuántos triángulos hay por ancla, antes de decidir qué construir.
- `ExchangeConnector.fetchMarkets()` a la interfaz.
- Extraer `TriangleCheckRunner`/`TriangleWatchRunner`, reducir `TriangleCheck`/`TriangleWatcher` a mains delgados.
- `YobitTriangleCheck` / `YobitTriangleWatcher`, ancla USDT.
- _(Fuera de alcance: ancla BTC/ETH en YoBit — ver Decisiones.)_

## Decisiones
- **Solo ancla USDT en este sprint.** Medido en vivo antes de construir: 340 triángulos ancla USDT (393 order books únicos) vs. **7.520 ancla BTC y 7.516 ancla ETH** — la estimación vieja del backlog ("~4.573 candidatos BTC") estaba subestimada, el candidato real es 16x más grande que USDT, no ~13x. 393 books es del mismo orden que los 168 que `CrossTriangleWatcher` ya procesa en ~12s/ciclo (Sprint 0014, medido) — encaja cómodo en el ciclo de 30s sin cambios de infraestructura. BTC/ETH-anclado queda backlog explícito (no descartado): el Sprint 0011 ya encontró que incluso el par más líquido y clásico de Poloniex (ETH/BTC) pasa ~91% del tiempo congelado — apostar esfuerzo de construcción a pares BTC/ETH en un exchange todavía más chico y menos vigilado que Poloniex, sin haber confirmado primero si sufren el mismo patrón, es alto costo por una señal probablemente ya conocida.
- **`ExchangeConnector` gana `fetchMarkets()` como método de interfaz**, no solo como método concreto de cada connector. Los 4 ya lo implementaban con la misma firma — declararlo en la interfaz es mecánico (cero lógica nueva) y es lo que habilita que `TriangleCheckRunner`/`TriangleWatchRunner` reciban cualquier `ExchangeConnector` sin conocer el tipo concreto.
- **Extraer el runner compartido, no duplicar `TriangleCheck`/`TriangleWatcher`.** Ambos tenían `PoloniexConnector`/`"Poloniex"` cableados adentro — a diferencia de `TriangleFinder`/`TriangleSpread`, que ya eran agnósticos de exchange desde el Sprint 0009/0010. Copiar ~170 líneas casi idénticas para YoBit hubiera sido duplicar la misma hipótesis, no una hipótesis distinta (a diferencia de por qué sí hay archivos separados entre 02/03/04, que tienen matemática distinta). Los invocables de Poloniex no cambiaron de nombre ni de comportamiento — verificado en vivo, mismo output antes/después del refactor.
- **Sin guard de implausibilidad, a diferencia de lo cross-exchange.** El choque de tickers (Sprint 0012, "BOB") es un riesgo de comparar el mismo string entre exchanges distintos — acá todo es un solo exchange, no aplica. Sí aparecieron brutos muy negativos (hasta -99,99%) en pares sin liquidez real, pero como ninguno se acerca a positivo no generan falsos `REVISAR` — es la fricción esperada de un book fino, no un bug de datos.

## Tareas
- [x] Medir en vivo triángulos por ancla en YoBit (340 USDT / 7.520 BTC / 7.516 ETH)
- [x] `ExchangeConnector.fetchMarkets()` a la interfaz (+ 3 fakes de test actualizados)
- [x] `TriangleCheckRunner` + `TriangleWatchRunner`, `TriangleCheck`/`TriangleWatcher` reducidos a mains delgados
- [x] `YobitTriangleCheck` + `YobitTriangleWatcher`
- [x] `mvn test` en verde
- [x] Verificación en vivo: `TriangleCheck` (Poloniex, confirma output sin cambios) y `YobitTriangleCheck`

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.YobitTriangleCheck` para la foto única, o `...watch.YobitTriangleWatcher` para la corrida continua (CSV en `data/yobit-triangle-watch-*.csv`).

**Debe cumplir:**
- [x] `TriangleCheck`/`TriangleWatcher` (Poloniex) mantienen el mismo comportamiento después del refactor
- [x] `YobitTriangleCheck` encuentra los 340 triángulos medidos, 393 order books únicos
- [x] Ningún error de fetch interrumpe la corrida — todos capturados por `ParallelFetch`

## Hallazgos
- **Medido, no proyectado:** 340 triángulos / 393 books en YoBit (ancla USDT) — la estimación vieja del backlog para BTC (~4.573) resultó ~40% baja: el número real medido es 7.520.
- **43 de 393 books (~11%) sin liquidez bid**, mismo patrón que `comp_btc`/`shib_btc` del Sprint 0017 (la API omite `"bids"` en vez de mandar vacío), más una variante nueva (`azy_doge` devuelve `[]` en vez de un objeto) — todos capturados como error, ninguno interrumpe la corrida.
- **0 de 680 direcciones con neto positivo** — sin señal en YoBit tampoco, mismo veredicto pendiente que las demás hipótesis.

## Cierre
Hipótesis 02 (triangular intra-exchange) suma su segundo exchange, con la superficie de búsqueda acotada por un número medido, no por una intuición vieja del backlog. El refactor (`TriangleCheckRunner`/`TriangleWatchRunner`) deja el mecanismo listo para sumar un tercer exchange sin duplicar código, si en algún momento hace falta.

Sigue pendiente: ancla BTC/ETH en YoBit (backlog explícito), confirmar el tier real de NotBank, la fee del perpetuo de Poloniex, el rate limit real de cada exchange, la herramienta de salud de mercado, sumar Buda/YoBit a cash-and-carry, investigar un 5to exchange — y la corrida nocturna con las hipótesis juntas, todavía no ejecutada.
