# Roadmap — Etapas y sprints

> **Este documento se mantiene deliberadamente simple y general.** El roadmap es vivo: no está planificado por completo de antemano, se construye a medida que avanzamos. Por eso aquí **no van costos, métricas ni detalle fino** — el *porqué* vive en [vision.md](vision.md) y el detalle de ejecución en cada `sprints/sprint_NNNN.md`. Mantenerlo así evita duplicar información y que el roadmap se desincronice. Aquí solo: las etapas como dirección, y los sprints ubicados bajo su etapa con su estado.

## Las etapas

De la investigación a la evidencia. No saltamos etapas — la 3 no se abre si la 2 no da señal positiva.

```plantuml
@startuml
' --- Estilo Cryptobot (ver _assets/cryptobot-style.puml) ---
skinparam backgroundColor #FFFFFF
skinparam defaultFontName "FreeSans"
skinparam shadowing false
skinparam roundCorner 10
skinparam ArrowColor #B7791F
skinparam rectangle {
  BackgroundColor #FBF3E7
  BorderColor #B7791F
  FontColor #4A3403
}
' -------------------------------------------------
rectangle "Etapa 1\nEstado del arte" as E1
rectangle "Etapa 2\nAnálisis de viabilidad" as E2
rectangle "Etapa 3\n(pendiente)" as E3
E1 -right-> E2
E2 -right-> E3
@enduml
```

| Etapa | Qué es | Evaluar pasar a la siguiente cuando… |
| :--- | :--- | :--- |
| **1 — Estado del arte** | Catálogo de estrategias de arbitraje cripto (la que ya vivió Marcelo hace ~10 años + las que aparecieron después: triangular cross-exchange, funding rate, premium regional, etc.), qué cambió en el mercado desde entonces, e hipótesis de dónde puede quedar edge hoy. Sin código, sin conectarse a ningún exchange. | El catálogo está completo y salen 2-3 hipótesis concretas y priorizadas, listas para testear con datos reales. |
| **2 — Análisis de viabilidad** | Herramientas de **solo lectura** (APIs públicas de mercado, sin cuentas ni permisos de trading, sin capital) que capturan datos reales — order books, funding rates — en un grupo de exchanges (algunos "majors" de control + chicos/regionales) y miden si las hipótesis de la Etapa 1 se sostienen neto de fees y slippage. | Cada hipótesis priorizada tiene un veredicto documentado con datos: señal real o descartada. |
| **3 — (pendiente)** | Se define **solo si** la Etapa 2 muestra señal positiva en al menos una hipótesis. No se decide de antemano qué es — podría ser paper trading, podría ser profundizar en la hipótesis ganadora. | — es la salida de la Etapa 2, no tiene criterio propio todavía. |

> **Capital real:** no se abre ni se evalúa antes de la Etapa 3, y ni ahí sin autorización explícita — ver [metodologia.md](metodologia.md).

## Sprints

Cada sprint se agrupa bajo su etapa. El detalle de cada uno vive en su archivo; aquí solo el estado. Las etapas aparecen acá a medida que tienen sprints.

### Etapa 1 — Estado del arte

|              Sprint              | Objetivo                                | Estado      |
| :------------------------------: | :-------------------------------------- | :---------- |
| [`0001`](sprints/sprint_0001.md) | Estado del arte y catálogo de hipótesis | ✅ Cerrado |

### Etapa 2 — Análisis de viabilidad

|              Sprint              | Objetivo                                                       | Estado         |
| :------------------------------: | :--------------------------------------------------------------| :------------- |
| [`0002`](sprints/sprint_0002.md) | Primer conector de solo lectura y validación de spread real    | ✅ Cerrado      |
| [`0003`](sprints/sprint_0003.md) | Monitoreo continuo con registro (SpreadWatcher)                | ✅ Cerrado      |
| [`0004`](sprints/sprint_0004.md) | Primera corrida nocturna real + detector de precio congelado   | ✅ Cerrado      |
| [`0005`](sprints/sprint_0005.md) | Conectores BudaPRO y YoBit + comparación en vivo de 4 exchanges | ✅ Cerrado      |
| [`0006`](sprints/sprint_0006.md) | Spread neto, no bruto — restar fees reales                     | ✅ Cerrado      |
| [`0007`](sprints/sprint_0007.md) | Monitoreo continuo de 4 exchanges, todas las combinaciones     | ✅ Cerrado      |
| [`0008`](sprints/sprint_0008.md) | Fee real de NotBank (no estimada), por tipo de par             | ✅ Cerrado      |
| [`0009`](sprints/sprint_0009.md) | Arbitraje triangular intra-exchange, primer corte (Poloniex)   | ✅ Cerrado      |

### Estados

| Estado | Significado |
| :--- | :--- |
| 📝 **Planificado** | Definido, aún no comienza |
| 🔵 **En curso** | En ejecución |
| ✅ **Cerrado** | Terminado y demostrado |
| ⏸️ **Pausado** | Detenido temporalmente |

> Plantilla para sprints nuevos: [_plantilla-sprint.md](sprints/_plantilla-sprint.md) (archivo único; para cambiar el formato se edita directamente y git guarda el historial).

## Backlog técnico (no priorizado)

Cosas que sabemos que hay que hacer pero que **no son un sprint todavía**: mejoras, deuda técnica o ideas que esperan su momento. No tienen orden ni fecha; cuando una se vuelve relevante, se convierte en (o se suma a) un sprint. Vive acá para **no perderse**.

| Tema                                                           | Qué es                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |                                                          Surgió en                                                          |
| -------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------: |
| **Modelo de expectativa, no de spread fijo**                   | La Etapa 2 no debería preguntar solo "¿hay spread positivo?" — con latencia y fills parciales de por medio, esto deja de ser arbitraje sin riesgo y pasa a ser una apuesta con expectativa matemática (positiva si el hit rate y el tamaño de ganancia/pérdida lo justifican, igual que un market maker). El análisis de viabilidad debería calcular esa expectativa neta por operación, no un spread puntual.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |         [Sprint 0001](sprints/sprint_0001.md), leyendo [spot-cross-exchange](estrategias/01-spot-cross-exchange.md)         |
| **Slippage medido, no asumido**                                | No alcanza con el precio del top of book — hay que simular la ejecución contra la profundidad real del order book capturado en la Etapa 2, y validar contra el slippage que realmente se habría sufrido.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |                                                            Idem                                                             |
| **Política ante posición abierta / fill parcial**              | Si una pata de la operación se ejecuta y la otra no (o se ejecuta a peor precio), queda una posición direccional no cubierta. Falta decidir la política: cerrar a mercado de inmediato, esperar un margen de tiempo, u otra cosa. Es una decisión de diseño para cuando se construya la ejecución (Etapa 3+), pero el riesgo hay que modelarlo ya en la Etapa 2.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |                                                            Idem                                                             |
| ~~**Filtrar por liquidez real antes de evaluar un ciclo**~~ **— implementado** | Que un triángulo exista matemáticamente (o que un par cotice en dos exchanges) no significa que tenga profundidad real en todas sus patas. **Confirmado en vivo en el Sprint 0003**: en Poloniex, XTZ mostraba un "mejor bid" que era una sola orden vieja y chica (0,0278 XTZ) muy por encima del resto del libro real — sin filtrar, generaba un spread falso del 81%. `OrderBook.bestBidAbove/bestAskAbove` filtra por valor nocional mínimo antes de considerar un nivel válido. Aplica a más de una estrategia; queda como técnica reusable, no solo resuelto para spot cross-exchange. | [Sprint 0001](sprints/sprint_0001.md) → [Sprint 0003](sprints/sprint_0003.md) |
| **Ejecución multi-orden bajo rate limit**                      | Disparar varias órdenes casi simultáneas (patas de un ciclo) puede chocar con el rate limit de la API del exchange y demorar una pata lo suficiente como para perder la ventana. Problema de infraestructura de ejecución, aparece en cualquier estrategia multi-pata.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |                                                            Idem                                                             |
| **Rebalanceo vía trades, no solo transferencia**               | El rebalanceo hoy se piensa como "mover el activo entre exchanges" (fees de red + tiempo + exposición en tránsito). Podría existir una forma más barata: operaciones tipo arbitraje que reposicionen el inventario donde hace falta, sin transferir. Esa operación no necesita ser rentable por sí sola — alcanza con que cueste menos que la transferencia real (incluso una pérdida chica, si es menor al costo de transferir, sería una mejora). Vale la pena modelarlo como alternativa en la Etapa 2, no asumir que rebalancear siempre implica transferir.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |   [Sprint 0001](sprints/sprint_0001.md), leyendo [triangular-cross-exchange](estrategias/03-triangular-cross-exchange.md)   |
| **Considerar exchanges DEX, no solo CEX**                      | Los perpetuos también existen en exchanges descentralizados (dYdX, Hyperliquid, GMX) — sin custodio central, se opera directo desde la wallet. Cambia el perfil del [riesgo de custodia](glosario.md#riesgo-de-custodia) (no hay exchange que pueda congelar retiros) por otros riesgos distintos (smart contract, liquidez menor que los CEX grandes). Vale la pena tenerlos como alternativa a evaluar en la Etapa 2, no solo los CEX clásicos.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | [Sprint 0001](sprints/sprint_0001.md), leyendo [funding-rate-cash-and-carry](estrategias/04-funding-rate-cash-and-carry.md) |
| **Ejecución maker + taker (ambos lados), no solo taker-taker** | El modelo de ejecución de [spot cross-exchange](estrategias/01-spot-cross-exchange.md) asumido hasta ahora es dos órdenes de mercado (taker-taker). Alternativa: dejar una orden límite parada (maker) del lado que conviene — un bid para comprar barato, o un ask para vender caro (esta última requiere inventario ya posicionado ahí) — y disparar la orden de mercado (taker) en el otro exchange recién cuando se llena. Reduce fee (si el exchange/tier tiene maker \< taker — en Poloniex tier base son iguales, hay que confirmar por exchange) y evita el slippage de barrer el book en la pata de entrada. Riesgo central: **selección adversa** — la orden parada tiende a llenarse justo cuando el precio se mueve en esa dirección, momento en que el spread objetivo del otro exchange puede haberse achicado o cerrado. No se puede confirmar con una foto del book — hace falta medir, con datos reales en el tiempo, la probabilidad de fill por nivel y qué tan correlacionado/con qué demora se mueve el otro exchange cuando eso pasa. |                       [Sprint 0001](sprints/sprint_0001.md), analizando order books reales de LTC/USD                       |
| **Confirmar el tier real de NotBank en la cuenta de Marcelo**  | `ExchangeFees` usa el tier base de NotBank (0,49% taker en CRYPTO-FIAT, 0,14% en CRYPTO-CRYPTO — ver `entorno.md`) asumiendo que es el que aplica, por ser una cuenta de exploración sin volumen real todavía. Es el supuesto más seguro (es la fee más alta de la escala, así que si el tier real fuera otro, la fee real sería igual o más baja — nunca peor de lo que ya se está asumiendo), pero sigue siendo un supuesto, no un dato confirmado desde la cuenta. Revisar cuando se loguee y actualizar `ExchangeFees`/`entorno.md` si el tier real es distinto. | [Sprint 0008](sprints/sprint_0008.md) |
