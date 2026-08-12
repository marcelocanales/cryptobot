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
| **Poloniex** | ✅ | Internacional, grande pero no top-3 global (Binance/Coinbase) | `GET https://api.poloniex.com/markets/{symbol}/orderBook` — pública, sin auth. Params: `symbol` (formato `BASE_QUOTE`, ej. `LTC_USDT`), `limit` (5/10/20/50/100/150). **Conector real en `code/cryptobot`** (`PoloniexConnector`), verificado en vivo desde el Sprint 0002 | Fees confirmadas: 0,20% maker / 0,20% taker en tier base (iguales — sin ventaja de ser maker hasta subir de volumen). Books de pares chicos (AAVE/USDT) vistos con huecos reales de liquidez en un momento dado |
| **NotBank** (ex-CryptoMKT) | ✅ | Regional/LatAm | Plataforma tipo AlphaPoint. Host real (no está en la documentación pública, confirmado a mano): `https://api.notbank.exchange`. `POST /AP/GetInstruments` `{"OMSId":1}` → lista de pares con su `InstrumentId` numérico (el símbolo, ej. `LTCUSDT`, no alcanza solo). `POST /AP/GetL2Snapshot` `{"OMSId":1,"InstrumentId":<id>,"Depth":10}` → array de filas posicionales `[MDUpdateId, Accounts, ActionDateTime, ActionType, LastTradePrice, Orders, Price, InstrumentId, Quantity, Side]` (`Side`: 0=bid, 1=ask) — sin auth. **Conector real en `code/cryptobot`** (`NotBankConnector`), verificado en vivo desde el Sprint 0002 — el mejor bid obtenido coincidió exacto con lo visto a mano en la UI | Liquidez real y buena en USDT/CLP. En pares de bajo volumen (DOGE/USDT, AAVE/USDT, GRAM/USDT) se observaron señales fuertes de datos no genuinos: volumen 24h en 0 con book poblado, "Recent Trades" en cantidad 0, y cantidades idénticas repetidas en múltiples niveles de precio (ver [wash trading](<glosario.md#Wash trading>) y la síntesis en [estrategias/README.md](estrategias/README.md)) — **verificar integridad de datos por par antes de confiar en el book** |
| **BudaPRO** (Buda.com) | ✅ | Chileno, establecido | _(por confirmar)_ | Book real y consistente en USDT/CLP — comparado con NotBank el mismo día, sin prima detectable |
| **YoBit** | ✅ | Particular — lista muchos tokens chicos/poco conocidos | _(por confirmar)_ | Reputación históricamente cuestionada en la comunidad cripto — antes de darlo por buena fuente de datos, aplicarle el mismo chequeo de integridad que reveló los problemas en NotBank |

## Servicios / APIs

_(vacío por ahora — proveedores de datos históricos, si hicieran falta más adelante, van acá)_

## Servicios / APIs

_(vacío por ahora)_
