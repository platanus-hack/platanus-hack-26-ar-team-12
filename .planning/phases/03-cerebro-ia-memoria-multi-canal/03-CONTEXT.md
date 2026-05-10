# Phase 3 — Context

**Phase:** 3 — Cerebro IA + Memoria + Multi-canal con aprendizaje
**Captured:** 2026-05-09
**Status:** Planning → Execute

## Why this phase

Phase 2 entregó un Plan C offline robusto pero rígido: un solo comando ("mandale a mi nieto que ya llegué"), un solo canal (WhatsApp), un solo contacto hardcoded. Es la base confiable, pero no prueba la tesis del producto: "agente que entiende y opera el celular".

Phase 3 da el salto. **Es el inflection point donde Beto pasa de script duro a agente IA real.** Sin Phase 3, la demo es impresionante pero limitada. Con Phase 3, Beto entiende lenguaje natural, usa la libreta del usuario, recuerda lo que aprende, y maneja varios canales (WhatsApp, SMS, llamada, Maps) con un router que pregunta cuando le falta info y guarda la respuesta para no repreguntar.

## What this phase delivers

End-to-end del usuario después de Phase 3:

> El usuario dice *"Beto, llamá a Pedro"*. Beto resuelve "Pedro" contra la libreta del sistema, encuentra coincidencia, llama. Si encuentra varios "Pedro", pregunta "¿A cuál? Pedro Gómez o Pedro Suárez". Guarda la elección.
>
> El usuario dice *"Mandale a mi nieto que ya llegué"*. La primera vez, Beto no sabe quién es "mi nieto" → pregunta cálidamente "¿Quién es tu nieto?" → el user dice "Juan" → Beto resuelve "Juan" en la libreta → encuentra → guarda la asociación `nieto = Juan` → ejecuta. La segunda vez, no pregunta.
>
> El usuario dice *"Mandale a Juan que pase a buscarme"*. Si Beto no tiene preferencia de canal para Juan, pregunta "¿Por WhatsApp, SMS o llamada?" → el user dice "WhatsApp" → Beto guarda preferencia → manda WhatsApp. La segunda vez, no pregunta.
>
> Si la red cae, el matcher determinista de Phase 2 toma la posta para el comando del guion principal sin que el usuario lo note.

## Key constraints from upstream

- **PROJECT.md (2026-05-09):** Multi-canal con aprendizaje, memoria persistida (`EncryptedSharedPreferences`), STT corrector vía Gemini, sanitizer mínimo regex.
- **CLAUDE.md:** vocabulario simple, voseo argentino, frases cortas, fallbacks elegantes (red caída → matcher), economía de tokens.
- **Stack (PROJECT.md):** Gemini 2.5 Flash vía `com.google.firebase:firebase-ai`. Modelo Lite para Compañero (Phase 4). Anthropic comentado como fallback.
- **Privacidad:** sanitización on-device antes de cualquier payload al LLM. Sin NER profundo.
- **Equipo:** 5 devs en paralelo. Wave 1 tiene 3 plans independientes (03-01, 03-03, 03-04) — cada dev puede tomar uno sin coordinarse.

## Plans

| Plan | Wave | Depends on | Goal |
|---|---|---|---|
| 03-01 | 1 | — | Gemini client + structured tool calling + sanitizer |
| 03-02 | 2 | 03-01 | STT corrector vía Gemini + on-device recognizer (API 31+) |
| 03-03 | 1 | — | Acceso a contactos del sistema (`ContactsContract`) + permiso runtime |
| 03-04 | 1 | — | Memoria del usuario en `EncryptedSharedPreferences` |
| 03-05 | 3 | 03-01, 03-03, 03-04 | Action router multi-canal con aprendizaje |

## Decisions log (Phase 3 specific)

| Decision | Reason |
|---|---|
| `LlmClient` interface con dos impls (Gemini default, Anthropic comentado) | Si Gemini decepciona en es-AR / tool calling, switch de 1 línea. |
| `temperature: 0` para tool calling, `temperature: 0.4` para Compañero (Phase 4) | Determinístico para acciones, natural para charla. |
| Sanitizer aplicado en `LlmClient`, no en cada caller | Garantiza que ningún payload sale sin filtrar. |
| `EncryptedSharedPreferences` (no Room/SQLite) | Hackathon scope. JSON serializado con `kotlinx.serialization` alcanza. |
| Memoria es JSON único (`user_memory_v1`), no múltiples claves | Lectura atómica. Mutex en escritura. |
| `ContactRepository` lee `ContactsContract` directamente, no copia local | Sin sincronización ni cache invalidation. La libreta es la fuente de verdad. |
| STT corrector solo se invoca si la confianza del recognizer está baja **o** si el matcher determinista no matchea | No se usa siempre — overhead de latencia. |
| On-device recognizer (`createOnDeviceSpeechRecognizer`) preferido sobre cloud cuando esté disponible | Mejor para acentos / voces senior, sin red. Fallback al cloud si on-device no está. |
| `ContactClarifier` y `ChannelClarifier` reutilizan el mismo `VoiceCaptureActivity` que el flujo principal | No duplicar pipeline de STT. Solo cambia el prompt TTS previo. |

## Dependencies on Phase 2 code

Phase 3 reusa y extiende:

- `VoiceCaptureActivity` — agrega segunda invocación para clarificadores
- `TtsManager` — usado por clarificadores
- `AgentBus` + `AgentEvents` — se suman eventos: `ClarificationRequested`, `ClarificationReceived`, `MemoryUpdated`
- `DeterministicMatcher` — sigue activo como Plan C, con precedencia sobre el LLM cuando matchea
- `IntentBranch` — extendido con métodos para SMS, llamada, Maps
- `PlanCController` — refactorizado a `ActionDispatcher` que rutea entre matcher determinista y LLM router

## Out of scope for Phase 3

- Loop agéntico iterativo — descopeado permanente.
- Modo Compañero (chat conversacional) — Phase 4.
- Modo Guía con gestos — Phase 4.
- Anti-fraude — Phase 5.
- Wake word / activación física — Phase 6.
- Memoria con embeddings — v2.
- Voz neural premium — Phase 4 (VOICE-HUM-01).
- Respuestas LLM-generated del flujo de acción — Phase 4 (VOICE-HUM-02). En Phase 3 se siguen usando frases cortas hardcoded para confirmación/éxito/fallo, igual que Phase 2.
