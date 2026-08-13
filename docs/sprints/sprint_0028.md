---
sprint: 28
titulo: "Conectar Binance (7mo exchange) — fase 1 de la Etapa 3"
etapa: 3
---

# Sprint 0028 — Conectar Binance (7mo exchange) — fase 1 de la Etapa 3

## Objetivo
Marcelo pidió llevar la Etapa 2 a factibilidad técnica: intentar el arbitraje real con un monto mínimo (10 USD o menos). El plan completo de 4 fases vive en [etapa3-plan.md](../etapa3-plan.md) — este sprint es la fase 1, conectar el mejor candidato de exchange de solo lectura, sin ninguna cuenta ni API key de trading todavía.

## Alcance
- `com.cryptobot.marketdata.binance.BinanceConnector` (nuevo): `fetchOrderBook()`/`fetchMarkets()`, mismo patrón que los otros 6 conectores.
- Wiring: `ExchangeFees`, `TrackedAssets.all(...)`, `SpreadWatcher`, `OverlapCheck`.
- _(Fuera de alcance: cualquier API key de trading, cualquier movimiento de capital, paper trading — fases 2-4 del plan, todavía sin ejecutar.)_

## Decisiones
- **Binance, no Bitfinex/CoinEx** (lo que se venía conversando) — motivado por 3 hallazgos verificados en vivo el mismo día: NotBank no tiene ZEC listado (confirmado contra su API), Buda/NotBank sin señal en toda la corrida nocturna completa, y Binance (nunca conectado) mostró el spread más grande visto hasta ahora contra Poloniex en ZEC/USDT — con la ventaja práctica de que Marcelo ya tiene cuenta habilitada ahí, sin esperar a renovar KYC como haría falta en Bitfinex.
- **Fee de Binance confirmada contra su página oficial** (`binance.com/en/fee/schedule`), 0,10% taker tier VIP 0 — no se asume el descuento de 0,075% con BNB activo, mismo criterio de "no asumir el mejor caso" que el proyecto usa siempre (ej. NotBank tier base).
- **Manejo de error más simple que CoinEx/YoBit** — Binance responde HTTP no-200 para símbolo inválido (confirmado en vivo: 400 con `{"code":-1121,"msg":"Invalid symbol."}`), no hace falta el patrón de chequear un campo `code`/`success` con HTTP 200.
- **Sin timestamp propio en el book** — mismo tratamiento que `BudaConnector`: se usa el momento de la respuesta.

## Tareas
- [x] `BinanceConnector.fetchOrderBook()`/`fetchMarkets()` + tests (JSON real capturado: depth de BTCUSDT, exchangeInfo recortado, símbolo inválido)
- [x] `ExchangeFees` — entrada de Binance + test
- [x] `TrackedAssets.all(...)`, `SpreadWatcher`, `OverlapCheck` actualizados
- [x] `mvn test` en verde (87 tests — incluye arreglar `unknownExchangeIsAnError`, que usaba "Binance" como ejemplo de exchange no conectado)
- [x] Verificación en vivo: `OverlapCheck` con los 7 exchanges
- [x] Docs: `docs/entorno.md`, `docs/arquitectura.md`, este sprint

## Sprint Review
**Cómo probar:** `mvn test`; `mvn exec:java -Dexec.mainClass=com.cryptobot.OverlapCheck` y confirmar que Binance aparece como venue.

**Debe cumplir:**
- [x] `OverlapCheck` corre con los 7 exchanges sin errores de compilación ni de wiring
- [x] `ZEC/USDT` aparece con Binance como una de las combinaciones evaluadas
- [x] El universo de activos crece respecto a los 6 exchanges anteriores

## Hallazgos
- **`OverlapCheck` pasó de 450 a 688 activos** — el salto más grande de cualquier exchange conectado hasta ahora (Binance solo tiene 1.378 mercados activos en `TRADING`, más que todos los otros 6 exchanges juntos).
- **Confirmado en vivo, no solo en la conversación de ayer**: `ZEC/USDT` comprando en Poloniex y vendiendo en Binance dio bruto 7,52%, neto 7,22% — muy en línea con los 7,02% (CoinEx) y 7,19% (Bitfinex) vistos en la misma corrida. No es un caso aislado de Binance ni una foto rara: es la misma dislocación de Poloniex en ZEC que ya se documentó en [veredictos-etapa2.md](../veredictos-etapa2.md), ahora confirmada contra un cuarto exchange distinto.
- La respuesta de `GET /api/v3/exchangeInfo` de Binance es sustancialmente más grande que la de cualquier otro exchange conectado (~17,5MB) — no causó ningún problema práctico (se pide una sola vez al arranque), pero es un dato a tener presente si en algún momento el arranque de `SpreadWatcher`/`OverlapCheck` se siente lento.

## Cierre
Fase 1 de la Etapa 3 cerrada. Sigue [etapa3-plan.md](../etapa3-plan.md), fase 2: dejar `SpreadWatcher` corriendo con los 7 exchanges unas horas para confirmar que el spread de Binance/Poloniex en ZEC se sostiene en el tiempo, no fue una foto de un momento raro — mismo principio de nunca confiar en una sola medición que guio toda la Etapa 2. Commiteado localmente en `sprint/0028-conectar-binance`, sin push — a la espera de que Marcelo lo autorice, mismo criterio que los Sprints 0026/0027.
