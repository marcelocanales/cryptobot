---
sprint: 21
titulo: "Conectar CoinEx (5to exchange, hipótesis 01)"
etapa: 2
---

# Sprint 0021 — Conectar CoinEx (5to exchange, hipótesis 01)

## Objetivo
El backlog pedía investigar un 5to exchange candidato con el perfil "chico/regional, muchos pares, poca vigilancia sofisticada" que el catálogo señala como prometedor — sin nombres confirmados, solo puntos de partida (Latoken, CoinEx, Bitrue). Este sprint investiga los 3, elige uno, confirma que Marcelo puede efectivamente operar ahí, y lo conecta.

## Alcance
- Research de Latoken/CoinEx/Bitrue: API pública real, actividad genuina, disponibilidad para Chile.
- `com.cryptobot.marketdata.coinex.CoinExConnector` — mismo patrón que los otros 4 conectores.
- `ExchangeFees` + `TrackedAssets.all(...)` (5to parámetro) + `OverlapCheck`/`SpreadWatcher` actualizados.
- _(Fuera de alcance: futuros/funding de CoinEx, CoinEx en triangular, CoinEx en cash-and-carry — ver Decisiones.)_

## Decisiones
- **CoinEx elegido sobre Bitrue y Latoken.** Los tres tienen API pública real y trust score aceptable en 2 de los 3 (CoinEx y Bitrue, 7/10; Latoken 3/10 con banderas rojas concretas en regulación/ciberseguridad/reservas — descartado). Entre CoinEx y Bitrue (mismo trust score, mismo overlap de 20/22 activos), CoinEx gana porque su API de **futuros también es 100% pública, incluido `funding-rate`, sin API key** — Bitrue exige registrarse para esa parte. Eso importa porque la hipótesis 05 (funding rate cross-exchange) es la #3 priorizada del catálogo desde el Sprint 0001 y el proyecto nunca pudo probarla por falta de un segundo exchange con perpetuos accesibles.
- **Disponibilidad para Chile, verificada antes de conectar — pedido explícito de Marcelo.** La API pública de mercado no necesita cuenta, pero eso no confirma que se pueda operar ahí a futuro (Etapa 3+). Confirmado contra la fuente oficial de CoinEx: Chile no está en su lista de países restringidos (EE.UU., Canadá, China continental, Hong Kong, UE, Reino Unido, Suiza, Kazajistán, Corea del Norte, Irán, Cuba). Mismo chequeo hecho para Bitrue (tampoco restringido), por las dudas.
- **Alcance de esta sprint = mismo tamaño que conectar Buda/YoBit en el Sprint 0005: conector + hipótesis 01, nada más.** Futuros/funding, triangular y cash-and-carry quedan como backlog explícito — cada uno es una construcción propia, no "un exchange más" en algo que ya existe.
- **Fee de CoinEx: 0,20% plano, documentado como aproximación.** A diferencia de los otros 4 exchanges, CoinEx varía la fee **por mercado individual** (751 mercados al 0,30%, 242 al 0,20%, 6 al 0,10%) — no por exchange ni por tipo de moneda de cotización. Confirmado en vivo que los majors que efectivamente se usan (BTCUSDT, ETHUSDT, LTCUSDT) están los tres al 0,20%, así que se usa ese valor — no es exacto para un par exótico, mismo tratamiento que otras fees imperfectas ya documentadas (tier de NotBank, fee del perp de Poloniex).

## Tareas
- [x] Research Latoken/CoinEx/Bitrue (API pública, trust score, overlap, futuros) — en background, ver `docs/roadmap.md`
- [x] Confirmar disponibilidad para Chile en CoinEx y Bitrue contra sus fuentes oficiales
- [x] `CoinExConnector` + `CoinExConnectorTest` (parseOrderBook con `code != 0` como error, parseMarkets filtrando `status`)
- [x] `ExchangeFees` + test
- [x] `TrackedAssets.all(...)` + `OverlapCheck`/`SpreadWatcher` actualizados
- [x] `mvn test` en verde
- [x] Verificación en vivo: `OverlapCheck`, confirmado CoinEx participa, medido el número real de activos

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.OverlapCheck`.

**Debe cumplir:**
- [x] CoinEx aparece en las comparaciones de `OverlapCheck` para los activos donde se espera overlap
- [x] Los 11 activos originales del Sprint 0007 siguen presentes
- [x] Ningún error nuevo — solo el patrón ya conocido de YoBit (pares sin liquidez bid)

## Hallazgos
- **Medido, no proyectado:** `OverlapCheck` pasó de 67 a **436 activos** con 2+ exchanges — CoinEx (999 mercados) cruza el umbral de "2+ exchanges" para muchos tickers que antes solo vivían en YoBit en solitario.
- Los 7 errores de la corrida en vivo fueron todos el patrón ya conocido de YoBit ("sin bids/asks esperados") — ninguno nuevo de CoinEx.

## Cierre
Con CoinEx, el proyecto pasa de 4 a 5 exchanges conectados, y la hipótesis 01 gana su mayor salto de cobertura hasta ahora (67 → 436 activos). Más importante: CoinEx deja la puerta abierta a la hipótesis 05 (funding rate cross-exchange) — la única priorizada del catálogo que seguía sin poder probarse — sin que este sprint la construya todavía.

Sigue pendiente: futuros/funding de CoinEx (habilita la 05), CoinEx en triangular (02/03) y en cash-and-carry (04), confirmar el tier real de NotBank, la fee del perpetuo de Poloniex, el rate limit real de cada exchange, ancla BTC/ETH en YoBit triangular, Buda en cash-and-carry con conversión de moneda — y la corrida nocturna, todavía no ejecutada.
