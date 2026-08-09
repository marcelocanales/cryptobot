---
sprint: 1
titulo: "Estado del arte y catálogo de hipótesis"
etapa: 1
---

# Sprint 0001 — Estado del arte y catálogo de hipótesis

## Objetivo
Catalogar las estrategias de arbitraje cripto conocidas — la que Marcelo ya operó hace ~10 años en Bittrex/Cex.io/Poloniex y las que aparecieron después — explicar cómo funciona cada una y qué cambió en el mercado desde entonces, y salir con 2-3 hipótesis priorizadas y concretas para testear con datos reales en la Etapa 2.

## Alcance
- Catalogar y explicar, con su hipótesis de vigencia hoy:
  - Spot cross-exchange (2 legs) — la que ya se operó.
  - Triangular intra-exchange (3 legs, mismo exchange).
  - Triangular / multi-leg cross-exchange (varios exchanges).
  - Funding rate arbitrage / cash-and-carry (spot + short perpetuo).
  - Funding rate cross-exchange (diferencial de funding entre exchanges).
  - Premium regional / fiat (spreads entre exchanges de distintas regiones/monedas).
  - Arbitraje de nuevo listado.
- Descartar explícitamente, con su porqué, lo que no se va a explorar (ej. latency arbitrage puro en exchanges grandes — requiere infraestructura de colocation que no vamos a tener).
- Priorizar 2-3 hipótesis concretas para pasar a la Etapa 2.
- _(Fuera de alcance: cualquier conexión a una API real o dato en vivo — eso es la Etapa 2.)_

## Decisiones
_(sprint de investigación/documentación pura — no hay decisiones de stack acá)_

## Tareas
- [ ] Catalogar cada estrategia (qué es, cómo la vivió Marcelo si aplica, qué cambió, hipótesis de edge hoy)
- [ ] Documentar qué se descarta y por qué
- [ ] Priorizar 2-3 hipótesis concretas para la Etapa 2

## Sprint Review
**Cómo probar:** el documento del catálogo existe, cubre todas las estrategias del alcance, y termina con una lista corta y priorizada de hipótesis.

**Debe cumplir:**
- [ ] Cada estrategia tiene: qué es, qué cambió, hipótesis de si queda edge y dónde
- [ ] Hay 2-3 hipótesis priorizadas y concretas (no genéricas), listas para diseñar la Etapa 2

## Cierre
_(al cerrar)_
