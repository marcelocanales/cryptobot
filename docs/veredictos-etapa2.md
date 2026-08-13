# Veredictos — Etapa 2

Este documento cumple el criterio de salida que el propio [roadmap.md](roadmap.md) define para la Etapa 2: *"cada hipótesis priorizada tiene un veredicto documentado con datos: señal real o descartada"*. No existía hasta ahora — se escribió después de la primera corrida nocturna completa de los 6 exchanges (2026-08-13, 8h20m, ~1,5M filas entre los 6 CSV).

**Documento vivo**, igual que [arquitectura.md](arquitectura.md): se actualiza cada vez que una corrida nueva cambia o refuerza un veredicto — no se reescribe la historia, se agrega evidencia nueva con su fecha.

## Resumen

| # | Hipótesis | Veredicto | Alcance probado |
| :-: | --- | --- | --- |
| 01 | [Spot cross-exchange](estrategias/01-spot-cross-exchange.md) | 🟡 **Señal real, acotada** | 450 activos, 6 exchanges, 8h20m |
| 02 | [Triangular intra-exchange](estrategias/02-triangular-intra-exchange.md) | 🔴 **Descartada** (en lo probado) | Poloniex + YoBit (ancla USDT), 8h20m |
| 03 | [Triangular cross-exchange](estrategias/03-triangular-cross-exchange.md) | 🔴 **Descartada** (en lo probado) | Solo Poloniex + NotBank, 8h20m |
| 04 | [Funding cash-and-carry](estrategias/04-funding-rate-cash-and-carry.md) | 🔴 **Descartada** (en lo probado) | Poloniex perp + 3 candidatos de spot, 8h20m |
| 05 | [Funding cross-exchange](estrategias/05-funding-rate-cross-exchange.md) | 🟡 **Señal real, acotada** | Poloniex + Bitfinex, 18 activos candidatos |
| 06 | [Premium regional](estrategias/06-premium-regional.md) | 🔴 **Descartada** (Chile) — fuera de la Etapa 2 | Medición manual, Etapa 1 (Sprint 0001) |
| 07 | [Nuevo listado](estrategias/07-nuevo-listado.md) | ⚪ **No abordada** — fuera de la Etapa 2 | Nunca construida |

🟡 = señal real pero angosta (no "arbitraje sin fricción" a gran escala, un caso concreto con reservas). 🔴 = sin señal en lo que se probó, con salvedad de alcance cuando corresponde. ⚪ = nunca se puso a prueba.

## 01 — Spot cross-exchange: señal real, acotada

**Alcance probado:** 450 activos descubiertos dinámicamente (`TrackedAssets`), las combinaciones posibles entre los 6 exchanges conectados, corridos 8h20m continuas (796 ciclos) el 2026-08-13.

**Veredicto:** de 450 activos, **uno solo** mostró una señal robusta: **ZEC** (USDT y BTC, comprando en Poloniex y vendiendo en CoinEx/Bitfinex). 100% de consistencia (marcado en el 100% de los ciclos en que apareció), movimiento de precio real y verificado — no un book congelado (92% de las filas sin ninguna pata `stale`) — con neto de 2,2% a 5,3% según el par. El resto del universo probado no mostró nada más allá de ruido de baja consistencia o artefactos identificados (choques de ticker, books de YoBit congelados — ver abajo).

**Por qué probablemente no se arbitró ya:** investigación externa (no solo la medición propia) encontró una ola regulatoria real y actual (2026) contra privacy coins — Coinbase deslistó Zcash/Monero/Dash/Horizen en marzo-abril, OKX/Bit2Me/Binance Dubai restringieron soporte, 10+ países limitan monedas de privacidad, la UE prohibirá su listado desde julio 2027. Menos venues dispuestos a operar ZEC, spreads que no se cierran porque hay menos jugadores puenteándolos — no es una casualidad estadística sin causa.

**Reservas:**
- Un solo activo de 450 — no hay evidencia de que esto sea un patrón generalizable, es un caso puntual.
- Una sola noche corrida completa — falta ver si se sostiene varios días o si empieza a comprimirse (corridas de esta noche y las próximas apuntan a esto).
- Nada de esto se probó ejecutable — es precio de book observado, no fills reales (ver Reservas generales, al final).

## 02 — Triangular intra-exchange: descartada, en lo probado

**Alcance probado:** Poloniex (23 triángulos, Sprint 0009-0011) y YoBit con ancla USDT (340 triángulos, Sprint 0018) — 1006 y 127 ciclos respectivamente en la corrida completa del 2026-08-13.

**Veredicto:** **0 marcas en ambos**, sobre más de 1000 y 127 ciclos. Resultado repetido, no de una sola foto — ya se había visto 0 señal en corridas cortas anteriores (Sprint 0009-0011, 0018).

**Alcance no probado:** anclas BTC/ETH en YoBit (7.500+ triángulos cada una) — diferido a propósito desde el Sprint 0018 por el hallazgo del Sprint 0011 (el par más líquido de Poloniex, ETH/BTC, pasa ~91% del tiempo congelado — apostar a un exchange más chico sin haber confirmado que no sufre lo mismo es alto costo por señal probablemente ya conocida).

## 03 — Triangular cross-exchange: descartada, con la salvedad más grande del catálogo

**Alcance probado:** **solo Poloniex + NotBank** (2 de los 6 exchanges conectados), 69 triángulos, 1006 ciclos en la corrida completa.

**Veredicto:** 8 marcas en total sobre 1006 ciclos × 69 triángulos × 2 direcciones — 2 combinaciones distintas, 3 y 5 veces cada una. Ruido, no señal.

**La salvedad importante:** esta es la hipótesis de **mayor convicción teórica de todo el catálogo** (ver [estrategias/README.md](estrategias/README.md) — "media-alta: la ventaja es cobertura/diseño, no velocidad pura") y **nunca se probó con su universo completo** — `CrossTriangleWatcher` solo alimenta `CrossVenue` con Poloniex y NotBank, YoBit/CoinEx/Bitfinex nunca entraron al buscador de triángulos cruzados. El veredicto de "descartada" vale para el par Poloniex+NotBank específicamente, no para la hipótesis en general.

## 04 — Cash-and-carry: descartada, en lo probado

**Alcance probado:** Poloniex como único exchange de perpetuos, con 3 candidatos de spot (Poloniex, NotBank, YoBit) — 1005 ciclos en la corrida completa.

**Veredicto:** 2 combinaciones marcadas en toda la noche, con consistencia de 6,6% y 0,2% — no hay caso.

**Alcance no probado:** Bitfinex como candidato de spot (fee 0%, mejoraría directamente el breakeven — backlog desde el Sprint 0023) y Buda (requiere conversión CLP→USDT, backlog desde el Sprint 0020).

## 05 — Funding cross-exchange: señal real, acotada, con mecanismo ahora entendido

**Alcance probado:** Poloniex + Bitfinex, 18 activos con perpetuo en ambos, 796 ciclos en la corrida completa — la primera vez que esta hipótesis corre sin parar toda una noche.

**Veredicto:** de los 18 "candidatos" que reporta el watcher, **11 resultaron ser un artefacto, no señal** — confirmado en vivo contra la API cruda de Poloniex (sin pasar por nuestro código): su funding rate fue exactamente 0,01% por período (10,95% anualizado) para prácticamente todos los activos, sin excepción, incluyendo BTC y ETH. La documentación pública de Poloniex confirma que su funding se compone de *Interest Rate + Premium Index* (mismo modelo estándar de la industria) — 0,01%/8h es el valor típico de la Interest Rate base; cuando el Premium Index (la parte que depende del mercado) da ~0, el funding total colapsa a esa base fija. No es señal de dos mercados divergiendo, es Poloniex sin actividad suficiente para generar un Premium Index propio en la mayoría de sus contratos.

**Lo que sí es real: BNB, FIL y ETH.** Bitfinex mostró una tasa propia, genuinamente calculada y móvil para estos tres — y la corrida nocturna cruzó el reset de funding de las 08:00 UTC, permitiendo ver el antes/después:

| Activo | Antes (04:20–07:58 UTC) | Después (07:58–12:41 UTC) |
| --- | --- | --- |
| BNB | 159,7% anualizado | 37,1% anualizado |
| FIL | 88,8% anualizado | 26,0% anualizado |
| ETH | 6,5% anualizado | 3,6% anualizado |

Los tres bajaron de magnitud pero **siguieron positivos** — primera evidencia de persistencia direccional real para esta hipótesis. BNB y FIL ya tenían profundidad real confirmada en el Sprint 0024 (~$120k–270k de notional, no polvo).

**Reservas:**
- Solo se observó **un** reset de funding — persistencia con n=1 es evidencia débil, podría revertir en el próximo. La corrida de esta noche (24h+) apunta a resolver esto viendo 2-3 resets más.
- El mecanismo de Poloniex (Interest Rate + Premium Index) está confirmado por su propia documentación pública y corroborado por la API cruda, pero Poloniex no publica el valor exacto de su Interest Rate por contrato — la lectura de "0,01% = base fija" es la explicación más consistente con la evidencia, no una confirmación textual de Poloniex.
- Nada de esto se probó ejecutable (ver Reservas generales).

## 06 — Premium regional: descartada (Chile), fuera de la Etapa 2

Medición manual en la sesión de Etapa 1 (Sprint 0001): sin señal en USDT/CLP. Nunca entró al alcance de la Etapa 2 — el propio catálogo la dejó condicionada, a retomar solo si aparece un país con control de capital real y accesible.

## 07 — Nuevo listado: no abordada, fuera de la Etapa 2

Nunca se construyó — requiere infraestructura de vigilancia activa de anuncios de listado, distinta de lo que comparten las otras seis hipótesis. No es un veredicto de "sin señal", es "no se llegó a probar".

## Reservas generales, válidas para las dos señales reales (01 y 05)

Estas no son específicas de ningún activo — aplican a **todo** lo medido en la Etapa 2, y son la razón por la que ningún veredicto de arriba se lee como "listo para operar":

1. **Todo es de solo lectura.** El proyecto nunca ejecutó una orden real — los números son de books observados, no de fills. Latencia, fill parcial y slippage real siguen sin medirse (backlog desde el Sprint 0001).
2. **Sin modelo de expectativa.** Un spread positivo puntual no es lo mismo que una expectativa matemática positiva sostenida — con latencia de por medio, cada intento es una apuesta, no un arbitraje sin riesgo (backlog desde el Sprint 0001).
3. **Sin política de rebalanceo ni de posición abierta/fill parcial** — necesarias antes de pensar en ejecutar cualquiera de las dos señales reales (backlog desde el Sprint 0001).
4. **Capital real:** no se abre ni se evalúa antes de una Etapa 3 explícitamente autorizada, y ni ahí sin pasar antes por simulación/paper trading — regla no negociable del proyecto, ver [metodologia.md](metodologia.md).
