---
sprint: 11
titulo: "Hallazgo: pares cotizados en BTC mayormente congelados en Poloniex"
etapa: 2
---

# Sprint 0011 — Hallazgo: pares cotizados en BTC mayormente congelados en Poloniex

## Objetivo
Documentar los hallazgos de la primera corrida larga en paralelo de `SpreadWatcher` y `TriangleWatcher` (67 minutos, ambas hipótesis corriendo a la vez) — sin cambios de código, es una corrida de verificación que reveló algo que vale la pena dejar anotado antes de seguir.

## Alcance
- Solo documentación. No hubo cambios de código en este sprint.

## Hallazgos

### SpreadWatcher (hipótesis 01) — confirma lo ya sabido, nada nuevo
5.610 filas, **0 `REVISAR`**. 6 errores puntuales de conexión a YoBit (transitorios, sin patrón). 2.002 filas con algún lado marcado `stale` — mismos mercados frágiles de siempre: YoBit congelado en BTC/ETH/LTC, XTZ muerto en Poloniex, LTC/BTC muerto en Buda.

### TriangleWatcher (hipótesis 02) — hallazgo nuevo
5.106 filas, **0 `REVISAR`**, cero errores de conexión, 111 ciclos completos en 66 minutos. Pero: **la mayoría de las patas cotizadas en BTC quedaron congeladas ~91% del tiempo (101 de 111 ciclos)** — no solo en pares exóticos (ya se sabía que ETC/BTC es fino, Sprint 0009), sino también en **ETH/BTC**, uno de los pares más clásicamente líquidos de todo el exchange: `SOL_BTC`, `ZEC_BTC`, `DOGE_BTC`, `XLM_BTC`, `ETC_BTC`, `ETH_BTC` (lado ask), `TRX_BTC`, `DASH_BTC`, `LTC_BTC`, y las cruzadas vía USDC (`XRP_USDC`, `TRX_USDC`, `LTC_USDC`).

**Verificación, no solo el número:** antes de anotar esto se comprobó que no es un bug de la corrida.
- El `gross_pct` del triángulo USDT-BTC-ETH varió en **111 de 111 ciclos** (nunca se repitió) — las otras dos patas (BTC/USDT, ETH/USDT) se mueven con normalidad, así que no es que toda la corrida haya quedado pegada.
- El ask de ETH/BTC específicamente quedó fijo en **0,02987** desde el ciclo ~11 hasta el final de la corrida.
- Se confirmó con un `fetch` en vivo, después de cortar la corrida: **sigue siendo exactamente 0,02987, la misma orden** (qty 0,642) — lleva ahí sin moverse desde antes del Sprint 0009, más de 2 horas antes.

## Por qué importa

Explica en buena parte el hallazgo del Sprint 0009 (el bruto de casi todos los triángulos salía negativo, no solo neto): buena parte de lo que parece "quiebre de tasa cruzada" no es una inconsistencia real y aprovechable — es que la pata en BTC casi no cotiza. El volumen de Poloniex se concentra en los pares USDT; los pares X/BTC, **incluso los más clásicos como ETH/BTC**, están bastante más dormidos de lo esperado. Esto va más allá de un caso aislado (como XTZ) — es un patrón que toca a la mayoría de los 23 triángulos descubiertos, porque casi todos comparten una pata en BTC.

## Actualizado en `entorno.md`
Nota agregada a la fila de Poloniex: ETH/BTC (y en general los pares cotizados en BTC) muestran actividad mucho menor a la esperada — verificar staleness antes de confiar en cualquier pata cotizada en BTC, no solo en los pares chicos.

## Cierre

Sin veredicto todavía en ninguna de las dos hipótesis — mismo criterio que se viene aplicando desde que Marcelo pidió no cerrar la 01 hasta tener la corrida nocturna. Este hallazgo sí cambia la lectura de la hipótesis 02: la superficie de búsqueda real (triángulos con las 3 patas genuinamente líquidas) es más chica de lo que sugieren los 23 triángulos descubiertos — varios de ellos dependen de una pata que rara vez actualiza.

Con esto anotado, próximo paso: decidir cómo seguir — ver conversación en el hilo principal.
