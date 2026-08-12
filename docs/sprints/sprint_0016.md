---
sprint: 16
titulo: "CashAndCarryWatcher — monitoreo continuo cash-and-carry"
etapa: 2
---

# Sprint 0016 — CashAndCarryWatcher — monitoreo continuo cash-and-carry

## Objetivo
Mismo salto que `TriangleWatcher`/`CrossTriangleWatcher` fueron para sus respectivos checks: pasar de una sola foto (`CashAndCarryCheck`, Sprint 0015) a una corrida continua — necesario antes de la corrida nocturna con las 4 hipótesis, para que la 04 no se quede con una sola foto mientras las otras 3 acumulan horas de datos.

## Alcance
- `CashAndCarryWatcher`: corre en loop, descubre los perpetuos una vez, registra cada ciclo en CSV.
- Reusa `StalenessTracker`, pero **solo en las patas de precio** (spot ask, perpetuo bid) — no en el funding rate.
- _(Fuera de alcance: correrlo un tramo largo todavía — es la próxima decisión operativa, la nocturna con las 4 hipótesis juntas.)_

## Decisiones
- **El funding rate queda fuera del detector de staleness, a propósito.** El funding cambia cada 8 horas por diseño del propio mercado — dentro de esa ventana, que no cambie no es una señal de dato malo (a diferencia de un book que no se mueve), es el comportamiento esperado. Aplicarle el mismo detector que a un precio de order book generaría un falso positivo constante, no una alerta útil. Solo se vigila lo que sí debería moverse todo el tiempo: el spot ask y el bid del perpetuo.
- **`flag=REVISAR` significa "la entrada ya se paga sola con el basis"**, no "hay funding positivo" (eso pasa casi siempre que el funding es > 0, no es información). Se dispara cuando `breakevenPeriods == 0` — el basis por sí solo ya cubre las fees de entrada, sin necesitar ni un período de funding.

## Tareas
- [x] `CashAndCarryWatcher`: descubrimiento único, loop de 30s, CSV largo, staleness acotado a precios
- [x] Verificación en vivo: 1 ciclo completo, confirmado 15 columnas consistentes (incluidas las filas "sin liquidez")

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.watch.CashAndCarryWatcher` y confirmar el CSV en `data/cash-and-carry-watch-*.csv`.

**Debe cumplir:**
- [x] Los perpetuos y mercados spot se descubren una sola vez, no en cada ciclo
- [x] El funding rate no se marca `stale` aunque no cambie entre ciclos
- [x] Cada fila (con datos, sin liquidez, o error) tiene la misma cantidad de columnas

## Cierre

Con esto, las 4 hipótesis del catálogo que se pudieron construir con los exchanges conectados tienen la misma capacidad de correr sin supervisión: `SpreadWatcher` (01), `TriangleWatcher` (02), `CrossTriangleWatcher` (03) y ahora `CashAndCarryWatcher` (04). Ninguna tiene veredicto todavía.

Siguiente paso: la corrida nocturna con las 4 juntas — ahí sí, con horas de datos reales (y al menos un cambio de funding, que ocurre cada 8h), se puede empezar a escribir los veredictos con confianza.
