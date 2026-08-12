---
sprint: 4
titulo: "Primera corrida nocturna real + detector de precio congelado"
etapa: 2
---

# Sprint 0004 — Primera corrida nocturna real + detector de precio congelado

## Objetivo
Revisar los resultados de la primera corrida larga de `SpreadWatcher` (7 horas, overnight, Poloniex vs. NotBank) y corregir lo que esa corrida reveló: un "mejor precio" puede pasar el filtro de liquidez mínima (Sprint 0003) y aun así no ser real, si es una orden vieja que no se mueve.

## Alcance
- Análisis de la corrida nocturna del 2026-08-12 (04:02–11:04 UTC, 846 ciclos, 6.768 observaciones).
- `StalenessTracker`: marca (no descarta) un precio que lleva N ciclos seguidos sin cambiar — señal de mercado abandonado, no de tamaño insuficiente, que es lo que ya cubría el filtro de liquidez.
- Nueva columna `stale` en el CSV, y aviso en consola cuando una fila `REVISAR` involucra un precio marcado.
- _(Fuera de alcance: excluir automáticamente los pares "malos" — por ahora se marcan, la decisión de qué hacer con ellos queda para revisar con más datos.)_

## Decisiones
- **Marcar, no descartar.** Un precio quieto no es prueba de que el mercado es falso — un mercado real y tranquilo también puede quedarse sin cambios un rato. Se señala para que se revise, no se oculta la fila.
- **10 ciclos (~5 minutos) como umbral** — punto de partida razonable, no una medición fina; se puede ajustar con más corridas.
- **`compareTo()`, no `equals()`**, para comparar `BigDecimal` — dos representaciones del mismo número con distinta escala (`0.4999` vs `0.49990`) son el mismo precio, y `equals()` las trataría como distintas.

## Tareas
- [x] Analizar la corrida nocturna (846 ciclos) — ver hallazgos abajo
- [x] `StalenessTracker` + tests
- [x] Integrar en `SpreadWatcher`: nueva columna `stale`, aviso en consola
- [x] Verificación corta en vivo antes de dejarlo listo para la próxima corrida

## Hallazgos de la corrida nocturna

**Resultado principal: no apareció arbitraje real.** El spread bruto se mantuvo negativo en los 8 pares casi toda la noche. 28 filas se marcaron `REVISAR`, pero el máximo de toda la noche fue +0,105% (GRAM) — no alcanza a cubrir ni la fee de un solo lado en Poloniex (0,20%).

**XTZ en Poloniex es un mercado muerto, no uno real.** `polo_bid` quedó exactamente en 0,1200 y `polo_ask` exactamente en 0,4999 durante las 7 horas completas, sin moverse un centavo, mientras NotBank se movía con normalidad (0,1957 → 0,1942). Son órdenes viejas abandonadas. El filtro de liquidez del Sprint 0003 no lo detectaba porque el problema no es el tamaño — es que no cambia. Es la razón directa por la que nació este sprint.

**GRAM: la sospecha de datos dudosos se mueve de lado.** En la sesión de exploración manual habíamos marcado a NotBank como el lado sospechoso en GRAM (cantidades repetidas en el book). Con los datos de esta noche, `polo_bid` quedó pegado cerca de 1,26 toda la noche mientras NotBank bajó de forma pareja y creíble de 1,365 a 1,338. No es necesariamente contradictorio (NotBank podría tener un market maker con tamaños redondos que igual actualiza el precio real), pero el lado más estático esta vez fue Poloniex, no NotBank — vale la pena seguir mirando ambos, no asumir cuál es el problema de antemano.

**AAVE:** 33 errores de "sin liquidez suficiente", todos concentrados en los últimos 16 minutos de la corrida (10:48–11:04) — puntual, no un problema de toda la noche.

## Sprint Review
**Cómo probar:** `mvn test` (incluye `StalenessTrackerTest`); correr `SpreadWatcher` y confirmar la columna `stale` en el CSV.

**Debe cumplir:**
- [x] Un precio que no cambia en N ciclos se marca, sin ocultar la fila
- [x] Un cambio de precio resetea el conteo
- [x] No genera falsos "stale" por diferencias de escala en `BigDecimal`

## Cierre

Quedó funcionando y verificado. La corrida nocturna, aunque no encontró arbitraje, hizo exactamente lo que tenía que hacer: separar "no hay nada" de "no estamos mirando bien" — encontró un bug real (XTZ) antes de que arruinara la próxima corrida, y corrigió una sospecha mal puesta (GRAM). Con `StalenessTracker` sumado, la próxima corrida larga debería dar una lectura más limpia.

Pendiente / siguiente paso: dejar correr de nuevo con el detector puesto, por más tiempo si se puede; y sumar BudaPRO/YoBit, que es donde probablemente esté el resto de la hipótesis de la 01 (exchanges más chicos/regionales, no solo Poloniex vs. NotBank).
