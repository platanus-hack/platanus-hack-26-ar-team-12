---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
stopped_at: Phase 3 planning — roadmap reframed
last_updated: "2026-05-09T22:00:00.000Z"
last_activity: 2026-05-10 -- Phase 4 completa (4 plans). 125 tests verdes, APK 33MB.
progress:
  total_phases: 7
  completed_phases: 4
  total_plans: 20
  completed_plans: 15
  percent: 75
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-09 — full reframe)

**Core value:** Beto es un asistente conversacional que (1) opera el celular por voz, (2) aprende y recuerda al usuario, (3) guía con gestos en pantalla, (4) acompaña conversando, y (5) detecta intentos de estafa cuando se lo consultan — todo en español argentino con tono cálido.

**Current focus:** Phase 3 — Cerebro IA + Memoria + Multi-canal con aprendizaje.

## Current Position

Phase: 3 (Cerebro IA + Memoria + Multi-canal) — PLANNING
Plan: 0 of 5 (plans being written)
Status: Plans being authored after roadmap reframe
Last activity: 2026-05-09 -- Roadmap reframed (5 → 7 phases) and old Phase 3/4 deleted

Progress overall: [██░░░░░░░░] 29% (2 of 7 phases complete)

## Performance Metrics

**Velocity:**

- Total plans completed: 6 (Phase 1: 3, Phase 2: 3)
- Phase 3 plans pending: 5
- Phase 4-7 plans pending: TBD

**By Phase:**

| Phase | Plans | Status |
|-------|-------|--------|
| 1. Foundation | 3/3 | ✓ Done |
| 2. Plan C offline | 3/3 | ✓ Done |
| 3. Cerebro IA + Memoria + Multi-canal | 0/5 | Planning |
| 4. Voz humana + UX + Compañero + Guía | 0/4 | Plans escritos |
| 5. Anti-fraude reactivo | 0/2 | Plans escritos |
| 6. Activación rápida (opcional) | 0/2 | Plans escritos |
| 7. Demo Readiness | 0/1 | Plans escritos |

**Recent Trend:**

- Phase 1 + Phase 2: completados en sprint inicial (no medido formalmente).
- Phase 3: planning post-reframe en curso.

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- 2026-05-09: Roadmap reformulado (5 → 7 phases) post-Phase 2 para reflejar visión expandida (memoria, multi-canal, guía con gestos, anti-fraude reactivo, voz humana).
- 2026-05-09: Loop agéntico universal completamente descopeado (out of scope permanente). Modo Guía con gestos lo reemplaza como diferenciador técnico.
- 2026-05-09: Multi-canal con aprendizaje reemplaza WhatsApp-only — Beto pregunta el medio la primera vez y guarda preferencia.
- 2026-05-09: Voz Beto pasa a ser LLM-generated (no PhraseBank fijo) para tono más humano, con cache por hash para latencia.
- 2026-05-09: Memoria persistida con `EncryptedSharedPreferences` + JSON (kotlinx.serialization). No Room/SQLite, no embeddings.
- 2026-05-09: Anti-fraude reactivo (no proactivo) — el user pregunta, Beto analiza. Out of scope: monitoreo continuo.
- 2026-05-09: Wake word a Phase 6 opcional. Botón físico (vol-down 2s) como mínimo viable.
- 2026-05-09: Soporte de computadora descopeado permanentemente.

### Pending Todos

- ✓ Phase 3 plans escritos (03-01 a 03-05).
- ✓ Phase 4 plans escritos (04-01 a 04-04).
- ✓ Phase 5 plans escritos (05-01, 05-02).
- ✓ Phase 6 plans escritos (06-01, 06-02 spike-conditional).
- ✓ Phase 7 plan escrito (07-01).
- Pendiente: ejecutar Phase 3 (Wave 1 paraleliza 03-01 + 03-03 + 03-04).
- Sincronizar `docs/STATUS.md` cuando Phase 3 esté en ejecución.

### Blockers / Concerns

- Phase 3 / 4 dependen de comportamiento real de Gemini 2.5 Flash en es-AR — solo medible al smoke test post-Phase 3.1. Mitigación: `LlmClient` interface con dos impls (Gemini default, Anthropic fallback comentado).
- Voz neural premium TTS depende del device de demo — verificar en el teléfono real al inicio de Phase 4.
- Modo Guía con gestos depende de poder localizar Views target por texto/id en apps externas (WhatsApp, etc.) vía AccessibilityService — alguno requerirá heurísticas específicas.
- Anti-fraude requiere prompt que no genere falsos positivos sobre mensajes legítimos — necesita curado de ejemplos.

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| LLM strategy | Anthropic fallback (anthropic-java) | Comentado en code, activable si Gemini falla | 2026-05-09 |
| Wake word | Implementación real ("Hola Beto") | Phase 6 opcional, requiere spike previo | 2026-05-09 |
| Anti-fraude proactivo | Monitor continuo de notificaciones/SMS | Out of scope v1, candidato v2 | 2026-05-09 |
| RAG memoria | Embeddings + retrieval para perfil grande | Out of scope v1 | 2026-05-09 |
| Soporte computadora | Plataforma desktop | Out of scope permanente | 2026-05-09 |

## Session Continuity

Last session: 2026-05-09T22:00:00.000Z
Stopped at: Phase 3 planning — roadmap reframed, plans 03-01..03-05 about to be written
Resume file: .planning/phases/03-cerebro-ia-memoria-multi-canal/ (about to be created)
