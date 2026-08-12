---
sprint: 7
titulo: "Monitoreo continuo de 4 exchanges, todas las combinaciones"
etapa: 2
---

# Sprint 0007 — Monitoreo continuo de 4 exchanges, todas las combinaciones

## Objetivo
Dejar de comparar exchanges de a pares elegidos a mano y correr **todas las combinaciones posibles** entre los 4 exchanges conectados, tanto en la foto única (`OverlapCheck`) como en la corrida continua (`SpreadWatcher`), para la estrategia 01 (spot cross-exchange).

## Alcance
- `TrackedAsset`/`Venue`/`TrackedAssets`: fuente única de qué activo cotiza en qué exchange, compartida por los dos programas — reemplaza las dos listas que hasta ahora vivían duplicadas y desalineadas (8 pares en `SpreadWatcher`, 11 en `OverlapCheck`).
- `NetSpread`: la cuenta de spread neto (duplicada entre los dos programas desde el Sprint 0006) se extrae a una función pura compartida.
- `SpreadWatcher` pasa de 2 exchanges fijos a N exchanges combinables; el CSV pasa de formato ancho a formato largo.
- `OverlapCheck` genera sus combinaciones automáticamente en vez de tenerlas hardcodeadas.
- _(Fuera de alcance, por pedido explícito: confirmar el fee real de NotBank —lo hace Marcelo directo en su cuenta—, slippage/modelo de expectativa, y sumar otras estrategias del catálogo — quedan para después.)_

## Decisiones
- **Un solo lugar para "qué activo vive en qué exchange".** Antes de esto, la lista de `SpreadWatcher` y la de `OverlapCheck` no eran el mismo conjunto — nunca se había notado porque nada obligaba a que coincidieran. `TrackedAssets` es ahora esa única fuente.
- **Verificar antes de armar la lista, no asumir.** Antes de escribir `TrackedAssets`, se confirmó en vivo (`GET /api/3/info` de YoBit) que AAVE/GRAM/XTZ **no** cotizan ahí — quedan solo en Poloniex/NotBank, igual que antes.
- **CSV de ancho a largo.** Con 2, 3 o 4 exchanges por activo según el caso, columnas fijas por exchange ya no generalizan. El nuevo formato (`timestamp,asset,buy_exchange,sell_exchange,buy_price,sell_price,gross_pct,fees_pct,net_pct,stale,flag,error`) tiene una fila por combinación×dirección×ciclo — más filas, pero cada una autocontenida y fácil de filtrar sin importar cuántos exchanges tenga el activo. Las corridas anteriores (formato ancho) quedan como están, no se migran.
- **Fetch una sola vez por venue, combinaciones en memoria.** Aunque un activo tenga 3 exchanges (3 combinaciones), cada exchange se consulta una sola vez por ciclo — las combinaciones se generan después, en memoria, no disparando más pedidos HTTP de los necesarios.

## Tareas
- [x] Confirmar en vivo qué activos cotiza YoBit (AAVE/GRAM/XTZ: no)
- [x] `TrackedAsset`/`Venue`/`TrackedAssets`
- [x] `NetSpread` + test
- [x] Reescribir `SpreadWatcher`: N exchanges, CSV largo, borrar `TrackedPair`
- [x] Reescribir `OverlapCheck`: combinaciones automáticas desde `TrackedAssets`
- [x] Verificación en vivo de ambos

## Hallazgos de la verificación en vivo

**`OverlapCheck` ahora compara Poloniex vs. NotBank directo** (algo que nunca se había chequeado ahí, solo en la corrida continua) — sin sorpresas, mismo patrón de siempre: sin arbitraje neto.

**AAVE y GRAM muestran gaps grandes entre Poloniex y NotBank** — AAVE hasta -20,6% bruto en una dirección, GRAM hasta -8,2% en la otra. No son oportunidades (los gaps son negativos en ambas direcciones, es decir hay divergencia de precio real entre los dos exchanges, no un puente comprable-vendible) — son evidencia de que al menos uno de los dos lados no está reflejando un precio confiable en estos pares de bajo volumen, consistente con lo ya documentado (errores de liquidez en AAVE al final de la corrida del Sprint 0004, sospecha de datos en GRAM desde la exploración manual). Queda como observación, no se investiga más a fondo en este sprint.

**XTZ en Poloniex sigue con un gap enorme contra NotBank** (-38% a -62% bruto) — el precio de Poloniex se movió levemente desde el Sprint 0004 (0,1220 ahora vs. 0,1200 entonces) pero sigue totalmente desalineado de NotBank (~0,196). Consistente con la conclusión del Sprint 0004: mercado abandonado, no una fuente de precio confiable.

**Verificación de `SpreadWatcher`:** una corrida corta (1 ciclo, ~12s con los 4 exchanges — sigue entrando cómodo en los 30s de intervalo) confirmó 6 filas para BTC/USDT (3 exchanges → 3 combinaciones × 2 direcciones), 12 columnas consistentes en todo el CSV, sin arbitraje neto en ningún caso.

## Sprint Review
**Cómo probar:** `mvn test` (incluye `NetSpreadTest`); `mvn compile exec:java -Dexec.mainClass=com.cryptobot.OverlapCheck` para ver todas las combinaciones en vivo; correr `SpreadWatcher` y confirmar el CSV en formato largo.

**Debe cumplir:**
- [x] `TrackedAssets` es la única fuente de qué activo vive en qué exchange, usada por ambos programas
- [x] Se generan automáticamente todas las combinaciones posibles por activo, no solo las cableadas a mano
- [x] Cada exchange se consulta una sola vez por ciclo, sin importar en cuántas combinaciones participe

## Cierre

Con esto, la superficie de búsqueda de la estrategia 01 quedó completa para los 4 exchanges conectados: todas las combinaciones posibles, en continuo, con spread neto. Sigue sin aparecer arbitraje — ya son 7 sprints seguidos sin una señal que sobreviva a fees. Lo nuevo esta vez no fue encontrar una oportunidad, sino confirmar que no había combinaciones sin revisar (Poloniex vs. NotBank directo, por ejemplo, nunca se había chequeado fuera de la corrida continua) — y encontrar dos observaciones de calidad de datos más (AAVE, GRAM) que valdría la pena investigar si se retoma el trabajo de integridad de datos.

Siguiente paso, tal como se conversó: dejar correr `SpreadWatcher` con los 4 exchanges un tramo más largo (no hace falta toda la noche necesariamente, pero sí más que unos minutos) para que el detector de precio congelado y el muestreo continuo tengan tiempo de decir algo que una foto no puede. Si esa corrida tampoco muestra nada, ahí sí conviene documentar un veredicto formal sobre la hipótesis 01 con estos 4 exchanges, y mirar si conviene sumar más exchanges o pasar a otra estrategia del catálogo (triangular, funding rate).
