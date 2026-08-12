---
sprint: 1
titulo: "Estado del arte y catálogo de hipótesis"
etapa: 1
---

# Sprint 0001 — Estado del arte y catálogo de hipótesis

## Objetivo
Catalogar las estrategias de arbitraje cripto conocidas — la que Marcelo ya operó hace ~10 años en Bittrex/Cex.io/Poloniex y las que aparecieron después — explicar cómo funciona cada una y qué cambió en el mercado desde entonces, y salir con 2-3 hipótesis priorizadas y concretas para testear con datos reales en la Etapa 2.

## Alcance
- Un documento por estrategia en [`docs/estrategias/`](../estrategias/README.md) (plantilla: [`_plantilla-estrategia.md`](../estrategias/_plantilla-estrategia.md)), cada uno con: qué es (mecanismo detallado, sin asumir conocimiento previo), diagrama del flujo (PlantUML, Estilo Cryptobot), qué cambió en 10 años, riesgos propios, e hipótesis de vigencia hoy con su convicción.
- Términos no obvios van a un glosario compartido ([`docs/glosario.md`](../glosario.md)) en vez de reexplicarse en cada documento.
- Estrategias a documentar:
  - Spot cross-exchange (2 legs) — la que ya se operó.
  - Triangular intra-exchange (3 legs, mismo exchange).
  - Triangular / multi-leg cross-exchange (varios exchanges).
  - Funding rate arbitrage / cash-and-carry (spot + short perpetuo).
  - Funding rate cross-exchange (diferencial de funding entre exchanges).
  - Premium regional / fiat (spreads entre exchanges de distintas regiones/monedas).
  - Arbitraje de nuevo listado.
- **Se documenta una estrategia a la vez:** se arma, se revisa con Marcelo, y recién ahí se pasa a la siguiente — la primera es spot cross-exchange, para calibrar el nivel de detalle con la experiencia real.
- Descartar explícitamente, con su porqué, lo que no se va a explorar (ej. latency arbitrage puro en exchanges grandes — requiere infraestructura de colocation que no vamos a tener) — queda en [`estrategias/README.md`](../estrategias/README.md).
- Al terminar las 7, síntesis en [`estrategias/README.md`](../estrategias/README.md): índice, descartadas, y 2-3 hipótesis priorizadas y concretas para pasar a la Etapa 2.
- _(Fuera de alcance: cualquier conexión a una API real o dato en vivo — eso es la Etapa 2.)_

## Decisiones
_(sprint de investigación/documentación pura — no hay decisiones de stack acá)_

## Tareas
- [x] Estructura: `docs/estrategias/` + plantilla + `docs/glosario.md` + `estrategias/README.md` (índice vacío)
- [x] `estrategias/01-spot-cross-exchange.md`
- [x] `estrategias/02-triangular-intra-exchange.md`
- [x] `estrategias/03-triangular-cross-exchange.md`
- [x] `estrategias/04-funding-rate-cash-and-carry.md`
- [x] `estrategias/05-funding-rate-cross-exchange.md`
- [x] `estrategias/06-premium-regional.md`
- [x] `estrategias/07-nuevo-listado.md`
- [x] Descartadas documentadas en `estrategias/README.md`
- [x] Síntesis: hipótesis priorizadas en `estrategias/README.md`

## Sprint Review
**Cómo probar:** los 7 documentos de estrategia existen en `docs/estrategias/`, cada uno sigue la plantilla, el glosario tiene los términos que fueron apareciendo, y `estrategias/README.md` cierra con la síntesis priorizada.

**Debe cumplir:**
- [x] Cada estrategia tiene: qué es, diagrama, qué cambió, riesgos propios, hipótesis de si queda edge y dónde (con convicción)
- [x] Hay hipótesis priorizadas y concretas (no genéricas), listas para diseñar la Etapa 2

## Cierre

Quedó funcionando: catálogo completo de 7 estrategias, cada una con mecanismo, diagrama, riesgos propios e hipótesis de vigencia — más un glosario de 20 términos construido en el camino. La síntesis giró distinto a lo previsto: en vez de elegir 2-3 estrategias y descartar el resto, la priorización real quedó ordenada por **qué exchanges instrumentar** (Poloniex, NotBank, BudaPRO, YoBit — los cuatro con cuenta ya abierta), porque una vez armada la captura de datos, varias hipótesis se pueden chequear sobre la misma información. Spot cross-exchange (01) quedó como prioridad más alta y explícita, sin agruparse con las demás, por ser la de mayor evidencia empírica ya reunida hoy mismo comparando order books reales a mano.

Sumado en el camino, sin estar en el alcance original: una revisión manual de varios pares reales (DOGE, AAVE, GRAM, USDT/CLP) entre los cuatro exchanges, que no encontró arbitraje directo ejecutable en ningún caso — pero sí encontró algo más valioso para la Etapa 2: una heurística concreta para detectar order books con datos no genuinos (cantidades idénticas repetidas en varios niveles de precio, volumen en cero con book poblado), y una idea de ejecución (maker en la pata de entrada, taker en el hedge) que quedó anotada en el backlog con su riesgo central identificado (selección adversa).

Pendiente / siguiente paso: Sprint 0002, Etapa 2 — construir el primer conector de solo lectura contra la API pública de Poloniex y NotBank (ya confirmadas, sin auth), y una primera comparación real de spread en un par líquido (BTC/USDT o LTC/USDT) para validar que la herramienta calcula bien antes de expandir a pares chicos.
