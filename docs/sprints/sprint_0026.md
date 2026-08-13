---
sprint: 26
titulo: "Dashboard web de solo lectura sobre los CSV de los watchers"
etapa: 2
---

# Sprint 0026 — Dashboard web de solo lectura sobre los CSV de los watchers

## Objetivo
Marcelo pidió, antes de dormir con la corrida nocturna en marcha, una herramienta web simple para revisar "todo lo encontrado" a la mañana siguiente con visualizaciones que muestren patrones — un primer paso liviano hacia una futura aplicación. La sesión ya había hecho ese trabajo a mano dos veces esa misma noche: separar señal real (ZEC — persistente, siempre positivo) de ruido (MANA — el book fino de Poloniex hace que el flag aparezca solo por casualidad). El objetivo del dashboard es automatizar exactamente ese diagnóstico, no solo mostrar filas.

## Alcance
- `com.cryptobot.dashboard.WatcherFormats`: registro estático de los 6 formatos de CSV (columnas de identidad + métrica principal por watcher).
- `com.cryptobot.dashboard.CombinationSeriesAnalyzer`: agrupa por combinación, calcula consistencia (veces marcada / veces que apareció) y bandera de posible choque de ticker.
- `com.cryptobot.dashboard.DashboardServer`: servidor HTTP local (`HttpServer` del JDK, sin dependencia nueva) con 3 rutas.
- `src/main/resources/dashboard/index.html`: visor autocontenido (HTML/CSS/JS vanilla, sin librerías externas).
- _(Fuera de alcance: auto-refresh/WebSockets, autenticación, empaquetado standalone, arreglar el guardia de implausibilidad faltante de `SpreadWatcher` — ver backlog.)_

## Decisiones
- **Reusar `WatchHealthAnalyzer` (Sprint 0019) tal cual, sin tocarlo** — ya cubre timestamp/stale/flag/error de forma genérica para los 6 formatos; lo nuevo es solo la parte que no cubre a propósito (identidad de combinación + métrica principal, que sí varía por hipótesis).
- **`consistencyPct = flaggedCount / appearances`** por combinación — la misma pregunta que se respondió a mano esa noche para distinguir ZEC (señal real) de MANA (ruido). Solo se cuentan como serie los puntos marcados `REVISAR` (el único valor común a los 6 formatos); `IMPLAUSIBLE` (solo en `CrossTriangleWatcher`) cuenta como aparición pero no se grafica.
- **Bandera informativa de posible choque de ticker** (`|métrica| > 50%`, mismo umbral que `CrossTriangleCheck`/`CrossTriangleWatcher` usan desde el Sprint 0012) — no descarta datos, solo avisa. Relevante para `spread-watch`, que no tiene ese guardia todavía (backlog) y mostró el caso real de TAO (118.620%) esa misma noche.
- **Serialización JSON manual contra árboles `Map`/`List`**, no serialización automática de los records — evita sumar la dependencia `jackson-datatype-jsr310` que haría falta para serializar `Instant`/`Duration` directamente; Jackson databind ya es dependencia del proyecto, esto no suma ninguna nueva.
- **Puerto 8089, no 8080** — 8080 ya estaba ocupado en la máquina de Marcelo por otro proyecto suyo (Aimily, Spring Boot) sin relación con Cryptobot. Confirmado en vivo, no asumido.
- **`TOP_N` de combinaciones en 50, no 20** — medido en vivo: `spread-watch`, el archivo con más volumen, tuvo 33 combinaciones distintas marcadas alguna vez `REVISAR` en ~200.000 filas. Con 20, las combinaciones de baja consistencia (como MANA, el ejemplo de ruido de esa noche) quedaban tapadas detrás de combinaciones siempre-100% — exactamente el caso que el dashboard debía ayudar a ver. Corregido tras la verificación en vivo, no en el diseño original.
- **Sin commit/push sin autorización de Marcelo** — se implementó y verificó en vivo, pero queda en un branch local sin pushear.

## Tareas
- [x] `WatcherFormats` + test de contrato contra los 6 headers reales
- [x] `CombinationSeriesAnalyzer` + tests (sintéticos, mismo estilo que `WatchHealthAnalyzerTest`)
- [x] `DashboardServer` (rutas `/`, `/api/files`, `/api/dashboard`)
- [x] `index.html` autocontenido
- [x] `mvn test` en verde
- [x] Verificación en vivo contra los 6 CSV de la corrida nocturna (incluidos los 2 archivos sueltos de corridas anteriores)

## Sprint Review
**Cómo probar:** `mvn test`; `mvn exec:java -Dexec.mainClass=com.cryptobot.dashboard.DashboardServer` y abrir `http://localhost:8089`.

**Debe cumplir:**
- [x] `WatchHealthAnalyzer` se reusa sin cambios para la sección de salud
- [x] Cada combinación marcada alguna vez `REVISAR` aparece con su consistencia, sin cap silencioso (`omittedCount` explícito)
- [x] El servidor relee el archivo en cada request — confirmado contra archivos que seguían creciendo mientras se probaba

## Hallazgos
- **El caso ZEC vs. MANA se reprodujo automáticamente**, sin ningún ajuste manual: en `spread-watch` (200k filas), las 5 combinaciones de ZEC salieron con 80,7%–100% de consistencia; `MANA/USDT CoinEx/Poloniex` salió con 11,1% — el mismo diagnóstico que se hizo a mano esa noche, ahora calculado por el propio dashboard.
- **`TOP_N=20` fue insuficiente en la práctica** — con solo 33 combinaciones totales en el archivo más grande, un cap de 20 alcanzaba a esconder justo los casos de baja consistencia que el dashboard existe para mostrar. Subido a 50 tras medirlo en vivo, no como ajuste especulativo.
- **Puerto 8080 en conflicto real** — no un caso hipotético: había otro proyecto de Marcelo corriendo ahí desde hacía 9 días. El dashboard usa 8089.
- **Rendimiento**: `spread-watch` (~200.000 filas, el archivo más grande de la corrida) respondió en ~0,3s por request, con los watchers todavía escribiendo — la pasada streaming (sin cargar todas las filas en memoria) confirmó ser suficiente sin necesidad de caché ni índices.
- **La bandera de posible choque de ticker funcionó en el dato real**: TAO/BTC (YoBit/CoinEx) y varios otros salieron marcados, coincidiendo con los hallazgos ya documentados en el backlog sobre `SpreadWatcher` sin guardia de implausibilidad.

## Cierre
El dashboard queda operativo y verificado contra datos reales, pero **no commiteado ni pusheado** — regla del proyecto, sin autorización explícita de Marcelo. Cómo levantarlo: `mvn exec:java -Dexec.mainClass=com.cryptobot.dashboard.DashboardServer`, abrir `http://localhost:8089`. Sigue pendiente el resto del backlog ya conocido (guardia de implausibilidad de `SpreadWatcher`, Bitfinex en cash-and-carry, CoinEx como 3er candidato de funding cross-exchange, rate limit real de cada exchange, entre otros) — este sprint no tocó ninguno de esos, solo agregó la capa de visualización sobre lo ya capturado.
