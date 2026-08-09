# Cryptobot — Contexto del Proyecto

## Qué es
Exploración de un bot de arbitraje de criptomonedas: detectar diferencias de precio del mismo par entre distintos exchanges (cross-exchange) o arbitraje triangular dentro de un mismo exchange, que superen fees y fricción.

Las **etapas** ya están definidas en [docs/roadmap.md](docs/roadmap.md) (1. Estado del arte → 2. Análisis de viabilidad → 3. pendiente) y el primer sprint está planificado. La **visión** ([docs/vision.md](docs/vision.md)) sigue pendiente de completar con la motivación y los antecedentes de una experiencia previa de Marcelo (~10 años atrás, arbitraje real en Bittrex/Cex.io/Poloniex) — no asumir ni inventar esa parte.

## Quién construye (Marcelo)
- Senior backend engineer — muy avanzado en Java/Spring Boot y sistemas distribuidos; mucha experiencia con LLMs y APIs de AI. Python lo maneja pero no es su zona de confort (explicar lo específico de Python con más detalle si el stack termina siendo Python).
- Comunicación en español.

## Filosofía de construcción
_(por definir junto con la visión — no asumir "software primero" ni ningún otro principio hasta confirmarlo con Marcelo)_

Una regla ya firme, no negociable, aplica desde ya: **ninguna decisión que involucre fondos reales o API keys con permiso de trading se ejecuta sin autorización explícita**, y solo después de pasar por simulación/paper trading.

## Cómo trabajamos
- **Ritual de inicio de sesión:** preguntar *"¿qué funciona hoy y qué quedó pendiente?"*, luego proponer el **siguiente paso mínimo** que agregue valor visible.
- **Doc-driven + sprints incrementales.** Decisiones de stack/herramientas/exchanges just-in-time; nada decidido de antemano. Código + docs juntos: una tarea no está completa si dejó documentación desactualizada.
- **Git:** nunca hacer `commit` ni `push` sin autorización explícita de Marcelo.
- Método completo, convenciones y principios de documentación: `docs/metodologia.md`.

## Dónde está todo
- **Documentación:** índice maestro en **`docs/hub.md`** (visión, roadmap, metodología, entorno, sprints). Empezar siempre por ahí.
- **Código:** todavía no existe. Se crea (y este archivo se actualiza) cuando el primer sprint lo defina.
