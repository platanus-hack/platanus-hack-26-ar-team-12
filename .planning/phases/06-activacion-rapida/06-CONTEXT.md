# Phase 6 — Context

**Phase:** 6 — Activación rápida (opcional)
**Captured:** 2026-05-09
**Status:** Planned, opcional según tiempo. Solo se ejecuta si Phases 3-5 cierran con margen.

## Why this phase

Tap en la burbuja flotante funciona pero requiere coordinación motora fina (encontrar la burbuja, tocarla precisamente). Para adultos mayores con dificultad motriz, queremos una segunda vía más fácil. Hay dos opciones:

1. **Botón físico** — mantener pulsado el volumen down 2s lanza Beto. Ventajas: hardware, funciona con pantalla apagada (semi), no falla por tremor de mano. Desventajas: choca con apps que también escuchan keys.
2. **Wake word** — *"Hola Beto"* lanza captura. Ventajas: la más natural. Desventajas: licencias (Porcupine non-commercial), batería, requiere mic siempre encendido o key-spotting limitado.

**Decisión:** botón físico es mínimo viable (Plan 06-01). Wake word es spike-first (Plan 06-02) — solo se implementa si el spike de 30 min sale viable.

## What this phase delivers

Si Phase 6 entra:

> El usuario puede activar Beto **sin tocar la pantalla**. Mantiene apretado el botón de volumen-down 2 segundos (con la app de Beto corriendo en background) y la burbuja entra automáticamente en modo `LISTENING`. Si el spike de wake word sale viable, también puede decir *"Hola Beto"* con la pantalla activa.

Si Phase 6 NO entra: la activación sigue siendo solo tap en la burbuja, que es funcional para la demo principal.

## Plans

| Plan | Wave | Depends on | Goal |
|---|---|---|---|
| 06-01 | 1 | — | Activación por mantener pulsado volumen-down 2s |
| 06-02 | 1 | — | Spike de wake word (30 min) + implementación condicional |

## Decisions log (Phase 6 specific)

| Decision | Reason |
|---|---|
| Vol-down 2s en lugar de vol-up | Vol-up suele estar mapeado a otras cosas (cámara). Vol-down menos colisiones. |
| Implementación vía `BetoAccessibilityService.onKeyEvent` | El AccessibilityService ya está activo y recibe key events globales. Sin permisos extra. |
| Wake word es spike-first, no commit | Riesgo de licencia + batería. Si el spike no sale viable rápido, descartamos sin pena. |
| Ambas activaciones reusan `VoiceCaptureActivity` | Sin duplicar pipeline. Solo el trigger cambia. |

## Out of scope for Phase 6

- Activación por sacudir el celular (acelerómetro) — gimmick.
- Activación al detectar caída del celular — feature de seguridad complejo, v2.
- Wake word continuo con mic siempre on — incompatible con batería.

## Dependencies on prior phases

- Phase 1: `BetoAccessibilityService` activo.
- Phase 2: `VoiceCaptureActivity` capaz de lanzarse arbitrariamente.
- `AgentBus` para emitir el trigger de captura igual que el tap.
