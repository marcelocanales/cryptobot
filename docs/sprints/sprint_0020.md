---
sprint: 20
titulo: "Cash-and-carry: sumar YoBit como candidato de spot"
etapa: 2
---

# Sprint 0020 — Cash-and-carry: sumar YoBit como candidato de spot (Buda queda fuera, documentado por qué)

## Objetivo
El backlog decía "sumar Buda y YoBit como candidatos de spot en cash-and-carry... cambio chico... mejora marginal esperada". Medido en vivo antes de tocar código, esa premisa resultó parcialmente equivocada — ver Decisiones. Este sprint suma lo que sí es correcto y seguro sumar ahora: YoBit.

## Alcance
- `com.cryptobot.funding.CashAndCarryCandidates`: generaliza el descubrimiento de candidatos de spot, hoy hardcodeado a exactamente Poloniex + NotBank.
- `CashAndCarrySpread.bookKey`.
- `CashAndCarryCheck`/`CashAndCarryWatcher` reescritos para usar el descubrimiento generalizado, sumando YoBit.
- _(Fuera de alcance: Buda — ver Decisiones.)_

## Decisiones
- **Buda queda fuera de este sprint — medido, no asumido.** De los 18 perpetuos de Poloniex, Buda no cubre **ninguno** en USDT (su universo es CLP/COP/PEN, confirmado también en el javadoc de `BudaConnector` desde el Sprint 0005) — sumarlo bajo el mismo filtro `quote == "USDT"` que ya usa el diseño no agrega nada. Sus mercados CLP sí cubrirían 5 activos (BTC, ETH, LTC, BCH, SOL), pero `CashAndCarrySpread.evaluate` calcula el basis restando directo `perpBid.price() - spotAskPrice`, asumiendo la misma moneda en ambos lados (USDT, porque los perpetuos de Poloniex son siempre `_USDT_PERP`) — un spot en CLP (~30.000.000 por BTC) sin convertir produce un basis sin sentido, y además rompe en silencio la comparación "mejor precio neto entre candidatos" (mezcla escalas de moneda distintas en el mismo `compareTo`). Habilitar Buda bien requiere una conversión CLP→USDT en vivo — una feature distinta y más grande, no el mismo "cambio chico" que YoBit. Queda en el backlog con esa razón explícita.
- **YoBit sí es un cambio chico y seguro**: todos sus mercados relevantes son USDT, mismas unidades que Poloniex/NotBank — cero riesgo de mezcla de escalas.
- **`CashAndCarryCandidates` reusa `CrossVenue`** (de `marketdata`, Sprint 0012/0017) en vez de inventar un tipo nuevo — ya tenía 2 consumidores reales (`CrossTriangle*`, `TrackedAssets`), este es el 3ro, y evita duplicar la lookup de connector-por-nombre-de-exchange.

## Tareas
- [x] `CashAndCarryCandidates` + `CashAndCarryCandidatesTest` (4 casos: agrupa venues USDT de un activo, activo sin ningún venue USDT queda excluido, un venue en CLP no cuenta, símbolo de perp con prefijo numérico se despoja bien)
- [x] `CashAndCarrySpread.bookKey`
- [x] `CashAndCarryCheck`/`CashAndCarryWatcher` reescritos
- [x] `mvn test` en verde
- [x] Verificación en vivo: `CashAndCarryCheck`, confirmado BTC/DOGE/ETH/LTC/TRX/XRP con 3 venues (Poloniex, NotBank, YoBit), el resto sin cambios

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.CashAndCarryCheck`.

**Debe cumplir:**
- [x] Los 6 activos medidos (BTC, DOGE, ETH, LTC, TRX, XRP) tienen YoBit como candidato adicional
- [x] Ningún otro activo cambia de candidatos
- [x] `CashAndCarrySpread.evaluate` sigue sin tocar — la lógica de negocio no cambió, solo de dónde vienen los candidatos

## Cierre
Hipótesis 04 gana más competencia de precio en el lado spot para los activos donde YoBit realmente participa, sin ningún riesgo de mezcla de monedas. Buda queda como backlog explícito y mejor especificado ("requiere conversión CLP→USDT en vivo") en vez de repetir la subestimación original del backlog.

Sigue pendiente: confirmar el tier real de NotBank, la fee del perpetuo de Poloniex, el rate limit real de cada exchange, ancla BTC/ETH en YoBit triangular, investigar un 5to exchange, Buda en cash-and-carry con conversión de moneda — y la corrida nocturna, todavía no ejecutada.
