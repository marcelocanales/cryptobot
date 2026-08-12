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
- **Java 21 + Maven**, sin framework (nada de Spring Boot todavía — no hay ceremonia que justifique para "pegarle a dos APIs y comparar"). Zona de confort de Marcelo. Detalle y alternativas consideradas en [entorno.md](../entorno.md).
- **Sin ccxt por ahora.** Existe y tiene soporte real en Java, pero no está publicado en Maven Central — se instala compilando desde fuente con Gradle. Para dos endpoints públicos simples, escribirlos a mano (HttpClient + Jackson) es menos fricción. Se puede reconsiderar si se suman muchos más exchanges.
- **`BigDecimal`, nunca `double`**, para precios y cantidades — es plata, no hay margen para error de precisión de punto flotante.

## Tareas
- [x] Conector de solo lectura — Poloniex (order book) — `code/cryptobot/.../poloniex/PoloniexConnector.java`, verificado contra la API real
- [ ] Conector de solo lectura — NotBank (L2 snapshot) — contrato de API todavía sin confirmar del todo (parece formato tipo AlphaPoint, con `OMSId`/`InstrumentId`); investigar antes de escribirlo
- [x] Elegir el par líquido a usar como control — LTC_USDT (ya teníamos referencia real de precio de la sesión de exploración manual, ~45,3-45,4)
- [ ] Calcular spread real cruzado (ask/bid, ambas direcciones) y compararlo contra "último precio" de cada exchange — pendiente hasta tener NotBank
- [ ] Documentar el resultado

## Sprint Review
**Cómo probar:** correr el conector contra los dos exchanges en el par elegido y mostrar el spread real calculado.

**Debe cumplir:**
- [ ] El spread calculado usa ask/bid ejecutable, no último precio
- [ ] El resultado en el par líquido de control es coherente con lo esperado (cerca de cero, sin arbitraje obvio) — si no lo es, se investiga la herramienta antes de seguir

## Cierre
_(al cerrar)_ Qué quedó funcionando · qué quedó pendiente o se aprendió · siguiente paso.
