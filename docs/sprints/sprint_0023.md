---
sprint: 23
titulo: "Conectar Bitfinex (6to exchange, hipótesis 01)"
etapa: 2
---

# Sprint 0023 — Conectar Bitfinex (6to exchange, hipótesis 01)

## Objetivo
Marcelo mencionó tener cuenta en Bitfinex (pendiente reverificar por el tiempo). Investigarlo con el mismo rigor que Latoken/CoinEx/Bitrue (Sprint 0021) y, si vale la pena, conectarlo.

## Alcance
- Research de Bitfinex: API pública, trust score, overlap de activos, fees, jurisdicción.
- `com.cryptobot.marketdata.bitfinex.BitfinexConnector` — mismo patrón que los otros 5 conectores.
- `ExchangeFees` + `TrackedAssets.all(...)` (6to parámetro) + `OverlapCheck`/`SpreadWatcher` actualizados.
- _(Fuera de alcance: futuros/funding de Bitfinex, Bitfinex en triangular, Bitfinex en cash-and-carry — ver Decisiones y Cierre.)_

## Decisiones
- **Conectar, sin dudarlo — el research lo justificó de sobra.** Trust Score CoinGecko 8/10 (el más alto de los 6 exchanges conectados), $17B+ en reservas, API pública real (spot y ~60 perpetuos, incluido funding, todo sin auth), 14 de 16 activos con perpetuo en Poloniex overlapean, no restringido para Chile, y Marcelo ya tiene cuenta (pendiente reverificar, no bloquea la Etapa 2 de solo lectura).
- **Hallazgo más importante: fee CERO, permanente, spot y ~60 perpetuos, sin umbral de volumen** — vigente desde el 17/12/2025, confirmado en el blog oficial de Bitfinex y corroborado por múltiples medios independientes (no un solo rumor). Hasta ahora, cada hipótesis evaluada en el proyecto perdió por fees, no por falta de spread bruto — un exchange con fee cero en una pata cambia esa cuenta de raíz. Confirmado en vivo: las comparaciones de `OverlapCheck` que incluyen a Bitfinex muestran la fee combinada más baja de todo el proyecto.
- **`UST` → `USDT`, normalización necesaria, no cosmética.** Confirmado contra `GET /v2/conf/pub:map:currency:label` que `UST` es el ticker de Bitfinex para Tether (no TerraUST). `TrackedAssets` agrupa por string exacto de moneda — sin normalizar, los 63 mercados UST de Bitfinex jamás hubieran cruzado con los USDT de los otros 5 exchanges, perdiendo en silencio toda esa superficie de comparación. Verificado en vivo que la normalización funciona: BTC/USDT lista a Bitfinex junto a los demás, no aparece un "BTC/UST" aparte.
- **El book de Bitfinex viene en un formato distinto a los otros 5**: un array plano combinado `[precio, count, cantidad]` donde el signo de la cantidad distingue bid de ask, no separado en `bids`/`asks`. `OrderBook` exige las listas pre-ordenadas (bid desc, ask asc) — el conector arma y ordena cada lado a mano, no confía en el orden de la respuesta.
- **Alcance de este sprint = mismo tamaño que conectar CoinEx (Sprint 0021): conector + hipótesis 01, nada más.** Futuros/funding, triangular y cash-and-carry quedan como backlog — el de futuros/funding con prioridad alta explícita, dado el impacto esperado de la fee cero.

## Tareas
- [x] Research Bitfinex (API pública, trust score, overlap, fees, jurisdicción) en vivo
- [x] `BitfinexConnector` + `BitfinexConnectorTest` (book con array plano reordenado a propósito, error con body `["error",...]`, markets con normalización UST→USDT y filtro de pares `TEST*`)
- [x] `ExchangeFees` (0,00%) + test
- [x] `TrackedAssets.all(...)` + `OverlapCheck`/`SpreadWatcher` actualizados
- [x] `mvn test` en verde
- [x] Verificación en vivo: `OverlapCheck`, confirmado Bitfinex participa, la normalización UST→USDT funciona, y el número real de activos

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.OverlapCheck`.

**Debe cumplir:**
- [x] Bitfinex aparece en las comparaciones de `OverlapCheck` para los activos donde se espera overlap
- [x] Los mercados UST de Bitfinex cruzan con los USDT de los demás exchanges (no un grupo separado)
- [x] Los 11 activos originales del Sprint 0007 siguen presentes
- [x] Ningún error nuevo — solo el patrón ya conocido de YoBit

## Hallazgos
- **Medido, no proyectado:** `OverlapCheck` pasó de 436 a **450 activos**.
- Las comparaciones con Bitfinex como una de las dos patas muestran la fee combinada más baja de todo el proyecto (ej. 0,20% en vez de 0,40%+) — consecuencia directa y ya visible de la fee cero.
- Los 7 errores de la corrida en vivo fueron todos el patrón ya conocido de YoBit — ninguno de Bitfinex.

## Cierre
Con Bitfinex, el proyecto pasa a 6 exchanges conectados. Más importante que el número: Bitfinex es, medido, el candidato de mayor impacto esperado para las hipótesis que siguen sin señal — su fee cero ataca directamente la razón por la que todo lo evaluado hasta ahora salió negativo neto.

Sigue pendiente, con **futuros/funding de Bitfinex como el ítem de mayor prioridad del backlog** (habilitaría la hipótesis 05 y mejoraría el breakeven de cash-and-carry): futuros/funding de CoinEx, Bitfinex/CoinEx en triangular y en cash-and-carry, el rate limit real de cada exchange, ancla BTC/ETH en YoBit triangular, Buda en cash-and-carry con conversión de moneda — y la corrida nocturna con las 5 hipótesis juntas, todavía no ejecutada.
