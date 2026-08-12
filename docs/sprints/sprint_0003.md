---
sprint: 3
titulo: "Monitoreo continuo con registro (SpreadWatcher)"
etapa: 2
---

# Sprint 0003 — Monitoreo continuo con registro (SpreadWatcher)

## Objetivo
Pasar de "una corrida, una foto" a un poller que corre en loop y registra cada observación en un archivo — porque el arbitraje real es esporádico (aparece y desaparece), y una sola foto no alcanza para saber si existe. Nace de una reflexión de Marcelo sobre su experiencia real hace ~10 años: dejaba el bot corriendo toda la noche, y a veces encontraba algo.

## Alcance
- `SpreadWatcher`: loop en primer plano, intervalo configurable (30s por defecto), trae Poloniex + NotBank para una lista de pares y registra cada observación en CSV — no solo la última.
- Lista de pares: BTC/ETH/LTC como control (se espera ~0), más DOGE/AAVE/GRAM/XTZ/SHIB como la hipótesis real — incluye pares ya vistos con señales de datos dudosas en NotBank, para seguir juntando evidencia sobre eso también.
- Cada fila marca `REVISAR` si el spread bruto (antes de fees) da positivo en cualquier dirección — sin asumir un umbral de fee, eso se filtra después a mano.
- Manejo de errores por par: si falla un fetch puntual, se registra el error y sigue — no se cae toda la corrida.
- _(Fuera de alcance: BudaPRO/YoBit, cálculo de fees reales, cualquier ejecución.)_

## Decisiones
- **CSV, no JSON ni base de datos** — para esta escala (un puñado de pares, unas horas) alcanza con un archivo que se abre en cualquier lado, sin agregar una dependencia nueva.
- **Filtro de liquidez mínima por valor nocional** (no por cantidad cruda) al elegir el "mejor" bid/ask — encontrado necesario en la verificación de esta misma tarea: un bid viejo y chico (0,0278 XTZ) parado muy por encima del resto del libro real de Poloniex generaba un spread falso del 81%. El resto del libro, agrupado cerca del precio de NotBank, es el real. Ver `OrderBook.bestBidAbove/bestAskAbove` y el test que fija este caso.

## Tareas
- [x] `SpreadWatcher` — loop, CSV, manejo de errores por par
- [x] `OrderBook.bestBidAbove/bestAskAbove` — filtro de liquidez real por valor nocional (US$50 por defecto)
- [x] Verificación corta (varios ciclos) antes de dejarlo para la corrida larga
- [x] Encontrado y corregido en el camino: falso positivo de 81% en XTZ por una orden vieja aislada en Poloniex

## Sprint Review
**Cómo probar:** `mvn exec:java -Dexec.mainClass=com.cryptobot.watch.SpreadWatcher` desde `code/cryptobot/`, dejarlo correr, revisar el CSV en `data/`.

**Debe cumplir:**
- [x] Corre en loop sin caerse ante un error puntual de un exchange
- [x] Cada ciclo se guarda antes del siguiente (interrumpirlo no pierde lo ya registrado)
- [x] No genera falsos positivos por ordenes viejas/chicas aisladas en el book

## Cierre

Quedó funcionando y verificado en corridas cortas (varios ciclos, en vivo). Pendiente: la corrida larga de verdad — Marcelo lo deja corriendo esta noche en su equipo, se revisa el CSV mañana. El hallazgo más valioso de este sprint no estaba en el plan: un bug real de "mejor precio" ingenuo (tomar el primer nivel del book sin mirar si tiene volumen real) que hubiera llenado la corrida de toda la noche con falsos positivos. Corregido antes de dejarlo desatendido — exactamente el tipo de cosa que conviene encontrar en una verificación de un minuto y no en una revisión de ocho horas de datos.
