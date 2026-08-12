---
sprint: 15
titulo: "Funding rate cash-and-carry, primer corte (Poloniex + NotBank)"
etapa: 2
---

# Sprint 0015 — Funding rate cash-and-carry, primer corte (Poloniex + NotBank)

## Objetivo
Primera implementación de la hipótesis 04 ([funding rate cash-and-carry](../estrategias/04-funding-rate-cash-and-carry.md)) — de naturaleza distinta a las tres anteriores (01/02/03, arbitraje instantáneo): acá se mide si el funding rate real, ahora mismo, alcanza para cubrir los costos de entrada de una posición delta-neutral que se mantendría abierta cobrando ese funding.

## Alcance
- Descubrimiento real de perpetuos en Poloniex (el único de los 4 exchanges que los tiene, confirmado en vivo antes de planificar).
- Spot: mejor precio neto entre Poloniex y NotBank (mismo criterio "2 exchanges primero" que la hipótesis 03).
- Foto en vivo (`CashAndCarryCheck`), no continua todavía.
- _(Fuera de alcance: apalancamiento, margen, riesgo de liquidación — eso es Etapa 3. Acá solo se mide si el funding actual cubre la entrada.)_

## Decisiones
- **La "viabilidad" acá no es un solo número.** A diferencia de `NetSpread`/`TriangleSpread`/`CrossTriangleSpread` (arbitraje instantáneo, se resuelve en un "neto%" único), cash-and-carry es un rendimiento periódico contra un costo de entrada — se reportan por separado: basis (ganancia/costo inmediato de la diferencia spot-perpetuo), funding por período, funding anualizado, fees de entrada, y períodos de funding necesarios para recuperarlas.
- **Intervalo de funding medido, no citado del doc.** El doc de estrategia dice "típicamente 8 horas" — se confirmó contra la API real (`nFT - fT` de Poloniex) que en efecto son 8 horas exactas, ahora es un dato verificado, no una cita.
- **La fee del perpetuo no tiene la misma calidad de fuente que el resto.** Poloniex no expone un endpoint público de fees de futuros (se buscó). El valor usado (0,075% taker) sale de contenido de soporte/anuncios del propio exchange — se documenta con esa salvedad y queda en el backlog, mismo tratamiento que tuvo la fee de NotBank antes de encontrar su API real (Sprint 0006→0008).
- **`ParallelFetch` desde el diseño, no como parche después** — a diferencia de las hipótesis anteriores (que lo sumaron en el Sprint 0014, después de construidas), acá se usó desde el primer commit.

## Tareas
- [x] `PerpQuote` + `PoloniexConnector.fetchPerpSymbols()`/`fetchPerpQuote()` + tests con respuestas reales
- [x] `ExchangeFees.perpTakerFee()`
- [x] `CashAndCarrySpread` + tests (caso base verificable a mano, selección de mejor spot por neto, funding negativo, sin liquidez)
- [x] `CashAndCarryCheck` + verificación en vivo

## Hallazgos de la verificación en vivo

**18 perpetuos encontrados en Poloniex, 16 con spot disponible en Poloniex y/o NotBank, 12 evaluados con datos suficientes** (XRP, TRX, ADA, DOGE quedaron fuera por liquidez insuficiente en un momento puntual — verificado contra el book real de XRP: el mejor bid del perpetuo tenía ~USD 41 de nocional, por debajo del umbral de $50, no un bug).

**El funding rate de los 12 activos evaluados dio exactamente 0,01% en todos** — se verificó contra la API real, por separado, para ETH/SOL/APT: los tres confirmaron el mismo valor. No es un error de parseo — parece ser un piso/valor por defecto que Poloniex está aplicando en sus perpetuos ahora mismo, no un funding diferenciado por activo. Vale la pena tenerlo en cuenta: si esto es un piso administrado por el exchange y no un funding "de mercado", la estrategia depende de que en algún momento se diferencie hacia arriba, no de que el valor de hoy se mantenga.

**El basis fue negativo en los 12 casos** (el perpetuo cotiza *por debajo* del spot, no arriba) — lo opuesto al escenario típico que describe el doc de estrategia ("común en mercados alcistas"). Esto significa que, además de pagar las fees de entrada, hoy se empieza con una pérdida inmediata por el basis, no una ganancia.

**Los breakeven salieron entre ~29 y ~424 períodos de 8 horas** (≈10 días a ≈141 días) para recuperar solo las fees de entrada vía funding, sin contar que el funding rate puede cambiar de signo en cualquier momento (riesgo explícito del propio doc de estrategia) y que el basis negativo ya juega en contra desde el día uno.

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.CashAndCarryCheck` para la foto en vivo.

**Debe cumplir:**
- [x] Los perpetuos se descubren de la API real, no de una lista hardcodeada
- [x] El intervalo de funding se deriva de las horas reales de la API, no se asume
- [x] Se reportan basis/funding/fees/breakeven por separado, no colapsados en un solo número

## Cierre

Primer resultado real de la hipótesis 04: con los datos de ahora mismo, el cash-and-carry en Poloniex **no se ve atractivo** — funding en el piso (0,01%, igual en los 12 activos, posible valor por defecto del exchange más que una señal de mercado), basis negativo (costo inmediato, no ganancia), y breakevens de semanas a meses sobre un funding que puede cambiar de signo en cualquier momento. No es un "no hay arbitraje" como en 01/02/03 — es un "hoy no compensa el costo de entrada, con el funding actual". Coherente con lo que el propio catálogo anticipaba: convicción media, la pregunta central no era si la estrategia existe (existe) sino si el funding real alcanza — hoy, no.

Sin veredicto todavía en `docs/estrategias/04-funding-rate-cash-and-carry.md` — mismo criterio que las 3 anteriores: falta ver esto sostenido en el tiempo (una foto no alcanza, mismo argumento que ya se aplicó a 01/02/03) antes de concluir. Siguiente paso natural: dejarlo correr un tramo — el funding cambia cada 8 horas, así que unos pocos días de corrida (no continua todavía, la próxima pieza si esto se retoma) alcanzarían para ver si el funding se mueve de ese piso de 0,01% en algún momento.
