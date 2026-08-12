# Glosario

Términos usados en los documentos de estrategias ([docs/estrategias/](estrategias/)), para no asumir conocimiento previo de trading/cripto y no repetir la misma explicación en cada documento. Cada término entra acá la primera vez que aparece; los documentos de estrategia lo linkean con `[término](../glosario.md#anchor)` en vez de reexplicarlo.

> Vivo: crece a medida que aparecen términos nuevos en `docs/estrategias/`. Orden alfabético.

## Apalancamiento

Operar con una posición más grande que el capital propio depositado, pidiendo el resto "prestado" al exchange contra ese capital como garantía ([margen](#margen)). Multiplica tanto las ganancias como las pérdidas — y las pérdidas pueden superar el capital depositado si no se gestiona con cuidado, lo cual dispara una [liquidación](#liquidación).

## Control de capital

Restricciones que impone un país sobre cuánta moneda local se puede convertir a otra divisa o sacar del país, y qué tan rápido. Cuando existen, dificultan (a veces bloquean) el arbitraje entre el mercado cripto interno y el internacional — no por falta de tecnología o velocidad, sino porque mover el dinero de un lado a otro no es libre. Es la causa de fondo detrás de primas persistentes como el "kimchi premium" en Corea o las que aparecieron en Argentina o Venezuela en momentos de crisis cambiaria.

## Delta-neutral

Una posición combinada cuyo valor no cambia (o cambia muy poco) si el precio del activo sube o baja, porque una parte gana exactamente lo que la otra pierde. Se logra combinando una posición larga y una corta de tamaño equivalente en el mismo activo (ver [posición larga y corta](<#Posición larga y corta>)). El objetivo no es apostar a la dirección del precio, sino capturar otra fuente de ganancia (ej. el [funding rate](<#Funding rate>)) sin esa exposición.

## Fees

Comisión que cobra un exchange por cada operación. La mayoría distingue dos tipos, **maker** y **taker**:
- **Maker** — pagás esta fee cuando tu orden **agrega** liquidez al order book (una orden límite que no se ejecuta al instante, queda esperando). Suele ser más baja.
- **Taker** — pagás esta fee cuando tu orden **quita** liquidez del book (una orden de mercado, o límite que se ejecuta al instante contra una orden ya existente). Suele ser más alta.

Para que un arbitraje sea rentable, el spread tiene que superar la suma de fees de **todas** las operaciones involucradas (típicamente 2 o más), no solo una.

## Funding rate

El pago periódico (típicamente cada 8 horas) que se intercambian directamente entre sí — no con el exchange — quienes están en largo y quienes están en corto en un [perpetuo](<#Perpetuo>) (ver [posición larga y corta](<#Posición larga y corta>)). Es el mecanismo que mantiene el precio del perpetuo anclado al precio [spot](#spot), ya que un perpetuo no tiene vencimiento que fuerce esa convergencia por sí solo. Si el funding es positivo, los largos le pagan a los cortos (suele pasar cuando hay mucha demanda de exposición larga apalancada); si es negativo, es al revés.

## HFT

High-Frequency Trading, o trading de alta frecuencia: firmas que operan con infraestructura especializada (servidores en el mismo datacenter que el exchange —"colocation"—, conexiones de baja latencia, acceso directo a la API) para ejecutar en milisegundos o menos. Compiten por el mismo tipo de oportunidad que un bot de arbitraje retail, pero con una ventaja de velocidad que un bot corriendo en una VPS o PC normal no puede igualar.

## Liquidación

El cierre forzado de una posición apalancada por parte del exchange, cuando las pérdidas acumuladas se acercan al capital depositado como [margen](#margen) y ya no alcanza para cubrir el riesgo de la posición. Ocurre automáticamente, sin que quien opera lo decida, y suele incluir una penalización adicional. Es el riesgo central de cualquier posición con [apalancamiento](#apalancamiento) — no confundir con [liquidez](#liquidez), que es una propiedad del mercado, no de una posición.

## Liquidez

Qué tan fácil es comprar o vender un activo sin mover mucho el precio. Un par **líquido** (ej. BTC/USDT en un exchange grande) tiene mucho volumen y órdenes profundas en el book — se puede operar montos grandes con poco impacto en el precio. Un par **ilíquido** (ej. una altcoin chica en un exchange chico) tiene poco volumen — cualquier orden de tamaño moderado mueve el precio contra quien la ejecuta (ver [slippage](#slippage)).

## Lote

También llamado **step size** o tamaño mínimo de orden. Cada exchange redondea la cantidad de un activo que se puede operar a un incremento fijo (ej. solo múltiplos de 0.0001 ETH, no cualquier decimal). En una estrategia de una sola operación es un detalle menor; en una de varias patas encadenadas, el redondeo en cada pata puede comerse buena parte de un margen ya fino.

## Margen

El capital depositado como garantía para abrir una posición con [apalancamiento](#apalancamiento). Cuanto menor el margen en relación al tamaño de la posición, mayor el apalancamiento — y menor el margen de maniobra antes de una [liquidación](#liquidación) si el precio se mueve en contra.

## Market maker

Un actor (persona, firma o el propio exchange) que coloca órdenes límite de compra y venta permanentemente alrededor del precio de mercado, para ganar el spread entre ambas y proveer liquidez al book. Los market makers profesionales son, junto a las firmas de [HFT](#hft), los principales competidores de un bot de arbitraje en los exchanges grandes.

## Order book

También llamado **libro de órdenes**. La lista de todas las órdenes de compra (bids) y venta (asks) pendientes para un par, ordenadas por precio. El precio más alto que alguien está dispuesto a pagar (mejor bid) y el más bajo al que alguien está dispuesto a vender (mejor ask) definen el spread del mercado en ese momento. Ver también [top of book](<#Top of book>).

## Perpetuo

Contrato de futuros sin fecha de vencimiento — a diferencia de un futuro tradicional, que fuerza la convergencia con el precio [spot](#spot) al vencer, un perpetuo puede mantenerse abierto indefinidamente. Sin un vencimiento que lo ancle, usa en cambio el [funding rate](<#Funding rate>) para mantener su precio cerca del precio spot.

## Posición larga y corta

**Larga (long):** apostar a que el precio de un activo suba — se gana si sube, se pierde si baja. Comprar el activo en el mercado [spot](#spot) es la forma más simple de estar largo. **Corta (short):** apostar a que el precio baje — se gana si baja, se pierde si sube. Requiere instrumentos que permitan esa apuesta sin poseer el activo (ej. un [perpetuo](#perpetuo)), ya que no se puede vender en el spot algo que no se tiene.

## Rate limit

El límite de cuántas llamadas a la API se pueden hacer en una ventana de tiempo. Superarlo hace que el exchange rechace o demore las siguientes órdenes. Es crítico en estrategias que dependen de ejecutar varias órdenes casi simultáneas (ej. las patas de un [triangular](estrategias/02-triangular-intra-exchange.md)): si el rate limit demora una pata, la oportunidad puede cerrarse antes de completarla.

## Riesgo de custodia

El riesgo de que el exchange donde tenés fondos depositados no te deje retirarlos cuando querés — por insolvencia, hackeo, o una decisión unilateral de congelar retiros. Es un riesgo del **exchange como contraparte**, no del mercado: podés tener razón sobre el precio y perder igual si no podés mover tu capital.

## Slippage

La diferencia entre el precio que esperabas obtener y el precio real al que se ejecutó tu orden. Pasa porque una orden de tamaño real consume varios niveles del [order book](<#Order book>) (no solo el mejor precio disponible), y cada nivel siguiente es peor. Es más grande cuanto menor es la [liquidez](#liquidez) del par. En arbitraje, el slippage de ambas patas de la operación come directamente el margen esperado — hay que descontarlo, no solo los fees.

## Spot

El mercado o precio "de contado": comprar o vender el activo mismo, para entrega inmediata — a diferencia de un contrato derivado como un [perpetuo](#perpetuo), que referencia el precio de un activo sin necesariamente poseerlo. Es el mercado más simple: comprar 1 BTC en spot significa tener 1 BTC real, ya. Sirve como punto de referencia — el precio de un perpetuo se compara contra el precio spot para calcular el [funding rate](<#Funding rate>).

## Spread

La diferencia de precio entre dos puntos de referencia — puede ser entre el mejor bid y el mejor ask de un mismo [order book](<#Order book>) (spread de mercado), o entre el precio de un mismo activo en dos exchanges distintos (spread de arbitraje, el que se busca en arbitraje cross-exchange).

## Tasa cruzada

También llamada **cross rate**. El precio implícito entre dos activos que no cotizan directamente entre sí, derivado de sus precios contra un tercer activo común — por ejemplo, el precio de ETH en BTC se puede derivar combinando BTC/USDT y ETH/USDT, aunque ETH/BTC también cotice directamente como su propio par. El arbitraje triangular explota los momentos en que la tasa cruzada implícita no coincide con la tasa cotizada directamente.

## Top of book

El mejor precio de compra y el mejor precio de venta disponibles en un [order book](<#Order book>) en un momento dado — el precio "de vidriera" que se ve primero, pero que solo cubre una cantidad limitada del activo antes de que el precio empeore (ver [slippage](#slippage)).

## Wash trading

Comprar y vender el mismo activo a uno mismo (o entre cuentas relacionadas) para simular volumen o movimiento de precio que no refleja demanda real. Es habitual en listados nuevos de exchanges chicos, donde el proyecto o el propio exchange puede inflar artificialmente la actividad para atraer atención. Un volumen alto no garantiza que sea real — ver también [liquidez](#liquidez).
