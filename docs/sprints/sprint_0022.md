---
sprint: 22
titulo: "Fee real del perpetuo de Poloniex, confirmada (0,06%, no 0,075%)"
etapa: 2
---

# Sprint 0022 — Fee real del perpetuo de Poloniex, confirmada (0,06%, no 0,075%)

## Objetivo
Cerrar el último valor de fee del proyecto que no estaba confirmado contra una API real o una cuenta — `ExchangeFees.perpTakerFee("Poloniex")` venía de contenido de soporte del exchange (Sprint 0015), no de un endpoint público (no existe) ni de una cuenta real.

## Alcance
- Corregir `ExchangeFees.PERP_TAKER_FEE` con el valor real, confirmado.
- Actualizar el test y los valores esperados de `CashAndCarrySpreadTest` que dependían del valor viejo.
- Docs: `docs/entorno.md`, `docs/roadmap.md` (backlog resuelto).
- _(Sin cambios de arquitectura — es una corrección de dato, no de estructura; sin sección nueva en `arquitectura.md`, mismo criterio que el Sprint 0011.)_

## Decisiones
- **Fuente: la cuenta real de Marcelo en Poloniex** (captura de pantalla de "Trading Tier Status", VIP 0, USDT-M Perpetual Futures) — la mejor fuente posible, mejor que cualquier búsqueda web. Confirma **0,02% maker / 0,06% taker**, contra el 0,075% taker que se venía usando.
- **De paso, confirma también el spot**: la misma captura muestra 0,20% maker/taker en spot, tier VIP 0 — coincide exacto con lo que `ExchangeFees` ya tenía para Poloniex, sin cambios ahí.
- **El breakeven de cash-and-carry mejora con este ajuste** (fee de entrada más baja): en el caso de test ya existente, pasa de 3,75 a 3 períodos para recuperar la entrada — antes se sobreestimaba el costo, no al revés.

## Tareas
- [x] `ExchangeFees.PERP_TAKER_FEE["Poloniex"]` = 0,0006 (antes 0,00075)
- [x] `ExchangeFeesTest` actualizado
- [x] `CashAndCarrySpreadTest.picksTheSpotExchangeWithBestNetCostNotJustBestRawPrice` — recalculados fees de entrada (0,26%, antes 0,275%) y breakeven (3, antes 3,75 períodos)
- [x] `mvn test` en verde
- [x] `docs/entorno.md`, `docs/roadmap.md` actualizados

## Sprint Review
**Cómo probar:** `mvn test` — en particular `ExchangeFeesTest`/`CashAndCarrySpreadTest`.

**Debe cumplir:**
- [x] `ExchangeFees.perpTakerFee("Poloniex")` devuelve 0,0006
- [x] Los tests que dependen de ese valor (breakeven, fees de entrada) reflejan el número correcto, no solo "pasan"

## Cierre
Con esto, todos los valores de fee del proyecto están confirmados contra una API real o la cuenta del propio Marcelo — no queda ningún supuesto de fee sin verificar. Cierra el housekeeping que quedaba pendiente junto con el tier de NotBank (confirmado sin cambios de código, commit directo a master).

Sigue pendiente: futuros/funding de CoinEx (habilita la hipótesis 05), CoinEx en triangular y en cash-and-carry, el rate limit real de cada exchange, ancla BTC/ETH en YoBit triangular, Buda en cash-and-carry con conversión de moneda — y la corrida nocturna con las 5 hipótesis juntas, todavía no ejecutada.
