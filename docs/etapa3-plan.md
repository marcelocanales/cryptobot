# Etapa 3 — plan de exploración

**Documento vivo**, mismo tratamiento que [veredictos-etapa2.md](veredictos-etapa2.md): se actualiza a medida que cada fase avanza, no se reescribe la historia. Existe porque el propio [roadmap.md](roadmap.md) dejó la Etapa 3 "pendiente — se define solo si la Etapa 2 muestra señal positiva". Ya la mostró (ver [veredictos-etapa2.md](veredictos-etapa2.md): ZEC en spot cross-exchange, BNB/FIL/ETH en funding cross-exchange) — este documento es esa definición.

**Lo que Marcelo pidió:** llevar esto a factibilidad técnica — intentar el arbitraje con un monto mínimo (10 USD o menos) — antes de comprometerse a nada más grande. No es "abrir la Etapa 3 en serio" todavía, es la exploración mínima para saber si vale la pena.

## Las 4 fases

Cada fase gatea a la siguiente — no se salta ninguna sin que la anterior la justifique.

1. **Conectar Binance de solo lectura** ([Sprint 0028](sprints/sprint_0028.md)) — mismo patrón que los otros 6 exchanges, sin cuentas ni API keys de trading. Motivado por un hallazgo concreto del 2026-08-13, ver abajo.
2. **Monitorear el spread real Poloniex↔Binance unas horas** — confirmar que lo visto en el momento de conectar no es una foto rara, mismo principio que toda la Etapa 2 (nunca confiar en una sola medición).
3. **Paper trading** — simular la ejecución contra el book real (caminar el book, con una demora simulada realista antes de "ver" el precio) sin ninguna API key ni capital. Reusa `ParallelFetch`/`OrderBook`/`NetSpread` tal cual.
4. **Prueba real mínima** (10 USD o menos) — recién si las fases 2 y 3 dan motivo. Ver los principios de seguridad abajo.

## Hallazgos que motivan el orden (2026-08-13, verificados en vivo, no asumidos)

- **NotBank no tiene ZEC listado** — confirmado contra `POST /AP/GetInstruments` (110 instrumentos, ninguno con ZEC). No es que el spread no alcance ahí, es que no hay par que comparar.
- **Buda nunca cruzó a positivo contra nada en toda la corrida nocturna completa** (31.838 filas comparadas, 0 marcadas). **NotBank tuvo un solo parpadeo de ruido** (FIL/USDT, 1,1% de consistencia — mismo nivel que MANA, ya descartado en [veredictos-etapa2.md](veredictos-etapa2.md)).
- **Binance — nunca conectado al proyecto hasta ahora — mostró el spread más grande visto contra Poloniex en ZEC/USDT**: 8,09% bruto en vivo (Poloniex ask 458,08 / Binance bid 495,13), ~7,79% neto con fees reales (Poloniex 0,20% + Binance 0,10% taker VIP 0, confirmado contra `binance.com/en/fee/schedule`, no asumido).
- **Marcelo ya tiene cuenta habilitada para operar en Binance** — a diferencia de Bitfinex (venció, necesitaría renovar KYC) o CoinEx (nunca tuvo cuenta ahí). Cero fricción para las fases 2-4.
- **Chile confirmado sin restricción en Binance** (no está en su lista de países restringidos — Irán/Corea del Norte/Siria/Cuba/EE.UU./Canadá/Reino Unido).

Esto es lo que hizo cambiar el candidato de la fase 4 de "ZEC vía Bitfinex o CoinEx" (lo que se venía conversando) a "ZEC vía Binance" — no una preferencia, un dato medido.

## Principios de seguridad para la fase 4

Escritos ahora, antes de que exista una sola línea de código de ejecución — para que sean un compromiso, no una promesa de palabra:

- **API keys sin permiso de retiro** — solo trading, restringidas por IP si Binance lo permite. Así un bug o una key filtrada no puede sacar fondos, solo operar dentro de lo que el código permita.
- **Tope de notional fijo en el código**, no solo acordado verbalmente — ni un bug puede superarlo.
- **Inventario pre-posicionado en las dos puntas** (USDT en Poloniex + ZEC ya en Binance) para la primera prueba — separa a propósito "¿funciona la ejecución simultánea?" de "¿funciona la transferencia entre exchanges?" (el problema de rebalanceo sigue en el backlog desde el Sprint 0001, sin resolver — no se mezcla con esta prueba).
- **El objetivo es factibilidad de mecánica, no rentabilidad** — latencia real, si las dos patas efectivamente llenan, slippage real vs. lo modelado en [el simulador](veredictos-etapa2.md). Un resultado neto ligeramente negativo con datos limpios es un resultado válido y útil — no un fracaso.
- **Ningún visto bueno es global.** Conectar Binance de solo lectura no autoriza crear API keys de trading; crear las API keys no autoriza mover capital; ninguna fase se ejecuta sin que Marcelo dé el ok explícito para esa fase específica — mismo criterio no negociable de siempre (ver [metodologia.md](metodologia.md)).

## Estado

| Fase | Estado |
| --- | --- |
| 1 — Conectar Binance | 🔵 En curso ([Sprint 0028](sprints/sprint_0028.md)) |
| 2 — Monitorear el spread real | ⏳ Pendiente |
| 3 — Paper trading | ⏳ Pendiente |
| 4 — Prueba real mínima | ⏳ Pendiente |
