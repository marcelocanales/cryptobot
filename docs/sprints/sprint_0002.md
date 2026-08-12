---
sprint: 2
titulo: "Primer conector de solo lectura y validación de spread real"
etapa: 2
---

# Sprint 0002 — Primer conector de solo lectura y validación de spread real

## Objetivo
Construir el primer conector de solo lectura contra las APIs públicas de Poloniex y NotBank, y calcular el spread real (ask/bid ejecutable, no "último precio") entre los dos en un par líquido conocido — para validar que la herramienta calcula bien antes de expandir a pares chicos, más exchanges, u otras hipótesis.

## Alcance
- Cliente de solo lectura para:
  - Poloniex: `GET https://api.poloniex.com/markets/{symbol}/orderBook` (pública, sin auth).
  - NotBank: `GetL2Snapshot` (REST, público).
- Un par líquido presente en ambos (candidato: BTC/USDT o LTC/USDT — a confirmar al iniciar).
- Calcular el spread real cruzado (mejor ask de un lado vs. mejor bid del otro, en las dos direcciones) — el mismo cálculo que se hizo a mano toda la sesión anterior, ahora programado.
- Validar: en un par líquido y ya conocido, el resultado debería dar cerca de cero (mercado eficiente) — si no da eso, el problema es la herramienta, no el mercado.
- _(Fuera de alcance: BudaPRO y YoBit — quedan para un sprint siguiente. Pares chicos/ilíquidos. Cualquier tipo de ejecución o cuenta con permisos de trading — sigue siendo solo lectura.)_

## Decisiones
Stack/lenguaje: por definir al iniciar, just-in-time — ver [metodologia.md](../metodologia.md).
- _(pendiente)_

## Tareas
- [ ] Conector de solo lectura — Poloniex (order book)
- [ ] Conector de solo lectura — NotBank (L2 snapshot)
- [ ] Elegir el par líquido a usar como control
- [ ] Calcular spread real cruzado (ask/bid, ambas direcciones) y compararlo contra "último precio" de cada exchange
- [ ] Documentar el resultado

## Sprint Review
**Cómo probar:** correr el conector contra los dos exchanges en el par elegido y mostrar el spread real calculado.

**Debe cumplir:**
- [ ] El spread calculado usa ask/bid ejecutable, no último precio
- [ ] El resultado en el par líquido de control es coherente con lo esperado (cerca de cero, sin arbitraje obvio) — si no lo es, se investiga la herramienta antes de seguir

## Cierre
_(al cerrar)_ Qué quedó funcionando · qué quedó pendiente o se aprendió · siguiente paso.
