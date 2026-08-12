---
sprint: 5
titulo: "Conectores BudaPRO y YoBit + comparación en vivo de 4 exchanges"
etapa: 2
---

# Sprint 0005 — Conectores BudaPRO y YoBit + comparación en vivo de 4 exchanges

## Objetivo
Sumar los otros dos exchanges de la lista original (Buda y YoBit) al análisis de viabilidad, con el mismo estándar de rigor que Poloniex/NotBank: conectores reales, verificados en vivo, y una comparación real de spreads — no supuestos.

## Alcance
- `BudaConnector` y `YobitConnector`, mismo patrón `ExchangeConnector` que los dos existentes.
- `OverlapCheck`: comparación en vivo (snapshot único) entre los 4 exchanges, en los pares donde overlapean sin necesitar conversión de moneda.
- _(Fuera de alcance: integrar Buda/YoBit a `SpreadWatcher` para corrida continua — queda como siguiente decisión, ver Cierre.)_

## Decisiones
- **Símbolos distintos por exchange, como ya era el caso.** Buda: `btc-clp` (minúscula, guion). YoBit: `btc_usdt` (minúscula, guion bajo). Ninguno usa el formato de Poloniex (`BTC_USDT`) ni el de NotBank (`BTCUSDT`).
- **YoBit manda precios como número JSON, no string** — a diferencia de los otros tres exchanges. Se activó `DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS` en el `ObjectMapper` del conector para que Jackson arme el `BigDecimal` directo desde el texto del token, sin pasar por `double` (que ya perdería precisión antes de llegar a `BigDecimal`).
- **Qué pares comparar, sin inventar conversión de moneda.** Buda casi no tiene pares en USDT para altcoins (su universo es BTC/ETH/BCH/LTC/USDC/USDT/SOL, cotizado mayormente en CLP/COP/PEN) — en vez de forzar una conversión vía USDT-CLP (que agregaría un supuesto y una fuente más de error), se usaron los pares que Buda comparte *directo* con NotBank: BTC-CLP, ETH-CLP, LTC-BTC. YoBit sí cotiza BTC/ETH/LTC/DOGE/SHIB directo en USDT, así que esos se comparan tal cual contra Poloniex y NotBank.
- **Filtrar por nocional también en `OverlapCheck`**, con el mismo criterio que `SpreadWatcher` (Sprint 0003) pero un umbral por moneda de cotización, ya que acá hay tres monedas distintas (USDT, CLP, BTC) en vez de una sola.

## Tareas
- [x] `BudaConnector` + test con respuesta real capturada
- [x] `YobitConnector` + test con respuesta real capturada (incluye el caso de símbolo inválido con HTTP 200)
- [x] `OverlapCheck`: comparación en vivo de 11 pares entre los 4 exchanges
- [x] Investigar y corregir un falso positivo real encontrado en la propia verificación (ver hallazgos)

## Hallazgos de la comparación en vivo

**Sin arbitraje real, otra vez.** De los 11 pares × 2 direcciones (22 chequeos), solo BTC/USDT (YoBit vs. Poloniex, YoBit vs. NotBank) mostró spread bruto positivo — ~0,06–0,08%. No alcanza a cubrir ni una sola fee de un lado (YoBit cobra 0,20%, Poloniex 0,20%, NotBank sin confirmar todavía).

**SHIB/USDT en YoBit: mismo patrón que XTZ en Poloniex.** El primer corrida, sin filtrar por nocional, mostró un "spread bruto" de 0,69% contra Poloniex. Verificado a mano: el mejor bid de YoBit tenía una cantidad de ~1.547 SHIB — valor nocional ≈ USD 0,007. Polvo, no liquidez. Con el filtro de nocional mínimo puesto, ambas direcciones quedan sin liquidez suficiente — el "spread" desaparece. Confirma que la técnica de filtrado (nacida en el Sprint 0003) es reusable y hace falta aplicarla en cualquier lugar donde se compare top-of-book, no solo en `SpreadWatcher`.

**LTC-BTC en Buda es un mercado casi muerto.** El book propio de Buda para ese par tiene un spread interno de ~30% (mejor bid 0,0006 / mejor ask 0,0009) y niveles sin sentido mezclados (una orden a precio `975696839.0` por 0,2 LTC — claramente basura o un error de fat-finger nunca cancelado). Comparado contra el book de NotBank, mucho más ajustado (~0,5% de spread interno), la diferencia cruzada llega a -30%, pero es un artefacto de que un lado no es un mercado real, no una oportunidad.

**Buda cobra fees más altas.** Confirmado en `GET /markets`: 0,80% taker / 0,40% maker — el doble o más que Poloniex/NotBank/YoBit (todos ~0,20%). Otro motivo más para que un spread bruto chico no alcance a ser neto positivo en Buda.

## Sprint Review
**Cómo probar:** `mvn test` (incluye `BudaConnectorTest` y `YobitConnectorTest`); correr `mvn compile exec:java -Dexec.mainClass=com.cryptobot.OverlapCheck` para ver la comparación en vivo de los 11 pares.

**Debe cumplir:**
- [x] Ambos conectores parsean una respuesta real capturada, con el mismo estándar de tests que Poloniex/NotBank
- [x] `YobitConnector` no pierde precisión al parsear precios (verificado con un valor de 8 decimales)
- [x] `OverlapCheck` filtra por nocional mínimo antes de reportar un spread como señal

## Cierre

Quedaron dos conectores reales y verificados más, con el mismo nivel de rigor que Poloniex/NotBank — y, otra vez, la comparación en vivo hizo su trabajo: encontró un falso positivo (SHIB en YoBit) antes de que pareciera una oportunidad real, y confirmó con datos (no con la sospecha "YoBit tiene mala fama") que el problema concreto de esta corrida es un mercado (LTC-BTC en Buda) casi sin liquidez propia.

Con los 4 exchanges de la lista original ahora conectados y comparados al menos una vez, no apareció arbitraje ejecutable en ningún par — el patrón se repite desde el Sprint 0002.

Pendiente / siguiente paso: decidir si conviene sumar Buda/YoBit a `SpreadWatcher` para una corrida continua de 4 exchanges (más superficie, mismo detector de staleness y filtro de nocional ya construidos), o si primero conviene calcular fees reales netas sobre el spread bruto — hasta ahora todos los "posible spread bruto" mostrados son antes de fees, y varios claramente no la cubrirían.
