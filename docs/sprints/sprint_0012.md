---
sprint: 12
titulo: "Triangular cross-exchange, primer corte (Poloniex + NotBank)"
etapa: 2
---

# Sprint 0012 — Triangular cross-exchange, primer corte (Poloniex + NotBank)

## Objetivo
Última hipótesis del catálogo sin construir: la 03, [triangular / multi-leg cross-exchange](../estrategias/03-triangular-cross-exchange.md) — la de mayor convicción teórica (media-alta, la más alta de las 7). A diferencia del triangular intra-exchange (02), cada pata del ciclo puede tomarse del exchange que dé mejor precio, y algunos ciclos pueden no existir completos en ningún exchange individual.

## Alcance
- `CrossVenue`/`CrossTriangle`/`CrossTriangleFinder`/`CrossTriangleSpread`: modelo y cálculo para triángulos repartidos entre exchanges.
- `NotBankConnector.fetchMarkets()`: hacía falta para poder descubrir triángulos cross-exchange (antes solo Poloniex tenía esta capacidad, desde el Sprint 0009).
- `CrossTriangleCheck`: foto en vivo (no continua todavía), sobre **Poloniex + NotBank únicamente**.
- Pasada de revisión explícita antes de cerrar (ver más abajo).
- _(Fuera de alcance: YoBit/Buda en el universo cross-exchange, corrida continua, modelaje de inventario pre-posicionado — ver Decisiones.)_

## Decisiones
- **Poloniex + NotBank, no los 4 exchanges.** Mismo criterio ya usado dos veces (Poloniex antes que YoBit en la 02; 2 exchanges antes que 4 en la 01): empezar chico, validar el mecanismo, expandir después. YoBit (~9.000 pares) multiplicaría el universo de triángulos de golpe, con reputación de datos ya cuestionada: Buda aporta poco a un ancla en USDT. Quedan para un sprint posterior.
- **Detección, no ejecución.** El propio doc de estrategia aclara que acá la ganancia es cambio de *valor de portafolio*, no efectivo que "vuelve" — requiere inventario pre-posicionado en cada exchange. Esta implementación, como las hipótesis 01 y 02, solo mide si el ciclo da neto positivo ahora mismo tomando la mejor cotización disponible por pata — el modelaje de inventario/rebalanceo queda para Etapa 3.
- **Elegir por neto, no por precio bruto.** En cada pata con más de un exchange candidato, `CrossTriangleSpread` evalúa todos y toma el de mejor resultado después de fees — un exchange puede tener el precio nominal más atractivo y perder igual por cobrar más fee (verificado con test: NotBank con mejor ask nominal pierde contra Poloniex por su fee más alta en CRYPTO-FIAT).
- **Necesidad vs. optimización, medido explícitamente.** El propio doc de estrategia pide confirmar primero qué tan frecuente es el caso "por necesidad" (el ciclo no existe completo en ningún exchange individual) antes de justificar la complejidad adicional. `CrossTriangle.isNecessityCycle()` lo responde directamente por cada triángulo encontrado.
- **Sin veredicto en `docs/estrategias/03-triangular-cross-exchange.md` todavía** — mismo criterio que se viene aplicando a la 01 y la 02.

## Tareas
- [x] `NotBankConnector.fetchMarkets()` + test (con `IsDisable` real confirmado en la API)
- [x] `TriangleSpread.minNotionalFor` extendido con CLP
- [x] `CrossVenue` + `CrossTriangle` (con `isNecessityCycle()`) + `CrossTriangleFinder` + test — incluye el caso clave: un triángulo que **solo existe combinando 2 exchanges**
- [x] `CrossTriangleSpread` + test — incluye el caso clave: elegir el exchange de mejor neto, no de mejor precio bruto
- [x] `CrossTriangleCheck` + verificación en vivo
- [x] Pasada de revisión

## Hallazgos de la verificación en vivo

**69 triángulos encontrados** (Poloniex + NotBank, ancla USDT), **168 order books únicos**, 0 errores de conexión.

**Solo 2 de 69 (2,9%) son "por necesidad"** (`USDT-BTC-XAUT`, `USDT-USDC-XAUT`) — el resto ya existía completo en Poloniex solo. Con estos dos exchanges, cross-exchange actúa sobre todo como **optimización** (mejorar una pata puntual), no como una fuente de ciclos nuevos — responde directo la pregunta que el propio catálogo pedía medir primero. Con más exchanges (YoBit en particular, si se suma después) esta proporción podría cambiar.

**Sin arbitraje real: 0 de 138 direcciones con neto positivo** (después de descartar el hallazgo de abajo).

**Hallazgo real, corregido antes de cerrar el sprint — no solo un caso más de mercado fino:** la primera corrida mostró un bruto de **+2.158.104%** en `USDT-BOB-BTC`. Se investigó antes de reportarlo (mismo estándar que XTZ/AAVE/GRAM en sprints anteriores) y resultó ser un **choque de tickers entre exchanges**: el "BOB" de Poloniex es un token cripto barato (~USD 0,0000006, confirmado contra su book real), y el "BOB" de NotBank es el **Boliviano**, la moneda fiat de Bolivia (confirmado: `BTCBOB`, `ETHBOB`, `USDTBOB` son instrumentos reales de NotBank). Son dos activos completamente distintos que comparten un código de 3 letras — `CrossTriangleFinder` los trató como la misma moneda porque solo compara el string del ticker, no la identidad real del activo.

**Corrección aplicada:** `CrossTriangleCheck` marca cualquier resultado con `|bruto| > 50%` como "IMPLAUSIBLE — posible choque de tickers" en vez de reportarlo como señal — es un parche a nivel de reporte, no una solución de identidad de activos. Queda anotado en el backlog técnico (`roadmap.md`) como riesgo real que crece si se suman más exchanges, sobre todo YoBit.

## Pasada de revisión
Releídos todos los archivos nuevos/modificados con ojo crítico antes de cerrar:
- `CrossTriangleFinder`: confirmado que agrupa por par de monedas sobre la unión de venues (no se queda con un solo exchange por par), que es justo lo que permite encontrar el caso "por necesidad" — verificado con test dedicado, no solo revisado a ojo.
- `CrossTriangle.isNecessityCycle()`: confirmado que la lógica (¿existe un exchange común a las 3 listas de candidatos?) coincide exactamente con la definición del doc de estrategia.
- `CrossTriangleSpread.bestChoice()`: confirmado que la selección usa el resultado neto (precio × fee), no el precio bruto — el test que prueba esto construye un caso donde el precio nominal y el neto dan ganadores distintos, no un caso donde coinciden por casualidad.
- Se encontraron y corrigieron dos detalles menores durante la revisión: imports sin usar en `CrossTriangleCheck` (`HashSet`/`Set`, quedaron de una versión anterior del archivo) y una referencia a `BigDecimal` sin importar (usaba el nombre completamente calificado). Ninguno afectaba el comportamiento, pero no correspondía dejarlos.
- El hallazgo del choque de tickers (BOB) se encontró y corrigió **durante** la verificación en vivo, antes de escribir este documento — no fue una revisión posterior separada, sino parte del mismo estándar de "no reportar un número sin entender de dónde sale" aplicado en cada sprint anterior.

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.CrossTriangleCheck` para la foto en vivo.

**Debe cumplir:**
- [x] Se descubre al menos un ciclo "por necesidad" real, no solo casos de optimización
- [x] La elección de exchange por pata prioriza el resultado neto, no el precio bruto
- [x] Un resultado implausible (choque de tickers) se marca como sospechoso, no se reporta como arbitraje real

## Cierre

Con esto, las 3 primeras hipótesis del catálogo (01, 02, 03) tienen su primera implementación real, y las 3 coinciden en el mismo resultado: sin arbitraje neto detectable, en ninguna combinación probada hasta ahora. La 03 sumó, además, una lección de metodología nueva — combinar datos de varios exchanges introduce un riesgo que ni la 01 ni la 02 tenían (comparar el mismo activo entre exchanges, no exchange contra sí mismo): el ticker por sí solo no alcanza para saber si dos exchanges están hablando del mismo activo.

Sin veredicto todavía en ninguna de las 3 hipótesis — se sigue esperando antes de concluir. Siguiente paso: a definir con Marcelo — candidatos incluyen extender la 03 a YoBit/Buda, construir su versión continua (mismo patrón que `TriangleWatcher`), o revisar por fin la corrida nocturna pendiente de la hipótesis 01.
