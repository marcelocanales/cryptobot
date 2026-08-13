---
sprint: 24
titulo: "Funding rate cross-exchange (05), primer corte: Poloniex + Bitfinex"
etapa: 2
---

# Sprint 0024 — Funding rate cross-exchange (05), primer corte: Poloniex + Bitfinex

## Objetivo
La hipótesis 05 (funding rate cross-exchange) está priorizada #3 en el catálogo desde el Sprint 0001 y nunca se pudo probar — hacía falta un segundo exchange con perpetuos accesibles además de Poloniex. Con Bitfinex conectado (Sprint 0023, con fee cero en derivados) esa condición ya está — construir el primer corte y medir con datos reales.

## Alcance
- `BitfinexConnector.fetchPerpSymbols()` / `fetchPerpQuote(symbol)`.
- `ExchangeFees.PERP_TAKER_FEE["Bitfinex"] = 0%`.
- `com.cryptobot.funding.FundingCrossExchangeCandidates` + `FundingCrossExchangeSpread` (nuevos).
- `FundingCrossExchangeCheck` (foto en vivo).
- _(Fuera de alcance: watcher continuo, CoinEx como 3er candidato, interfaz común para conectores con perpetuos — ver Decisiones.)_

## Decisiones
- **Funding de Bitfinex confirmado en grilla fija de 8h** (0:00/8:00/16:00 UTC) — contra la documentación oficial (liquidación 3 veces al día) y cruzado contra un `NEXT_FUNDING_EVT_TIMESTAMP_MS` real (cae exacto en un múltiplo de 8h). La API no expone la hora de inicio del período actual, solo la del próximo — a diferencia de Poloniex (que sí mide `fT`/`nFT` real), acá `fundingTime` se **deriva** (`nextFundingTime - 8h`), documentado explícitamente como una aproximación, no una medición independiente cada vez.
- **`FundingCrossExchangeSpread` anualiza con el intervalo real de cada candidato, y mide breakeven en horas, no en "períodos".** Aunque hoy Poloniex y Bitfinex comparten el mismo intervalo (8h), el diseño no depende de esa coincidencia — importa el día que se sume un exchange con otro intervalo. Verificado con un test que fuerza intervalos distintos (8h vs 24h) y confirma que la comparación anualizada da un resultado distinto (y correcto) que comparar las tasas crudas por período.
- **Diseño "mejor de N candidatos" desde el arranque**, no un bolt-on de 2 exchanges — mismo principio ya aplicado a `CashAndCarrySpread` para el lado spot. Entre todos los candidatos con liquidez suficiente, se elige el de mayor funding anualizado como corto y el de menor (en otro exchange) como largo. Generaliza a un 3er exchange (CoinEx, ya en el backlog) sin tocar la lógica.
- **Sin interfaz común para conectores con perpetuos todavía** — con 2 implementaciones (Poloniex, Bitfinex) no se justifica (mismo criterio de "3er consumidor real" ya aplicado a `CrossVenue`/`MinNotional`). `FundingCrossExchangeCheck` resuelve cuál conector usar con un `if` explícito por nombre de exchange — pragmático para 2 casos conocidos.

## Tareas
- [x] `BitfinexConnector.fetchPerpSymbols()` / `fetchPerpQuote(symbol)` + tests (JSON real: ticker de derivado, status/deriv con verificación índice por índice contra la documentación oficial, símbolos con normalización UST→USDT)
- [x] `ExchangeFees.PERP_TAKER_FEE["Bitfinex"]` + test
- [x] `FundingCrossExchangeCandidates` + test (4 casos sintéticos: agrupa activo en 2 exchanges, excluye activo en 1 solo, excluye contrato Bitfinex no margined en USDT, extracción de asset correcta)
- [x] `FundingCrossExchangeSpread` + test (6 casos sintéticos: elige corto/largo correctamente, extremos entre 3 candidatos, anualiza con intervalos distintos, filtra por liquidez, excluye mismo exchange en ambas patas, diferencial no positivo → sin breakeven)
- [x] `FundingCrossExchangeCheck`
- [x] `mvn test` en verde
- [x] Verificación en vivo: primera corrida real de la hipótesis 05

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.FundingCrossExchangeCheck`.

**Debe cumplir:**
- [x] Descubre activos con perpetuo en ambos exchanges sin hardcodear
- [x] Elige correctamente el corto (mayor funding) y el largo (menor funding, otro exchange)
- [x] El breakeven se reporta en horas, coherente con el diferencial anualizado

## Hallazgos
- **Primera vez que la hipótesis 05 se prueba con datos reales**: 15 activos con perpetuo en Poloniex y Bitfinex, 12 evaluables con liquidez suficiente en ambos lados.
- **12 de 12 con diferencial anualizado positivo** — breakeven entre 3,3h (BNB) y 80,6h (ETH). Ningún resultado negativo entre los evaluables (el único caso sin señal, APT, tiene diferencial negativo y se reporta correctamente como "nunca").
- **FIL y BNB con diferenciales extremos** (funding de Bitfinex en -77,8% y -148,8% anualizado) — verificados con profundidad real de mercado (~$120k y ~$250k de notional en el book respectivamente, no polvo ni mercado fino). Es un dato real, no un artefacto — pero es una sola foto.
- **No se declara señal confirmada todavía, a propósito.** El propio catálogo (`docs/estrategias/05-funding-rate-cross-exchange.md`) plantea la pregunta central como persistencia, no existencia: "hay que medir cuán seguido el diferencial supera los costos... no solo si alguna vez aparece". Este sprint mide que existe, con datos reales — falta el watcher continuo para saber si se sostiene.

## Cierre
Con esto, la hipótesis 05 deja de ser la única priorizada del catálogo sin ningún dato real detrás — 24 sprints después de haberla priorizado. El resultado de esta primera foto es alentador (12/12 positivo) pero se reporta con la misma disciplina que el resto del proyecto: medido, no forzado a positivo, con el hallazgo de FIL/BNB anotado para investigar, no celebrado sin más.

Sigue pendiente, con el **watcher continuo de la hipótesis 05 como el ítem más importante** (es lo único que puede confirmar o descartar el hallazgo de hoy): Bitfinex en cash-and-carry (04), CoinEx como 3er candidato de funding cross-exchange, futuros/funding de CoinEx, CoinEx/Bitfinex en triangular, Buda en cash-and-carry con conversión de moneda, el rate limit real de cada exchange — y la corrida nocturna con las 5 hipótesis existentes, todavía no ejecutada (ahora con una 6ta hipótesis recién nacida que también merecería sumarse).
