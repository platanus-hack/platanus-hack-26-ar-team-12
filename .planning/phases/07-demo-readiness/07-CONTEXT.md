# Phase 7 — Context

**Phase:** 7 — Demo Readiness
**Captured:** 2026-05-09
**Status:** Planned, ejecuta 4-6 horas antes del pitch

## Why this phase

> "La mayoría de hackathons no fallan por código mediocre. Fallan en la demo en vivo por algo prevenible — una app que se queda colgada, un teléfono que se desconecta del wifi, un permiso que se desactivó solo, una voz TTS que no estaba descargada, un APK que se actualizó 5 minutos antes y rompió algo."

Phase 7 no es de código. Es la fase operativa donde el equipo asegura que la demo en vivo va a salir bien aunque pase algo inesperado. Freeze del APK, hot-spare phone, hotspot dedicado, ensayos, video de respaldo, checklist físico, submission.

## What this phase delivers

Al terminar Phase 7, el equipo tiene:

1. APK debug instalado en **dos** teléfonos idénticamente configurados, sin builds en las últimas 4+ horas.
2. Hotspot personal dedicado (no Wi-Fi del venue) con cuota suficiente para 30 min de demo continuo.
3. Ambos teléfonos seedeados: contactos demo (`Pedro/nieto`, `Carlos/hijo`, `Dra López`), TTS es-AR descargado, AccessibilityService + Overlay + Battery + READ_CONTACTS otorgados manualmente, voz de Beto seleccionada y verificada masculina argentina.
4. Guion de 3-5 min ensayado mínimo 5 veces extremo a extremo, incluyendo recuperación de errores.
5. Video pre-grabado del guion completo (3 min) como respaldo absoluto.
6. Checklist físico en papel con 16 ítems chequeable antes de subir al escenario.
7. `platanus-hack-project.json` y README submission-ready.

## Plans

| Plan | Wave | Depends on | Goal |
|---|---|---|---|
| 07-01 | 1 | Phase 4 (mínimo) | Demo readiness completo: freeze + hot-spare + hotspot + ensayos + video + checklist + submission |

(Solo 1 plan grande — Phase 7 es operativa, no admite paralelización útil.)

## Decisions log (Phase 7 specific)

| Decision | Reason |
|---|---|
| Hot-spare phone obligatorio | Costo mínimo, salvavidas máximo. Si el principal muere en escenario, switch en 5s. |
| Hotspot personal en lugar de Wi-Fi del venue | Wi-Fi de venues hackathon suele saturarse o tener firewalls. |
| Video pre-grabado de respaldo | Si todo falla en vivo, el video cuenta la historia igual. Subimos del guion ensayado con mejor toma. |
| Guion ensayado ≥5x | Memoria muscular en el escenario. Improvisación = bug. |
| Checklist físico en papel | Off-device, no falla. 16 ítems verificables con boli. |
| Phase 5 y 6 son nice-to-have, no bloqueantes | Si hay tiempo entran. Sino, demo con Phase 4 todavía es contundente. |

## Out of scope for Phase 7

- Optimización de performance (si llegamos acá con un APK slow, no se arregla en 4h).
- Refactor de código.
- Features nuevas.
- Tests adicionales más allá del guion.
