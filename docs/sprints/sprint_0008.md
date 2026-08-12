---
sprint: 8
titulo: "Fee real de NotBank (no estimada), por tipo de par"
etapa: 2
---

# Sprint 0008 — Fee real de NotBank (no estimada), por tipo de par

## Objetivo
Reemplazar la estimación conservadora de la fee de NotBank (0,60% flat, Sprint 0006) por su valor real — cerrando la última tarea pendiente de la cuenta de fees.

## Alcance
- Buscar la fee real de NotBank de forma intensa antes de que Marcelo la revisara a mano en su cuenta.
- Actualizar `ExchangeFees`/`NetSpread` para reflejarla — incluye un cambio de forma: la fee de NotBank no es plana, depende del par.
- _(Fuera de alcance: confirmar que la cuenta de Marcelo está efectivamente en el tier base — queda como verificación suya, no se puede confirmar sin login.)_

## Decisiones
- **La página de tarifas es una SPA — la data real está en su API, no en el HTML.** `notbank.exchange/es-cl/tarifas` carga los números vía JavaScript desde `GET https://api.notbank.exchange/api/nb/instruments/fees` (pública, sin auth, la misma que usa la propia página). Se encontró rastreando el bundle de JS de la página (`taxes.js`), no adivinando.
- **La fee no es plana — depende de la categoría del par.** NotBank separa **CRYPTO-FIAT** (par cotizado en USDT/CLP/COP/PEN) de **CRYPTO-CRYPTO** (cotizado en otra cripto, ej. LTC/BTC), cada una con su propia escala de tiers por volumen de 30 días:

  | Categoría | Tier (volumen 30d) | Maker | Taker |
  | --- | --- | --- | --- |
  | CRYPTO-FIAT | 0 – 10.000 USD | 0,10% | **0,49%** |
  | CRYPTO-FIAT | 10.000 – 100.000 | 0,05% | 0,25% |
  | CRYPTO-FIAT | 100.000 – 1.000.000 | 0,00% | 0,19% |
  | CRYPTO-FIAT | > 1.000.000 | -0,05% | 0,15% |
  | CRYPTO-CRYPTO | 0 – 10.000 USD | 0,12% | **0,14%** |
  | CRYPTO-CRYPTO | 10.000 – 100.000 | 0,10% | 0,12% |
  | CRYPTO-CRYPTO | 100.000 – 1.000.000 | 0,08% | 0,11% |
  | CRYPTO-CRYPTO | > 1.000.000 | 0,06% | 0,10% |

- **Se usa el tier base (0-10.000 USD)** — el esperable para una cuenta de exploración sin trading real. Pendiente que Marcelo confirme que no está en un tier más alto por volumen histórico previo a este proyecto.
- **`ExchangeFees.takerFee` y `NetSpread.evaluate` ganan un parámetro de moneda de cotización.** Antes de este sprint solo importaba el exchange; ahora, solo para NotBank, también importa el par. `TrackedAsset.quoteCurrency()` deriva esto del propio `label` en vez de que cada lugar lo repita.

## Tareas
- [x] Encontrar la fee real de NotBank (API pública de tarifas, no estimación ni fuente de terceros)
- [x] `ExchangeFees`: fee de NotBank por categoría de par
- [x] `NetSpread`/`OverlapCheck`/`SpreadWatcher`: pasar la moneda de cotización
- [x] Tests actualizados con los valores reales
- [x] Verificación en vivo de ambos programas

## Sprint Review
**Cómo probar:** `mvn test`; `mvn compile exec:java -Dexec.mainClass=com.cryptobot.OverlapCheck` y confirmar `fees 0.6900%` en los pares USDT (Poloniex+NotBank) y `fees 0.9400%` en LTC/BTC (Buda+NotBank).

**Debe cumplir:**
- [x] La fee de NotBank ya no es una estimación — tiene fuente verificable (la propia API del exchange)
- [x] El cálculo distingue correctamente CRYPTO-FIAT de CRYPTO-CRYPTO
- [x] Ninguna conclusión de sprints anteriores cambia (todo seguía siendo negativo con la estimación; con el valor real, sigue siéndolo — algunos casos incluso más cerca de cero, ninguno cruza a positivo)

## Cierre

La fee real resultó **más baja** que la estimación conservadora en ambas categorías (0,49% vs. 0,60% estimado; 0,14% vs. 0,60% en el caso CRYPTO-CRYPTO, mucho más bajo) — así que no había estado sobrevendiendo "no hay arbitraje" por una fee inflada. Con el número real, ETH/USDT (YoBit → Poloniex) se acercó a cero (bruto +0,23%, neto -0,17%) pero sigue sin cruzar a positivo.

Con esto, la cuenta de fees queda cerrada para los 4 exchanges — ya no hay ningún número estimado o pendiente de confirmar en `ExchangeFees` (solo falta que Marcelo confirme su tier real, que no cambia ninguna conclusión salvo que esté en un tier mucho más alto del esperable). Siguiente paso: la corrida larga con los 4 exchanges que quedó pendiente del Sprint 0007.
