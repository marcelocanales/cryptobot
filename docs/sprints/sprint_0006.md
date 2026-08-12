---
sprint: 6
titulo: "Spread neto, no bruto — restar fees reales"
etapa: 2
---

# Sprint 0006 — Spread neto, no bruto — restar fees reales

## Objetivo
Cerrar una brecha abierta desde el cierre del Sprint 0002 ("empezar a restar fees reales al spread bruto, no solo mostrarlo crudo"): que el propio programa calcule el spread **neto de fees**, en vez de que cada sprint se repita el mismo paso manual de mirar el número bruto y descartarlo a ojo.

## Alcance
- `ExchangeFees`: fee de taker real por exchange (Poloniex, NotBank, Buda, YoBit).
- `OverlapCheck` y `SpreadWatcher` restan la fee de ambas patas al spread bruto antes de decidir si algo es "interesante".
- _(Fuera de alcance: slippage y modelo de expectativa — quedan en el backlog técnico, son un paso más allá de esto.)_

## Decisiones
- **Solo fee de taker, no maker.** El modelo de ejecución asumido en todo el proyecto sigue siendo taker-taker (dos órdenes de mercado, una por pata) — ver backlog "Ejecución maker + taker" en el roadmap. Mientras ese modelo no cambie, la fee de maker no aplica.
- **Suma simple de las dos fees, no compuesta.** `neto% = bruto% - fee_compra% - fee_venta%`. Es una aproximación (las fees se cobran sobre el monto de cada pata, no exactamente sobre el mismo % restando linealmente), pero es conservadora y transparente — alcanza para el objetivo de esta etapa (descartar ruido), no hace falta más precisión todavía.
- **Fees confirmadas contra fuente real, no inventadas:**
  - Poloniex: 0,20% taker (documentado en Sprint 0002).
  - Buda: 0,80% taker (confirmado en vivo, `GET /markets`, campo `taker_fee`).
  - YoBit: 0,20% taker (confirmado en vivo, `GET /api/3/info`, campo `fee`).
  - **NotBank: sin confirmar.** La página pública de tarifas (`notbank.exchange/es-cl/tarifas`) carga la tabla vía JavaScript y no expuso el tier base al consultarla. Fuentes de terceros se contradicen fuerte: entre 0,08% y 0,6% según cuál se lea. Se usa **0,60% (el extremo más alto reportado) como estimación conservadora** hasta que Marcelo confirme el tier real desde su cuenta — mejor sobreestimar la fee (y quedarse corto de oportunidades reales) que subestimarla (y perseguir un "arbitraje" que en realidad pierde plata).

## Tareas
- [x] `ExchangeFees` + test
- [x] `OverlapCheck`: verdict pasa de "posible spread bruto" a bruto/fees/neto explícitos
- [x] `SpreadWatcher`: nuevas columnas `*_net_pct` en el CSV, flag `REVISAR` dispara por neto positivo
- [x] Verificación en vivo de ambos (`OverlapCheck` y una corrida corta de `SpreadWatcher`)
- [ ] Confirmar el fee real de NotBank con Marcelo y actualizar `ExchangeFees`/`entorno.md`

## Sprint Review
**Cómo probar:** `mvn test` (incluye `ExchangeFeesTest`); `mvn compile exec:java -Dexec.mainClass=com.cryptobot.OverlapCheck` para ver bruto/fees/neto en los 11 pares; correr `SpreadWatcher` y confirmar las columnas `*_net_pct` en el CSV.

**Debe cumplir:**
- [x] El spread neto se calcula restando la fee real de ambas patas, no un número inventado
- [x] `REVISAR` en `SpreadWatcher` se dispara por neto positivo, no por bruto positivo
- [x] La fee de NotBank, al no estar confirmada, queda marcada como estimación — no se presenta como dato verificado

## Cierre

Con esto, cada corrida (`OverlapCheck` o `SpreadWatcher`) ya dice si algo sobrevive a fees, sin depender de que alguien lo revise a mano leyendo el CSV — que es exactamente lo que se venía haciendo, sprint tras sprint, desde que apareció el primer "posible spread bruto" en el Sprint 0002. Con la corrida de 11 pares del Sprint 0005 recalculada en neto, ningún caso quedó positivo — el margen bruto más alto que había (BTC/USDT, YoBit vs. Poloniex, ~0,08%) no alcanza a cubrir ni una fee de 0,20%, mucho menos dos.

Quedó una tarea abierta y explícitamente marcada como tal: la fee real de NotBank. El valor usado (0,60%) es conservador a propósito — no cambia la conclusión de que hoy no hay arbitraje neto en ningún par medido, porque una fee más baja en NotBank correría el número en la dirección de "menos pérdida", no de "arbitraje real" (los spreads brutos de por sí ya eran mayormente negativos).

Siguiente paso: con el spread neto ya resuelto, retomar la pregunta que quedó abierta al cerrar el Sprint 0005 — sumar Buda/YoBit a `SpreadWatcher` para una corrida continua de 4 exchanges, ahora con una lectura confiable de entrada (ya no hace falta descartar nada a mano).
