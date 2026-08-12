# Entorno y herramientas

Inventario vivo de **qué usamos y cómo**: equipos, lenguajes/runtimes, herramientas, exchanges y servicios. Su objetivo es no tener que re-investigar — un solo lugar para saber con qué se trabaja.

Se actualiza **cada vez que se adopta** una herramienta, lenguaje, exchange o servicio. El *porqué* de cada elección vive en las **Decisiones** del sprint donde se tomó (ver [hub.md](hub.md)); aquí solo el *qué* y el *cómo usarlo*.

## Equipo principal

_(por completar)_

## Lenguajes y runtimes

- **Java 21** (LTS) — decidido en el Sprint 0002. Zona de confort de Marcelo; se evaluó Python por ser el lenguaje "de fábrica" del ecosistema cripto (librería [ccxt](https://github.com/ccxt/ccxt)), pero ccxt tiene soporte real también en Java — aunque, verificado, no publicado en Maven Central: se instala compilando desde fuente con Gradle. Por esa fricción, arrancamos escribiendo los conectores a mano (HTTP + JSON), sin ccxt por ahora; se puede reconsiderar si se suman muchos más exchanges.
- **Maven** — build tool. `pom.xml` en `code/cryptobot/`.
- **Jackson** (`jackson-databind`) — parseo de JSON de las respuestas de los exchanges.
- **JUnit 5** — tests. Se testea el parseo contra respuestas reales capturadas (sin mockear HTTP todavía).

## Herramientas

_(vacío por ahora)_

## Exchanges / APIs

Cuentas existentes (Marcelo) y lo que se verificó de cada una durante el Sprint 0001 — leído a mano en la UI, sin automatizar todavía. Cualquier API key futura va en `.env`, nunca se commitea.

| Exchange | Cuenta | Carácter | API pública de mercado | Notas |
| --- | :---: | --- | --- | --- |
| **Poloniex** | ✅ | Internacional, grande pero no top-3 global (Binance/Coinbase) | `GET https://api.poloniex.com/markets/{symbol}/orderBook` — pública, sin auth. Params: `symbol` (formato `BASE_QUOTE`, ej. `LTC_USDT`), `limit` (5/10/20/50/100/150). **Conector real en `code/cryptobot`** (`PoloniexConnector`), verificado en vivo desde el Sprint 0002 | Fees confirmadas: 0,20% maker / 0,20% taker en tier base (iguales — sin ventaja de ser maker hasta subir de volumen). Books de pares chicos (AAVE/USDT) vistos con huecos reales de liquidez en un momento dado. **XTZ_USDT confirmado como mercado abandonado** (Sprint 0004): `polo_bid`/`polo_ask` quedaron exactamente iguales durante 7 horas seguidas de monitoreo — no usar como fuente de precio hasta ver evidencia de que volvió a moverse |
| **NotBank** (ex-CryptoMKT) | ✅ | Regional/LatAm | Plataforma tipo AlphaPoint. Host real (no está en la documentación pública, confirmado a mano): `https://api.notbank.exchange`. `POST /AP/GetInstruments` `{"OMSId":1}` → lista de pares con su `InstrumentId` numérico (el símbolo, ej. `LTCUSDT`, no alcanza solo). `POST /AP/GetL2Snapshot` `{"OMSId":1,"InstrumentId":<id>,"Depth":10}` → array de filas posicionales `[MDUpdateId, Accounts, ActionDateTime, ActionType, LastTradePrice, Orders, Price, InstrumentId, Quantity, Side]` (`Side`: 0=bid, 1=ask) — sin auth. **Conector real en `code/cryptobot`** (`NotBankConnector`), verificado en vivo desde el Sprint 0002 — el mejor bid obtenido coincidió exacto con lo visto a mano en la UI | Liquidez real y buena en USDT/CLP. En pares de bajo volumen (DOGE/USDT, AAVE/USDT, GRAM/USDT) se observaron señales fuertes de datos no genuinos: volumen 24h en 0 con book poblado, "Recent Trades" en cantidad 0, y cantidades idénticas repetidas en múltiples niveles de precio (ver [wash trading](<glosario.md#Wash trading>) y la síntesis en [estrategias/README.md](estrategias/README.md)) — **verificar integridad de datos por par antes de confiar en el book** |
| **BudaPRO** (Buda.com) | ✅ | Chileno, establecido | `GET https://www.buda.com/api/v2/markets/{symbol}/order_book` — pública, sin auth. `symbol` en minúsculas con guion, ej. `btc-clp`. Respuesta: `{"order_book":{"bids":[[price,qty],...],"asks":[...]}}`, ambos como string — sin timestamp propio. Símbolo inválido → HTTP 404. **Conector real en `code/cryptobot`** (`BudaConnector`), verificado en vivo en el Sprint 0005 | Universo de activos acotado: BTC, ETH, BCH, LTC, USDC, USDT, SOL — casi todo cotizado en CLP/COP/PEN, **no en USDT**. Overlapea con NotBank sin necesitar conversión de moneda en BTC-CLP, ETH-CLP y LTC-BTC. Fees más altas que Poloniex/NotBank: 0,80% taker / 0,40% maker (confirmado en `GET /markets`, campo `taker_fee`/`maker_fee`). **LTC-BTC confirmado como mercado casi muerto** (Sprint 0005): book propio con ~30% de spread interno (bid 0,0006 / ask 0,0009) y niveles absurdos mezclados (una orden a precio `975696839.0`) — no usar como fuente de precio hasta ver evidencia de que se volvió líquido |
| **YoBit** | ✅ | Particular — lista muchos tokens chicos/poco conocidos | `GET https://yobit.net/api/3/depth/{symbol}?limit=N` — pública, sin auth. `symbol` en minúsculas con guion bajo, ej. `btc_usdt`. Respuesta: `{"{symbol}":{"bids":[[price,qty],...],"asks":[...]}}`, precio/cantidad como **número JSON, no string** (parsear con `DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS` para no perder precisión vía double). Símbolo inválido → **HTTP 200** con `{"success":0,"error":"..."}`, no un book vacío — hay que detectarlo aparte del status code. **Conector real en `code/cryptobot`** (`YobitConnector`), verificado en vivo en el Sprint 0005 | A diferencia de Buda, cotiza BTC/ETH/LTC/DOGE/SHIB **directo en USDT** — comparable tal cual contra Poloniex y NotBank, sin conversión. Fee 0,20% maker/taker (confirmado en `GET /api/3/info`). Reputación históricamente cuestionada en la comunidad cripto. **Confirmado en vivo (Sprint 0005): el top-of-book de SHIB/USDT es polvo** (mejor bid ~1.547 SHIB de cantidad, valor nocional ≈ USD 0,007) — generaba un falso "spread bruto" de 0,69% contra Poloniex hasta filtrar por nocional mínimo; con el filtro, ninguna de las dos direcciones tiene liquidez suficiente. Aplicarle el mismo chequeo de integridad que reveló los problemas en NotBank antes de confiar en pares nuevos |

## Servicios / APIs

_(vacío por ahora — proveedores de datos históricos, si hicieran falta más adelante, van acá)_

## Servicios / APIs

_(vacío por ahora)_
