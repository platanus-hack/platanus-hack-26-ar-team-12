# Phase 2: Vertical Slice Minimo (Plan C Offline) - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase entrega el flujo observable minimo que prueba la tesis de Beto sin depender de red ni LLM:

- Tap corto en la burbuja flotante dispara captura de voz en es-AR.
- Android STT devuelve un transcript final al sistema en menos de 3 segundos desde el tap.
- `DeterministicMatcher` reconoce la familia de comandos del guion para WhatsApp, resuelve contacto y mensaje sin LLM.
- `DemoContacts.kt` contiene el contacto demo "Mi nieto" con aliases hardcodeados y numero E.164.
- `send_whatsapp(contact, message)` abre WhatsApp regular con el mensaje prellenado.
- Beto confirma por voz antes de abrir WhatsApp, reporta exito si Android acepta el intent, y falla con una frase calida si algo no sale.

**Out of scope para esta fase:** LLM real, tool calling cloud, auto-send por Accessibility, busqueda en contactos, aprendizaje de relaciones/contactos, WhatsApp Business, loop agentico de fallback, ElevenLabs o cualquier TTS con red, Modo Companero.

</domain>

<decisions>
## Implementation Decisions

### STT Handoff
- **D-01:** La captura de voz usa una `VoiceCaptureActivity` transparente que hostea el recognizer nativo de Android (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`) con idioma `es-AR`. Se acepta que aparezca la UI del sistema durante la demo; la prioridad es confiabilidad y velocidad.
- **D-02:** Si STT devuelve texto vacio, cancelado o no usable, Beto hace **un solo retry calido**: "No te escuche bien. Probemos de nuevo, dale." Si el retry tambien falla, corta el flujo con la frase de fallo general.
- **D-03:** El resto del sistema recibe solo el mejor transcript final, emitido por `AgentBus` como evento tipo `VoiceCaptured(text)`. No hay partial transcripts en Phase 2.
- **D-04:** El success criterion de 3 segundos se mide como **tap en burbuja -> transcript final emitido**. La apertura de WhatsApp se valida aparte por smoke test/manual demo.

### Matcher + Contacts
- **D-05:** `DeterministicMatcher` cubre la **familia de frases del guion**, no lenguaje natural arbitrario. Debe reconocer variantes cercanas de "mandale/avisale/decile/dile a mi nieto que ya llegue".
- **D-06:** `DemoContacts.kt` usa un alias map hardcodeado: `nieto`, `mi nieto` y el nombre real del contacto de demo resuelven al mismo numero E.164 seedado en el telefono.
- **D-07:** La extraccion del mensaje soporta varios verbos y conectores en espanol argentino mediante reglas deterministas/regex. No se limita a "todo despues de que", pero tampoco intenta parseo generico con LLM.
- **D-08:** Si la frase esta cerca pero falta contacto o mensaje, Beto hace **una aclaracion corta por voz** ("A quien le aviso?" o "Que queres que le diga?"), captura una respuesta adicional y luego completa o falla calidamente. No hay loops largos.

### WhatsApp Execution
- **D-09:** Phase 2 solo abre WhatsApp con el mensaje prellenado. No auto-envia el mensaje.
- **D-10:** El intent usa el paquete estricto `com.whatsapp` y la URL `wa.me/PHONE?text=...` o equivalente seguro. WhatsApp Business no entra en esta fase.
- **D-11:** Exito significa que Android acepta/lanza el intent sin exception. No se exige detectar que WhatsApp quedo en foreground ni inspeccionar el campo de texto.
- **D-12:** Si el intent falla por app faltante, numero malformado o launch exception, Beto da una frase calida y se detiene. No abre Play Store, Settings ni fallback agentico en Phase 2.

### TTS + Failure Tone
- **D-13:** Antes de lanzar WhatsApp, Beto dice exactamente: **"Abro WhatsApp con el mensaje para tu nieto."** Esta frase evita prometer que el mensaje ya fue enviado.
- **D-14:** Despues de que Android acepta el intent, Beto dice exactamente: **"Listo, te deje el mensaje preparado."**
- **D-15:** Si WhatsApp no se puede abrir, Beto dice exactamente: **"No pude abrir WhatsApp. Probemos de nuevo en un ratito, dale."**
- **D-16:** Si STT o matcher fallan despues del retry/aclaracion, Beto dice exactamente: **"Perdon, no te entendi bien. Probemos de nuevo, dale."**
- **D-17:** Phase 2 mantiene **Android native TTS only**. Aunque hay creditos de ElevenLabs, no se usan en esta slice offline-first. ElevenLabs queda fuera para no sumar red, latencia ni assets extra al Plan C.

### Claude's Discretion
- Forma exacta de las regex y normalizacion (`lowercase`, tildes, puntuacion, filler words) queda a criterio del planner/implementador mientras preserve la familia de comandos del guion.
- Nombres exactos de sealed classes adicionales (`VoiceCaptured`, `ActionExecuted`, `ToolFailed`, etc.) pueden ajustarse al contrato final de Phase 1, pero el flujo debe seguir pasando por `AgentBus`.
- Instrumentacion de latencia tap -> transcript puede ser logs Timber o timestamp en eventos, lo que sea mas simple para validar el criterio de 3 segundos.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project-level
- `.planning/PROJECT.md` — Vision, core value, constraints, out-of-scope boundaries, and demo priorities.
- `.planning/REQUIREMENTS.md` — Phase 2 requirement IDs: `OVERLAY-03`, `VOICE-03`, `VOICE-04`, `VOICE-05`, `LLM-04`, `ACT-01`, `ACT-02`, `ACT-06`.
- `.planning/ROADMAP.md` — Phase 2 goal and success criteria.
- `.planning/STATE.md` — Current planning state and carry-forward decisions.

### Prior Phase Context
- `.planning/phases/01-foundation-sync-de-hora-0/01-CONTEXT.md` — Locked project layout, `AgentBus`, `TtsManager`, overlay entry point, logging tags, manifest/service assumptions.

### Research
- `.planning/research/SUMMARY.md` — Consolidated hackathon architecture and Plan C offline-first rationale.
- `.planning/research/STACK.md` — Android/Kotlin stack, native STT/TTS, Firebase/Gemini decisions for later phases, and exclusions.
- `.planning/research/ARCHITECTURE.md` — Component boundaries for service, overlay, voice capture, bus, and action dispatch.
- `.planning/research/PITFALLS.md` — Demo risks, especially STT/TTS races, Android service behavior, and offline fallback constraints.
- `.planning/research/FEATURES.md` — Feature classification and anti-features.

### Project Conventions
- `CLAUDE.md` — Global implementation rules, Beto voice/tone, modularity, logging, and Android guardrails.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `project-logo.png` — Already selected in Phase 1 as the bubble identity; Phase 2 reuses the bubble as the single tap entry point.
- `.planning/phases/01-foundation-sync-de-hora-0/01-CONTEXT.md` — Defines the contracts Phase 2 builds on: `AgentBus`, `TtsManager`, `BetoForegroundService`, overlay tap event, and logging tags.

### Established Patterns
- There is no Android source tree yet in the repo. Phase 2 planning must assume Phase 1 creates the `android/` project and contracts before Phase 2 implementation starts.
- Cross-component communication should use `AgentBus`, not Binder, broadcasts, or direct service coupling.
- Demo reliability beats production completeness. Deterministic/hardcoded behavior is acceptable when it protects the live pitch.

### Integration Points
- Overlay tap from Phase 1 emits/handles a tap event that launches `VoiceCaptureActivity`.
- `VoiceCaptureActivity` emits final transcript into `AgentBus`.
- `DeterministicMatcher` consumes the transcript and emits/routes a WhatsApp action.
- `ActionDispatcher` or Phase 2 equivalent calls `send_whatsapp`.
- `TtsManager` speaks all confirmation, retry, success, and failure phrases.
- Timber tags relevant to this phase: `Beto-STT`, `Beto-Intent`, `Beto-Action`, `Beto-TTS`, `Beto-Bus`.

</code_context>

<specifics>
## Specific Ideas

- Demo command target: **"mandale a mi nieto que ya llegue"** and close variants.
- Pre-launch phrase: **"Abro WhatsApp con el mensaje para tu nieto."**
- Success phrase: **"Listo, te deje el mensaje preparado."**
- WhatsApp failure phrase: **"No pude abrir WhatsApp. Probemos de nuevo en un ratito, dale."**
- STT/matcher final failure phrase: **"Perdon, no te entendi bien. Probemos de nuevo, dale."**
- STT empty retry phrase: **"No te escuche bien. Probemos de nuevo, dale."**

</specifics>

<deferred>
## Deferred Ideas

- If Beto does not know a relationship like "nieto", search Android contacts, ask the user for the real person, suggest matching contacts, then save that alias for next time. This is a future relationship-learning/contact-disambiguation capability, not Phase 2.
- ElevenLabs voice generation may be useful later for a more polished Beto voice, but Phase 2 deliberately uses Android native TTS only to preserve the offline Plan C path.
- Auto-send through Accessibility and foreground/message-field verification belong to later agentic/UX phases, not this prefilled-WhatsApp slice.

</deferred>

---

*Phase: 2-Vertical Slice Minimo (Plan C Offline)*
*Context gathered: 2026-05-09*
