# Phase 3: Motor de Acciones Completo + Companero - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase expande el flujo de Beto desde el Plan C offline de WhatsApp hacia un motor de acciones completo y una experiencia conversacional simple:

- Mantiene el matcher determinista primero para comandos criticos de demo.
- Conecta Gemini para comandos arbitrarios en espanol argentino mediante tool calling estricto.
- Ejecuta llamadas, SMS y Maps ademas de WhatsApp.
- Sanitiza datos sensibles antes de cualquier llamada cloud y deja evidencia verificable en logs `Beto-LLM`.
- Resuelve contactos primero con `DemoContacts.kt` y luego, si hace falta, con Android Contacts.
- Long-press en la burbuja abre Modo Companero: una experiencia voice-first, conversacional, calida y simple.
- Desde Companero, Beto puede responder como chat o confirmar y ejecutar una accion sin que el usuario tenga que entender cambios de modo internos.

**Out of scope para esta fase:** loop agentico operativo, auto-operar pantallas por Accessibility como fallback, memoria personal persistente, configuracion de boton fisico lateral, historial de chat persistente, wake word, onboarding real de permisos, NER avanzado, vision con MediaProjection.

</domain>

<decisions>
## Implementation Decisions

### LLM Routing Contract
- **D-01:** Para los comandos criticos de demo, el ruteo es **deterministic first**. Frases top como "llama a mi hijo", "mandale un sms a Ana" y "abrime el mapa hasta la farmacia" deben pasar por matcher local antes de llamar a Gemini.
- **D-02:** Gemini se usa para phrasing arbitrario, variantes no exactas y comandos fuera de las familias deterministas. El usuario no debe notar si internamente se uso matcher o LLM.
- **D-03:** El Motor de Acciones usa `gemini-2.5-flash` con tool calling estricto y `temperature: 0` para reducir variacion en nombres de tool, campos y estructura.
- **D-04:** Si Gemini devuelve JSON malformado, un tool name desconocido o argumentos invalidos, Beto hace **un retry** con una instruccion de reparacion. Si el retry falla, puede caer a fallback determinista/cache solo si el texto original conserva la misma semantica explicita de accion/contacto/destino/mensaje.
- **D-05:** Ningun fallback puede sustituir contacto, destino, app, mensaje ni accion. Si el usuario dijo "Pedro" y Pedro no se resuelve, Beto no puede llamar a "hijo"; debe pedir aclaracion corta o fallar calidamente.
- **D-06:** La resolucion de contactos es `DemoContacts.kt` primero, Android Contacts segundo. Demo aliases ganan para confiabilidad; nombres arbitrarios como "Pedro" pueden resolverse por contactos del telefono.
- **D-07:** Si Android Contacts devuelve cero matches o multiples matches, Beto no adivina. Debe hacer una pregunta corta de aclaracion o fallar con tono calido.
- **D-08:** `ToolDescriptors.kt` puede incluir `agentic_perform_action` como descriptor de contrato compartido, pero Phase 3 no lo habilita. La allow-list del LLM para esta fase es exactamente: `send_whatsapp`, `make_call`, `send_sms`, `open_maps`.
- **D-09:** Si Gemini devuelve `agentic_perform_action` en Phase 3, `ActionDispatcher` debe rechazarlo, loguearlo con `Beto-LLM` / `Beto-Action`, y responder calidamente. El tool se habilita recien en Phase 4.

### Modo Companero UX
- **D-10:** Long-press en la burbuja abre Modo Companero y empieza a escuchar inmediatamente. Debe sentirse como "hablar con Beto", no como entrar a una pantalla compleja.
- **D-11:** Companero es voice-first pero conserva una barra de texto minima para escribir. No debe tener un boton gigante; el usuario objetivo ya lidia con pantallas chicas y complicadas.
- **D-12:** La UI de escucha debe usar una animacion de barras verticales estilo "Hey Google" para mostrar grabacion/escucha de forma estetica y clara.
- **D-13:** El sheet de Companero debe ser simple y liviano: chat/transcript, barra de texto minima y estado de escucha. Evitar settings, paneles pesados o controles que parezcan otra app.
- **D-14:** Companero puede responder de forma mas conversacional que el Motor de Acciones. Puede hacer follow-ups y dar respuestas mas completas cuando sirva, pero con tono argentino, vocabulario simple pero correcto, paciente, calmo y no aburrido.
- **D-15:** Companero usa `gemini-2.5-flash-lite` con temperatura conversacional moderada para tono natural. El Motor de Acciones mantiene `gemini-2.5-flash` con `temperature: 0`.
- **D-16:** Companero puede ejecutar acciones con confirmacion. Si el usuario dice dentro del chat "avisale a Ana..." o "llama a Pedro", Beto puede rutear internamente al Motor de Acciones, confirmar y ejecutar.
- **D-17:** La UX no debe hablar de "modo acciones" vs "modo companero". Internamente puede haber rutas separadas, pero para el adulto mayor debe sentirse como un solo Beto que entiende si tiene que conversar o hacer algo.
- **D-18:** El historial de Companero en Phase 3 es session-only: vive en el `ViewModel` mientras el sheet esta abierto y se descarta al cerrar.

### the agent's Discretion
- Forma exacta de las regex deterministas y umbrales de matching, siempre que preserven D-01 y no sustituyan entidades.
- Frases exactas de aclaracion/fallo calido para contactos ambiguos o desconocidos, siempre que sean cortas, argentinas, simples y no tecnicas.
- Implementacion visual exacta de las barras verticales de escucha, mientras sea clara, estetica, accesible y no ocupe la pantalla con un boton enorme.
- Si Companero confirma acciones por voz, texto o ambos queda a criterio del planner/implementador, pero debe haber una confirmacion antes de ejecutar.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project-level
- `.planning/PROJECT.md` - Vision, core value, constraints, out-of-scope boundaries, and demo priorities.
- `.planning/REQUIREMENTS.md` - Phase 3 requirement IDs: `ACT-03`, `ACT-04`, `ACT-05`, `LLM-01`, `LLM-02`, `LLM-03`, `LLM-05`, `LLM-06`, `PRIV-01`, `PRIV-02`, `OVERLAY-04`, `COMP-01`, `COMP-02`, `COMP-03`, `COMP-04`.
- `.planning/ROADMAP.md` - Phase 3 goal and success criteria.
- `.planning/STATE.md` - Current planning state and carry-forward decisions.

### Prior Phase Context
- `.planning/phases/01-foundation-sync-de-hora-0/01-CONTEXT.md` - Locked project layout, `AgentBus`, `TtsManager`, overlay entry point, logging tags, manifest/service assumptions.
- `.planning/phases/02-vertical-slice-minimo-plan-c-offline/02-CONTEXT.md` - Locked Phase 2 decisions for STT handoff, deterministic matcher, WhatsApp execution, TTS tone, and offline-first Plan C behavior.
- `.planning/phases/02-vertical-slice-minimo-plan-c-offline/02-RESEARCH.md` - Phase 2 technical research that grounds matcher/action flow and should not be broken by Phase 3.

### Research
- `.planning/research/SUMMARY.md` - Consolidated hackathon architecture and Plan C offline-first rationale.
- `.planning/research/STACK.md` - Android/Kotlin stack, Firebase AI Logic / Gemini model choices, native STT/TTS, and exclusions.
- `.planning/research/ARCHITECTURE.md` - Component boundaries for service, overlay, voice capture, bus, action dispatch, LLM, and future agentic loop.
- `.planning/research/PITFALLS.md` - Demo risks, especially LLM latency, Android service behavior, STT/TTS races, and offline fallback constraints.
- `.planning/research/FEATURES.md` - Feature classification and anti-features.

### Project Conventions
- `CLAUDE.md` - Global implementation rules, Beto voice/tone, modularity, logging, Android guardrails, and hackathon constraints.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `android/app/src/main/AndroidManifest.xml` - Existing Android manifest surface created by earlier phases; Phase 3 planning should verify required activities/services/permissions before adding new ones.
- `android/app/src/main/res/layout/overlay_bubble.xml` - Bubble entry point already exists; Phase 3 long-press behavior should connect from this overlay path rather than inventing a second launcher.
- `android/app/src/main/res/drawable/project_logo.png` and `project-logo.png` - Existing Beto identity assets; Companion UI should reuse identity subtly rather than creating a new brand surface.
- `.planning/phases/02-vertical-slice-minimo-plan-c-offline/02-CONTEXT.md` - Defines the Phase 2 flow that Phase 3 extends: tap -> STT -> matcher -> action -> TTS.

### Established Patterns
- Cross-component communication should continue through `AgentBus` and typed events/commands, not direct service coupling.
- Demo-critical actions should prefer deterministic paths and Intents over impressive but fragile agentic behavior.
- Logs are part of acceptance. Use tags such as `Beto-LLM`, `Beto-Action`, `Beto-Intent`, `Beto-STT`, and `Beto-TTS`.
- Android Views are preferred for the floating overlay; Compose is appropriate for the Companion sheet/activity.
- Keep one-user, one-device, demo-oriented assumptions. Avoid user accounts, persistent profiles, settings screens, or broad onboarding in this phase.

### Integration Points
- Long-press from overlay bubble opens Companion sheet/activity and starts voice capture immediately.
- Voice transcript can route either to Companion chat or to ActionDispatcher, depending on intent classification.
- `PromptBuilder` and LLM client must enforce separate prompts/models for Motor de Acciones vs Companero.
- `Sanitizer` must run before any cloud payload and log redacted payload evidence under `Beto-LLM`.
- `ActionDispatcher` must validate tool names against the Phase 3 allow-list and reject `agentic_perform_action`.
- Contact resolution must check `DemoContacts.kt` first, then Android Contacts, then ask/fail warmly on ambiguity.

</code_context>

<specifics>
## Specific Ideas

- "llama a mi hijo" should reliably call the known `hijo` contact.
- "llama a Pedro" should attempt to resolve `Pedro`, not silently substitute `hijo`.
- The Companion recording/listening animation should use vertical bars inspired by "Hey Google".
- Companion should feel like one Beto with the action system, not a separate mode the user has to understand.
- Companion tone: Argentine, simple but correct vocabulary, patient and calm, conversational and not boring.

</specifics>

<deferred>
## Deferred Ideas

- Physical side-button activation/configuration for Beto. This may be valuable for elders because phone screens are small and complex, but it is outside Phase 3's long-press-on-bubble scope.
- Personal memory for Beto: remember the user's name, close family members, frequent contacts, hobbies, and preferences. This should be a future phase with explicit consent, local/privacy-safe storage, and clear deletion/reset behavior.
- Persistent chat history across app restarts. Phase 3 keeps Companion history session-only per `COMP-04`.
- Enabling `agentic_perform_action` as an actual runnable fallback. Descriptor may exist now, but operational fallback belongs to Phase 4.

</deferred>

---

*Phase: 3-Motor de Acciones Completo + Companero*
*Context gathered: 2026-05-09*
