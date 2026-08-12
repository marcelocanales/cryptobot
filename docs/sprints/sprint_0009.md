---
sprint: 9
titulo: "Arbitraje triangular intra-exchange, primer corte (Poloniex)"
etapa: 2
---

# Sprint 0009 — Arbitraje triangular intra-exchange, primer corte (Poloniex)

## Objetivo
Primer código para la hipótesis 02 del catálogo ([triangular intra-exchange](../estrategias/02-triangular-intra-exchange.md)) — descubrir triángulos reales en Poloniex (no una lista armada a mano) y medir si la tasa cruzada se rompe lo suficiente como para cubrir 3 fees en vez de 2.

## Alcance
- Descubrimiento de triángulos reales a partir de `GET /markets` de Poloniex — no asumir qué activos "deberían" formar un triángulo.
- Cálculo de spread neto de un ciclo de 3 patas (bruto compuesto, menos 3 fees).
- Foto en vivo (`TriangleCheck`), no continua todavía.
- _(Fuera de alcance: monitoreo continuo, extensión a YoBit, veredicto de la hipótesis 01 — ver Cierre del Sprint 0008/decisión de Marcelo.)_

## Decisiones
- **Poloniex primero, no YoBit.** YoBit tiene muchísimos más candidatos (340 triángulos anclados en USDT, 4.573 en BTC — contra 23 de Poloniex), pero ya tiene reputación de datos cuestionada confirmada en sprints anteriores (wash trading, book mayormente congelado). Se valida el mecanismo con datos más confiables antes de apuntarlo a un universo 200x más grande y más sucio.
- **Triángulos descubiertos, no hardcodeados.** `TriangleFinder` es genérico: dado un ancla, encuentra qué monedas cotizan contra ella y cuáles de esos pares además tienen mercado directo entre sí. En la corrida en vivo encontró **23 triángulos** — más de los contados a mano explorando la API manualmente al planificar el sprint, señal de que descubrirlo en código en vez de a mano vale la pena incluso en un caso "chico".
- **`TriangleSpread` no reusa `NetSpread`.** Un triángulo compone un monto real a través de 3 conversiones (multiplicar, no restar) — forzar una abstracción común entre "restar dos fees a una diferencia de precio" (2 patas) y "componer 3 conversiones" (3 patas) sería más confuso que dos cálculos separados y honestos. Sí reusa `ExchangeFees.takerFee`, que es genuinamente la misma fuente de verdad en los dos casos.
- **Mismo filtro de liquidez que ya existe** (`bestBidAbove`/`bestAskAbove`), aplicado pata por pata según la moneda de cotización de esa pata (USDT→50, BTC→0,00078 — mismos umbrales que `TrackedAssets`).

## Tareas
- [x] `Market` + `PoloniexConnector.fetchMarkets()` + test con respuesta real
- [x] `TriangleFinder` + test con datos sintéticos (sin llamar a ninguna API)
- [x] `TriangleSpread` + test usando el propio ejemplo ilustrativo del doc de la estrategia como caso verificable a mano
- [x] `TriangleCheck` + verificación en vivo contra Poloniex real

## Hallazgos de la verificación en vivo

**23 triángulos encontrados, 0 con neto positivo en ninguna de las 46 direcciones evaluadas.** Mismo patrón que la hipótesis 01: sin arbitraje.

**El bruto ya es negativo en casi todos los casos — no es solo un problema de fees.** A diferencia del spot cross-exchange (donde el bruto a veces era positivo y las fees lo tumbaban), acá la mayoría de los triángulos ya pierden antes de fees: cruzar 3 spreads bid-ask en el mismo instante tiene un costo, y ese costo por sí solo suele superar cualquier inconsistencia real de la tasa cruzada. El caso más consistente con el ejemplo ilustrativo del doc (USDT-BTC-ETH, los tres pares más líquidos) dio bruto -0,56%/-0,71% — negativo pero con la magnitud más chica de las 23, justo lo esperable en el triángulo más líquido.

**Verificación a mano confirmó que el código está bien, no que hay un bug:** se recalculó a mano la dirección USDT→BTC→ETH→USDT con el book real del momento (ask BTC/USDT tras filtrar liquidez: 63385,71; ask ETH/BTC: 0,02987; bid ETH/USDT: 1882,90) y dio ≈ -0,546%, contra el -0,555% que reportó el programa — la diferencia es el redondeo del cálculo manual, no un error de código.

**ETC (USDT-BTC-ETC) dio -40% bruto — mismo patrón que XTZ/AAVE/GRAM antes.** Se verificó el book real de ETC/BTC: el mejor bid de vidriera (0,000099) tiene un nocional de apenas ~0,0000327 BTC — muy por debajo del umbral de 0,00078 — y el filtro de liquidez tuvo que bajar varios niveles (hasta 0,000060, ~40% más abajo) para encontrar una cantidad real. No es una oportunidad, es otro mercado fino — la técnica de filtrado que nació en el Sprint 0003 para spot cross-exchange demostró ser igual de necesaria acá.

## Sprint Review
**Cómo probar:** `mvn test` (incluye `TriangleFinderTest`, `TriangleSpreadTest`, y el nuevo test de `PoloniexConnectorTest` para `fetchMarkets`); `mvn compile exec:java -Dexec.mainClass=com.cryptobot.TriangleCheck` para la foto en vivo.

**Debe cumplir:**
- [x] Los triángulos se descubren de `GET /markets`, no de una lista escrita a mano
- [x] El cálculo reproduce el ejemplo ilustrativo del doc de estrategia (+0,4% bruto)
- [x] Una pata sin liquidez suficiente descarta esa dirección en vez de inventar un número

## Cierre

Primer resultado de la hipótesis 02: sin arbitraje, y con un matiz que la 01 no tenía tan marcado — acá el bruto ya es mayormente negativo, cruzar 3 spreads cuesta más de lo que la inconsistencia de tasa cruzada suele romper, al menos en Poloniex y en este instante. Coherente con lo que el propio catálogo de estrategias ya anticipaba: "en pares grandes y exchanges grandes, cerrado por la misma razón que el spot cross-exchange — velocidad, no inteligencia".

Quedó pendiente, a propósito: la hipótesis 01 sigue sin veredicto (Marcelo la dejó corriendo de noche por su cuenta), y este primer corte de la 02 tampoco se escribe como veredicto todavía — es una sola foto, no una corrida sostenida. Siguiente paso natural: correr `TriangleCheck` varias veces más (o construir su versión continua, análoga a `SpreadWatcher`) antes de sacar una conclusión — y extender a YoBit, que es donde el catálogo de estrategias señala que vive la verdadera pregunta abierta de esta hipótesis (muchos más pares, pero con la reputación de datos ya cuestionada).
