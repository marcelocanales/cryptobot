---
sprint: 27
titulo: "Guardia de implausibilidad en SpreadWatcher (paridad con CrossTriangleWatcher)"
etapa: 2
---

# Sprint 0027 — Guardia de implausibilidad en SpreadWatcher

## Objetivo
`CrossTriangleWatcher`/`CrossTriangleCheck` tienen, desde el Sprint 0012, un guardia que marca `IMPLAUSIBLE` cualquier resultado con `|bruto| > 50%` (choque de ticker entre exchanges, no arbitraje real). `SpreadWatcher` nunca lo recibió. La revisión completa de la corrida nocturna del 2026-08-13 (8h20m, ~1,2M filas) confirmó que esto no era un caso aislado: 13 de las ~33 combinaciones que llegó a marcar esa noche eran choques de ticker (TAO, BOB, XCN, VELO, BABYDOGE, MOG, TRUMP, US, DGB, ARB, TIA, PIVX, XCH), diluyendo la señal real (ZEC) en una lista dominada por falsos positivos.

## Alcance
- `SpreadWatcher.writeDirection`: agrega `IMPLAUSIBLE_GROSS_PCT = 50` y la misma lógica de `CrossTriangleWatcher.writeTriangle`.
- _(Fuera de alcance: la solución de fondo — mapeo de identidad real de activo, no solo comparar el ticker — sigue en el backlog, sin cambios.)_

## Decisiones
- **Portar el guardia tal cual, sin modificarlo** — mismo umbral (50%), mismo criterio (marca, no descarta la fila), mismo nombre de constante y de flag (`IMPLAUSIBLE`) que `CrossTriangleWatcher` ya usa desde el Sprint 0012. No se evaluó un umbral distinto para `SpreadWatcher` — no hay ninguna razón medida para que el punto de corte sea diferente entre ambos watchers, y mantener el mismo número evita que dos partes del proyecto respondan distinto ante el mismo tipo de choque de ticker.
- **La impresión por consola y el conteo de "combinaciones marcadas" pasan a excluir los casos implausibles** — antes de este cambio, un choque de ticker con neto positivo (siempre lo es, dado lo absurdo del bruto) contaba como señal y se imprimía en la consola igual que una oportunidad real; ahora sigue escribiéndose en el CSV (con su `gross_pct` real, para no perder el dato), pero ya no se cuenta ni se imprime como oportunidad.

## Tareas
- [x] `SpreadWatcher.writeDirection`: guardia de implausibilidad
- [x] `mvn test` en verde (sin tests nuevos — mismo criterio que `CrossTriangleWatcher`, que tampoco tiene test dedicado; los watchers son mains delgados, no lógica pura)
- [x] Verificación en vivo: 2 ciclos reales de `SpreadWatcher`

## Sprint Review
**Cómo probar:** `mvn test`; `mvn exec:java -Dexec.mainClass=com.cryptobot.watch.SpreadWatcher` un par de ciclos y confirmar contra el CSV resultante.

**Debe cumplir:**
- [x] Un choque de ticker conocido (TAO/BTC, BOB/USDT, TRUMP/USDT, XCN/USDT) sale con `flag=IMPLAUSIBLE`, no `REVISAR`
- [x] Una señal real conocida (ZEC/BTC, ZEC/USDT, ETC/BTC) sigue saliendo `REVISAR` sin cambios
- [x] El `gross_pct` real se sigue escribiendo en el CSV aunque el flag sea `IMPLAUSIBLE` — no se pierde el dato, solo se lo deja de contar como oportunidad

## Hallazgos
- **Confirmado en vivo, no solo en teoría**: en la corrida corta de verificación, TAO/BTC salió con `gross_pct` de 115.953,31% (YoBit→CoinEx) y -99,996% (CoinEx→YoBit) — ambos `IMPLAUSIBLE` ahora. BOB/USDT 93.634,34%, TRUMP/USDT ~1.951-1.953%, XCN/USDT 62,86% — los 4 casos conocidos de la corrida nocturna se reprodujeron igual en la corrida corta de esta verificación (el choque de ticker es estructural, no depende del momento del día).
- **La señal real no se vio afectada**: ZEC/BTC (3 pares), ZEC/USDT (2 pares) y ETC/BTC (3 pares, aunque parte de esas combinaciones ya se sabe — ver corrida nocturna completa — que son books congelados de YoBit, un problema distinto) siguieron marcando `REVISAR` exactamente igual que antes del cambio.

## Cierre
Guardia en paridad entre los dos watchers que podían sufrir el mismo problema. Queda pendiente, sin tocar en este sprint: la solución de fondo de identidad de activo (backlog), y el hallazgo separado de que algunas combinaciones con 100% de consistencia son books congelados de YoBit (no choques de ticker — ver el ítem de backlog sobre el dashboard sin cruce de `stale`, Sprint 0026). Commiteado localmente, sin push — a la espera de que Marcelo lo autorice, mismo criterio que el Sprint 0026.
