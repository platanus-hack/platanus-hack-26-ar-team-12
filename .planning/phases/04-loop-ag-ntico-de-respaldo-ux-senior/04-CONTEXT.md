# Phase 4: Loop Agentico de Respaldo + UX Senior - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase entrega el fallback agentico y la capa UX senior que dan credibilidad al demo sin cambiar el camino principal:

- El camino principal sigue siendo Intent/deterministic-first. El loop agentico solo se invoca como respaldo cuando el fallo es claro.
- El fallback se limita a WhatsApp y demuestra una busqueda/seleccion de contacto controlada para "mi nieto".
- El loop lee el arbol de Accessibility, elige una accion por iteracion, ejecuta acciones reales y corta bajo limites estrictos.
- La burbuja muestra 5 estados visuales claros: idle, listening, thinking, speaking, error.
- Toda UI propia de Beto usa tipografia senior, alto contraste, texto calido y TTS breve.

**Out of scope para esta fase:** usar el loop agentico como camino principal, auto-enviar mensajes de WhatsApp, fallback para SMS/Maps/llamadas, aprendizaje persistente de alias/contactos, vision con MediaProjection, onboarding de permisos, memoria personal persistente.

</domain>

<decisions>
## Implementation Decisions

### Fallback Trigger
- **D-01:** El planner debe elegir el trigger mas seguro y angosto para iniciar fallback, con sesgo a invocarlo solo ante fallo claro. No debe correr ante resultados apenas inciertos si eso arriesga la demo.
- **D-02:** Antes de iniciar fallback, Beto dice una frase calida y breve: **"Dame un segundo, lo intento de otra forma."**
- **D-03:** En Phase 4 el fallback aplica solo a WhatsApp. No se habilita para SMS, Maps ni llamadas.
- **D-04:** Exito de fallback significa que el mensaje previsto queda visible/llenado en el campo de compose de WhatsApp. Beto no auto-envia el mensaje.

### Agentic Safety Limits
- **D-05:** Acciones permitidas: click, type, scroll y back, siempre limitadas al fallback controlado de WhatsApp.
- **D-06:** El loop mantiene los hard limits de requirements: maximo 5 iteraciones, 15 segundos wallclock y 4000 tokens por turno.
- **D-07:** El detector de stuck debe abortar si el hash del arbol no cambia o si se repite la misma accion/ref dos veces.
- **D-08:** El LLM puede devolver un plan interno corto para debugabilidad, pero solo puede haber una accion ejecutable por iteracion.
- **D-09:** El escenario controlado de Phase 4 es WhatsApp contact/search fallback: si Beto no encuentra "mi nieto", pregunta como se llama el nieto o como esta agendado.
- **D-10:** Beto tiene maximo 3 intentos de aclaracion/busqueda para encontrar el contacto.
- **D-11:** Si despues de 3 intentos no encuentra el contacto, deja WhatsApp como este, dice una frase calida de fallo y la burbuja vuelve a idle.
- **D-12:** No se guarda el alias/contacto para sesiones futuras en Phase 4.

### Bubble States
- **D-13:** La burbuja mantiene el logo estable y comunica estado con ring de color + icono simple. No usar labels/badges completos dentro de la burbuja de 64dp.
- **D-14:** Paleta semantic calm: idle gris, listening azul, thinking ambar, speaking verde, error rojo.
- **D-15:** Animacion: un pulso suave compartido para estados no-idle y shake para error.
- **D-16:** El fallback agentico reutiliza el estado thinking. No se agrega sexto estado ni icono especial de fallback.
- **D-17:** Todas las transiciones visuales de estado deben completar en 200ms maximo.

### Senior UX System
- **D-18:** Definir style tokens compartidos para UI propia de Beto: `textStyleHero >= 28sp` y `textStyleBody >= 22sp`.
- **D-19:** Toda UI propia de Companion/atajos debe usar esos tokens, no tamanos sueltos por pantalla.
- **D-20:** Definir una phrase bank exacta para casos comunes de estado/fallo. Implementadores no deben improvisar errores tecnicos visibles al usuario.
- **D-21:** La phrase bank debe ser calida y breve. Ejemplos locked: "Dame un segundo, lo intento de otra forma." y "No pude encontrarlo. Probemos de nuevo despues, dale."
- **D-22:** Contraste: definir color tokens de alto contraste y verificarlos manualmente en el telefono de demo.
- **D-23:** Regla TTS: fallback/failure debe mantenerse en una sola frase; aclaracion de contacto puede ser una pregunta concisa.

### the agent's Discretion
- Trigger exacto de fallback queda a criterio del planner/implementador, siempre que sea angosto, demostrable y no convierta el loop en camino principal.
- Detalles exactos de TreeSerializer, hash de arbol, compactacion de nodos y shape del prompt quedan a criterio tecnico mientras respeten los hard limits y una accion por turno.
- Colores exactos de la paleta pueden ajustarse para contraste real en el telefono, siempre preservando el mapping semantico.
- Iconos simples de estado quedan a criterio de implementacion, priorizando legibilidad en 64dp.
- Frases adicionales de la phrase bank quedan a criterio, pero deben seguir el estilo calido, breve, argentino, sin codigo tecnico.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project-level
- `.planning/PROJECT.md` - Vision, core value, Android/Accessibility constraints, demo priorities, out-of-scope boundaries.
- `.planning/REQUIREMENTS.md` - Phase 4 requirement IDs: `AGENTIC-01`, `AGENTIC-02`, `AGENTIC-03`, `AGENTIC-04`, `AGENTIC-05`, `OVERLAY-05`, `UX-01`, `UX-02`, `UX-03`.
- `.planning/ROADMAP.md` - Phase 4 goal and success criteria.
- `.planning/STATE.md` - Current planning state and carry-forward decisions.

### Prior Phase Context
- `.planning/phases/01-foundation-sync-de-hora-0/01-CONTEXT.md` - Locked Android layout, `AgentBus`, overlay identity, TTS manager, logging tags, AccessibilityService skeleton.
- `.planning/phases/02-vertical-slice-minimo-plan-c-offline/02-CONTEXT.md` - Locked Plan C WhatsApp behavior, prefill-only rule, warm TTS phrases, and offline-first constraints.
- `.planning/phases/03-motor-de-acciones-completo-companero/03-CONTEXT.md` - Locked tool allow-list behavior, `agentic_perform_action` gating, Companion UX, sanitizer/LLM routing, and no-mode-split user experience.

### Research / Conventions
- `.planning/research/SUMMARY.md` - Consolidated hackathon architecture and Plan C rationale.
- `.planning/research/STACK.md` - Android/Kotlin stack and Firebase/Gemini constraints.
- `.planning/research/ARCHITECTURE.md` - Component boundaries for service, overlay, voice, LLM/action dispatcher, and future agentic loop.
- `.planning/research/PITFALLS.md` - Demo risks around AccessibilityService, LLM latency, Android service behavior, STT/TTS races, and fallback constraints.
- `.planning/research/FEATURES.md` - Senior UX, feedback states, accessibility, and agentic-loop rationale.
- `CLAUDE.md` - Global implementation rules, Beto voice/tone, modularity, logging, and Android guardrails.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `android/app/src/main/java/com/beto/app/service/BetoAccessibilityService.kt` - Current skeleton logs events and emits service lifecycle events; Phase 4 expands this into tree reading, node registry, and action execution.
- `android/app/src/main/java/com/beto/app/overlay/OverlayBubble.kt` - Existing classic View/WindowManager bubble with tap, long-press, drag, magnet, and close-target behavior. Phase 4 should add state rendering here rather than replacing the overlay.
- `android/app/src/main/java/com/beto/app/overlay/OverlayManager.kt` - Existing overlay creation and type selection (`TYPE_ACCESSIBILITY_OVERLAY` when AS is connected, fallback to application overlay).
- `android/app/src/main/java/com/beto/app/bus/AgentEvents.kt` - Existing TODOs for `TreeSnapshot`, `AgenticIterationComplete`, `AgenticAborted`; Phase 4 should add typed events/commands here.
- `android/app/src/main/java/com/beto/app/llm/ToolDescriptors.kt` - `agentic_perform_action` already exists as reserved tool name; Phase 4 enables its operational path in a constrained allow-list context.
- `android/app/src/main/res/layout/overlay_bubble.xml` and `android/app/src/main/res/drawable/project_logo.png` - Existing bubble surface and identity asset.

### Established Patterns
- Cross-component communication goes through `AgentBus`; do not wire services/activities directly.
- Demo reliability beats broad generality. Intent/deterministic paths remain primary; agentic work is a controlled fallback.
- Floating overlay uses classic Android Views, not Compose. Companion/screen UI may use Compose if Phase 3 introduced it.
- Logs are acceptance evidence. Continue using tags such as `Beto-Accessibility`, `Beto-LLM`, `Beto-Action`, `Beto-Intent`, `Beto-TTS`, and `Beto-Bus`.
- Existing TTS style is short, warm, and non-technical. Phase 4 formalizes that into a phrase bank.

### Integration Points
- Failed WhatsApp Intent/tool path from `ActionDispatcher` or equivalent invokes the constrained `AgentLoop`.
- `BetoAccessibilityService` provides `rootInActiveWindow`, filtered tree serialization, `NodeRefRegistry`, `nodeInfo.refresh()`, and `performAction`.
- Bubble state should listen to typed events/commands such as listening/thinking/speaking/error and update within 200ms.
- Agentic loop events should record iteration count, chosen action/ref, abort reason, and final status for logcat/debug.
- Companion/shortcut UI should consume shared senior typography/color tokens rather than local one-off text sizes.

</code_context>

<specifics>
## Specific Ideas

- Fallback status phrase: **"Dame un segundo, lo intento de otra forma."**
- Fallback failure phrase style: **"No pude encontrarlo. Probemos de nuevo despues, dale."**
- Contact clarification behavior: if "mi nieto" is not found, ask how the grandson is named or saved in contacts/WhatsApp.
- Max 3 clarification/search attempts before closing the fallback.
- WhatsApp can be left as-is on failure; Beto should not try to clean up the app with back navigation after the attempt limit.
- Bubble state mapping: idle gray, listening blue, thinking amber, speaking green, error red.

</specifics>

<deferred>
## Deferred Ideas

- Persistent alias/contact learning for relationships like "mi nieto". Future phase should define consent, storage, deletion/reset behavior, and privacy boundaries.

</deferred>

---

*Phase: 4-Loop Agentico de Respaldo + UX Senior*
*Context gathered: 2026-05-09*
