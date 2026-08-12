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
- [x] Conector de solo lectura — NotBank (L2 snapshot) — `code/cryptobot/.../notbank/NotBankConnector.java`. Host real (`api.notbank.exchange`) y formato (AlphaPoint: `GetInstruments` para resolver `InstrumentId`, `GetL2Snapshot` en filas de array posicional) confirmados a mano, no estaban completos en la documentación pública
- [x] Elegir el par líquido a usar como control — LTC_USDT (Poloniex) / LTCUSDT (NotBank) — ya teníamos referencia real de precio de la sesión de exploración manual, ~45,3-45,4
- [x] Calcular spread real cruzado (ask/bid, ambas direcciones) y compararlo contra "último precio" de cada exchange — hecho en `Main.java`
- [x] Documentar el resultado

## Sprint Review
**Cómo probar:** `mvn exec:java` en `code/cryptobot/` — trae los dos books en vivo y muestra el spread cruzado en las dos direcciones.

**Debe cumplir:**
- [x] El spread calculado usa ask/bid ejecutable, no último precio
- [x] El resultado en el par líquido de control es coherente con lo esperado (sin arbitraje obvio) — si no lo es, se investiga la herramienta antes de seguir

## Cierre

Quedó funcionando de punta a punta: `PoloniexConnector` y `NotBankConnector`, los dos contra APIs reales (no mockeadas), cada uno con un test que parsea una respuesta real capturada. `Main` trae LTC/USDT de los dos exchanges y calcula el spread cruzado en ambas direcciones — resultado: **sin arbitraje en ninguna de las dos** (-0,31% y -0,28% respectivamente), consistente con todo lo que veníamos encontrando a mano en la sesión de exploración manual. La herramienta confirma lo que ya sabíamos por otro camino — exactamente la validación que buscaba este sprint antes de confiar en ella para pares chicos.

Lo más valioso no estaba en el alcance original: el contrato real de la API de NotBank (host, formato de `GetL2Snapshot`) no está completo en su documentación pública — hubo que confirmarlo probando en vivo. Quedó documentado en `entorno.md` para no tener que redescubrirlo.

Pendiente / siguiente paso: sumar BudaPRO y YoBit (mismo patrón, `ExchangeConnector` nuevo cada uno); expandir de LTC/USDT a pares chicos/regionales, que es donde vive la hipótesis real de la 01; y empezar a restar fees reales al spread bruto, no solo mostrarlo crudo.
