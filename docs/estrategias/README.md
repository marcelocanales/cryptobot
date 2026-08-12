# Estrategias de arbitraje — Índice

Catálogo de estrategias de arbitraje cripto para el Sprint 0001 (ver [roadmap.md](../roadmap.md)). Cada documento explica **exactamente** cómo funciona una estrategia — sin asumir conocimiento previo, con diagrama y términos linkeados al [glosario](../glosario.md) — qué cambió desde que Marcelo la operó hace ~10 años, sus riesgos propios, y una hipótesis de si hoy queda edge.

> Se construye **una estrategia a la vez**: se arma, se revisa, y recién ahí se pasa a la siguiente. Plantilla para documentos nuevos: [_plantilla-estrategia.md](_plantilla-estrategia.md).

## Estrategias

| Estrategia | Resumen | Hipótesis de vigencia |
| --- | --- | --- |
| [Spot cross-exchange](01-spot-cross-exchange.md) | Comprar donde el par está más barato, vender casi al mismo tiempo donde está más caro, en dos exchanges distintos | Media — cerrado en exchanges grandes, abierto a testear en chicos/regionales |
| [Triangular intra-exchange](02-triangular-intra-exchange.md) | Ciclo de 3 operaciones entre 3 pares del mismo exchange que vuelve al activo inicial con ganancia si la tasa cruzada se rompe | Media — cerrado en exchanges grandes, abierto en exchanges chicos con muchos pares, sin el riesgo de custodia repartido del cross-exchange |
| [Triangular / multi-leg cross-exchange](03-triangular-cross-exchange.md) | Como el triangular, pero cada pata se ejecuta en el exchange que convenga — por mejor precio o porque el par no existe en ningún exchange individual | Media-alta — la más prometedora del catálogo: la ventaja es cobertura/diseño, no velocidad pura |
| [Funding rate (cash-and-carry)](04-funding-rate-cash-and-carry.md) | Comprar spot + short en el perpetuo (delta-neutral) para cobrar el funding rate mientras se mantiene la posición | Media — la estrategia funciona y es pública; la duda es si construirla propia gana algo frente a un producto de yield ya armado |
| [Funding rate cross-exchange](05-funding-rate-cross-exchange.md) | Corto en el perpetuo del exchange con funding más alto, largo en el de funding más bajo — delta-neutral, cobrando el diferencial | Media — real y usada por fondos, pero con doble riesgo de liquidación (dos patas apalancadas, no una) |
| [Premium regional / fiat](06-premium-regional.md) | Precio de un activo en moneda local vs. su precio internacional convertido — la prima persiste por controles de capital, no por velocidad | Baja-media — real en países con controles de capital fuertes; la medición propia en Chile (USDT/CLP) no mostró nada |
| [Arbitraje de nuevo listado](07-nuevo-listado.md) | Un activo ya listado en otro lado se lista de nuevo en un exchange — el precio inicial, con el book recién formándose, puede desalinearse del ya establecido | Media — competido en exchanges grandes, pero con ventana de minutos/horas en vez de milisegundos |

## Descartadas

Estrategias que se decide no explorar, con su porqué.

- **Latency arbitrage puro** (competir por velocidad pura en exchanges grandes) — requiere infraestructura de colocation que no vamos a tener. No se documenta con su propio archivo porque no hay nada que testear: se descarta por estructura del mercado, no por falta de datos.

## Síntesis — hipótesis priorizadas para la Etapa 2

**Cómo quedó organizada la priorización:** no elegimos 2-3 estrategias y descartamos el resto — la decisión cara de la Etapa 2 es **qué exchanges instrumentar**, no qué estrategia probar primero. Una vez que la captura de datos exista para un grupo de exchanges, varias hipótesis se pueden chequear sobre la misma data. Por eso la síntesis tiene dos partes: los exchanges candidatos (con lo que ya sabemos de cada uno, de las pruebas manuales de hoy) y qué hipótesis aplican sobre ellos.

### Exchanges candidatos

Detalle completo, con notas de confiabilidad por par, en [entorno.md](../entorno.md).

| Exchange | Cuenta | Carácter | Aplica a |
| --- | :---: | --- | --- |
| Poloniex | ✅ | Internacional, grande pero no top-3 global | 01, 02, 03 |
| NotBank (ex-CryptoMKT) | ✅ | Regional/LatAm; buena liquidez en USDT/CLP, señales de datos sintéticos en pares chicos — verificar por par | 01 (vs. Poloniex/Buda), 06 |
| BudaPRO | ✅ | Chileno establecido, book real en USDT/CLP | 01, 06 |
| YoBit | ✅ | Particular, lista muchos tokens chicos/raros; reputación históricamente cuestionada — validar integridad de datos antes de confiar | 01, 02 |

### Hipótesis priorizadas, en orden

1. **Spot cross-exchange (01), en varios de los cuatro exchanges de arriba** — la de mayor evidencia empírica hoy (ya la testeamos a mano toda esta sesión). Prioridad alta explícita: no se agrupa ni se subordina a otra.
2. **Triangular / multi-leg cross-exchange (03)** — la de mayor convicción teórica del catálogo (media-alta): la ventaja es cobertura y diseño, no velocidad pura.
3. **Funding rate cross-exchange (05)** — convicción media, con una razón estructural sólida (exchanges no comparten base de usuarios) y un perfil de riesgo distinto al resto (tiempo, no milisegundos).
4. **Triangular intra-exchange (02)** — se evalúa con la misma captura de datos que la 01 y la 03, sobre los mismos exchanges (en especial Poloniex y YoBit, que tienen muchos pares listados).

Cash-and-carry (04) y nuevo listado (07) quedan documentadas pero no priorizadas para el primer testeo de la Etapa 2 — la 04 porque su pregunta central (construir vs. usar un producto ya armado) no es urgente, la 07 porque necesita vigilancia activa de anuncios de listado, infraestructura aparte de la que comparten las otras cuatro. Premium regional (06) queda condicionada: se retoma solo si aparece un corredor con control de capital real y accesible — la medición de hoy en Chile no dio señal.
