# Architecture Research

**Domain:** Android nativo — agente multimodal autónomo (AccessibilityService + Foreground Service + LLM con tool calling + STT/TTS + Overlay flotante + Intents)
**Researched:** 2026-05-09
**Confidence:** HIGH (componentes Android verificados con docs oficiales) / MEDIUM (parámetros del loop agéntico, basados en literatura de agentes ReAct + Droidrun + benchmarks GUI)

---

## Resumen ejecutivo (TL;DR para roadmap)

- **3 procesos lógicos vivos en simultáneo**: `BetoForegroundService` (dueño del loop y del cliente LLM), `BetoAccessibilityService` (dueño del overlay + lectura/acción del árbol), `VoiceCaptureActivity` transparente (dueña del `RecognizerIntent`). Se hablan vía **`LocalBroadcastManager` / `SharedFlow` en un singleton `AgentBus`**, no vía Binder remoto. Mismo proceso, mismo `Application`.
- **Vision (MediaProjection) NO va en MVP.** Solo árbol de vistas + texto. Razones: latencia, costo de tokens, permiso extra, complejidad. Captura queda como hook opcional para post-MVP.
- **Loop agéntico con tope duro de 5 iteraciones** (perceive → reason → act). Si a la 5ta no resolvió, TTS dice "no pude hacerlo" y aborta. No infinito ni 10+ — la demo no puede colgar 30 segundos.
- **Branch fija (Intents) y branch agéntica comparten un `ActionDispatcher`** que expone tool descriptors al LLM. El LLM decide; el dispatcher rutea. Sin código duplicado.
- **5 devs paralelizan limpio** porque las superficies son disjuntas: (1) Foreground+Overlay, (2) Accessibility+ActionExecutor, (3) STT/TTS+Activity transparente, (4) LLM client+prompts+sanitizer, (5) Intents directos+demo seeding. Se sincronizan en `AgentBus` y `ActionDispatcher` que se acuerdan en la primera hora.

---

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        PROCESO ÚNICO (com.beto.app)                       │
│                                                                            │
│  ┌──────────────────────────┐         ┌────────────────────────────────┐ │
│  │ BetoAccessibilityService │◄───────►│  BetoForegroundService         │ │
│  │  - onAccessibilityEvent  │  events │  - dueño del Agent Loop        │ │
│  │  - rootInActiveWindow    │  via    │  - cliente HTTP LLM            │ │
│  │  - performAction         │  Bus    │  - notificación persistente    │ │
│  │  - WindowManager overlay │         │    foregroundServiceType=      │ │
│  │     (burbuja flotante)   │         │     mediaProjection|microphone │ │
│  └────────────┬─────────────┘         └──────────┬─────────────────────┘ │
│               │                                  │                        │
│               ▼                                  ▼                        │
│       ┌───────────────────────────────────────────────────┐              │
│       │              AgentBus (singleton)                  │              │
│       │  SharedFlow<AgentEvent> + SharedFlow<AgentCommand>│              │
│       │  - VoiceCaptured(text)                            │              │
│       │  - LlmDecision(toolCall)                          │              │
│       │  - ActionResult(success, payload)                 │              │
│       │  - SpeakRequest(text)                             │              │
│       └─────┬───────────────────┬─────────────────────────┘              │
│             ▲                   ▲                                         │
│             │                   │                                         │
│  ┌──────────┴──────────┐  ┌─────┴──────────────────────┐                 │
│  │ VoiceCaptureActivity│  │ ActionDispatcher (in-proc) │                 │
│  │  (transparente)     │  │  ├─ IntentBranch           │                 │
│  │  - RecognizerIntent │  │  │   (whatsapp, call, sms, │                 │
│  │  - cierra al volver │  │  │    maps — top-N fijos)  │                 │
│  │    resultado        │  │  └─ AgenticBranch          │                 │
│  └─────────────────────┘  │      (tree→LLM→Action)     │                 │
│                            └────────────┬───────────────┘                 │
│                                         │                                 │
│                                         ▼                                 │
│  ┌────────────────┐  ┌──────────────────────────────────────────┐        │
│  │ TtsManager     │  │ LlmClient                                │        │
│  │  - es-AR voice │  │  - HTTP (Retrofit/OkHttp)                │        │
│  │  - SpeakRequest│  │  - tool/function calling                 │        │
│  │     consumer   │  │  - Sanitizer.scrub(text) antes de POST   │        │
│  └────────────────┘  └──────────────────────────────────────────┘        │
└──────────────────────────────────────────────────────────────────────────┘
        │                                                       │
        ▼                                                       ▼
┌────────────────┐                                    ┌─────────────────┐
│ Android System │                                    │  LLM Cloud      │
│ - SpeechRec    │                                    │  (Claude/GPT/   │
│ - TTS engine   │                                    │   Gemini)       │
│ - Other apps   │                                    └─────────────────┘
│   (WhatsApp..) │
└────────────────┘
```

### Component Responsibilities

| Componente | Responsabilidad | Implementación |
|---|---|---|
| **BetoForegroundService** | Mantener vivo el proceso, ser dueño del agent loop, cliente LLM, gestionar timeouts/aborts | `Service` con `startForeground()` + `Notification` persistente. `foregroundServiceType="microphone"` (Android 14+ exige el tipo declarado por permiso). |
| **BetoAccessibilityService** | Leer `rootInActiveWindow`, ejecutar `performAction`, dueño del overlay (burbuja). Emite eventos al bus | `AccessibilityService`. Overlay vía `WindowManager` con `TYPE_ACCESSIBILITY_OVERLAY` (preferido — trusted) o `TYPE_APPLICATION_OVERLAY` (fallback si la burbuja debe vivir antes que el AS esté conectado). |
| **VoiceCaptureActivity** | Lanzar `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, devolver texto al bus, cerrarse | `Activity` con tema `Theme.Translucent.NoTitleBar`. Disparada por tap en burbuja. Alternativa: `SpeechRecognizer.createSpeechRecognizer()` directo en el FG service (más rápido pero requiere `RECORD_AUDIO` y manejo manual de RMS/silencio). MVP recomendado: la Activity transparente, es trivial. |
| **AgentBus** | Pub/sub in-process entre todos los componentes | `object AgentBus { val events = MutableSharedFlow<AgentEvent>(replay=0, extraBufferCapacity=8) }`. Coroutines + `flowOn(Dispatchers.Main)` para handlers de UI. |
| **ActionDispatcher** | Recibir `ToolCall` del LLM y rutear a `IntentBranch` o `AgenticBranch`. Devolver `ActionResult` al bus | Función pura `dispatch(toolCall): ActionResult`. La decisión "fija vs agéntica" la toma el LLM al elegir qué tool llamar — si llama `send_whatsapp_message` va a Intent; si llama `tap_element` va a agéntica. |
| **IntentBranch** | Top-N comandos guionados con `Intent` directos | Constantes con `setPackage("com.whatsapp")`, `Intent.ACTION_VIEW` con `smsto:`, `tel:`, `geo:`, etc. Sin AccessibilityService involucrado. |
| **AgenticBranch** | Loop perceive → reason → act sobre `AccessibilityNodeInfo` | Itera ≤5 veces. Serializa el árbol a JSON compacto, manda al LLM, recibe `tap(@nodeId)` o `type(@nodeId, "text")`, ejecuta vía AS, vuelve a leer el árbol, evalúa éxito. |
| **LlmClient** | HTTP al provider, tool calling, retry simple | Retrofit + OkHttp. Timeout 15s. Sin streaming en MVP (innecesario para acciones; sí para Compañero opcionalmente). |
| **Sanitizer** | Regex que tacha DNI/teléfono/tarjeta antes de salir del device | Función pura. Aplicada en `LlmClient` justo antes del POST. |
| **TtsManager** | `TextToSpeech` es-AR. Suscriptor de `SpeakRequest` en el bus | Singleton inicializado en `Application.onCreate()`. |
| **OverlayBubble** | Vista flotante draggable, tap = STT, long-press = Compañero sheet | `WindowManager.LayoutParams` con `FLAG_NOT_FOCUSABLE \| FLAG_LAYOUT_NO_LIMITS`, `OnTouchListener` con detección manual de drag vs tap. |
| **CompanionSheet** | Chat conversacional cuando se hace long-press | `BottomSheetDialog` lanzado desde una `Activity` (no se puede mostrar `BottomSheet` directo desde overlay). En MVP, alcanza con una Activity dedicada estilo "card". |

---

## Recommended Project Structure

```
app/src/main/java/com/beto/app/
├── BetoApplication.kt                 # init: TTS, AgentBus, LlmClient
├── service/
│   ├── BetoForegroundService.kt       # agent loop owner, notification
│   ├── BetoAccessibilityService.kt    # tree reader, action executor, overlay host
│   └── ServiceCoordinator.kt          # helpers: startFg(), ensureA11y()
├── overlay/
│   ├── OverlayBubble.kt               # WindowManager view + drag/tap detection
│   └── OverlayManager.kt              # add/remove from WindowManager
├── voice/
│   ├── VoiceCaptureActivity.kt        # RecognizerIntent host (transparente)
│   └── TtsManager.kt                  # speak(text)
├── agent/
│   ├── AgentBus.kt                    # SharedFlow event/command bus
│   ├── AgentEvents.kt                 # sealed class AgentEvent / AgentCommand
│   ├── AgentLoop.kt                   # perceive → reason → act, max 5 iter
│   ├── ActionDispatcher.kt            # routes ToolCall → branches
│   └── branches/
│       ├── IntentBranch.kt            # whatsapp, call, sms, maps
│       └── AgenticBranch.kt           # tree-based fallback
├── llm/
│   ├── LlmClient.kt                   # HTTP + tool calling
│   ├── ToolDescriptors.kt             # JSON schema de tools expuestas
│   ├── PromptBuilder.kt               # system prompt + tree serialization
│   └── Sanitizer.kt                   # regex DNI/phone/card
├── companion/
│   └── CompanionActivity.kt           # chat sheet — long-press en burbuja
├── tree/
│   ├── TreeSerializer.kt              # AccessibilityNodeInfo → JSON compact
│   └── NodeRefRegistry.kt             # @e1, @e2... refs para que el LLM apunte
└── util/
    ├── Logging.kt                     # tag "Beto-*" estándar
    └── Permissions.kt                 # checks pero sin onboarding
```

### Structure Rationale

- **`service/`, `overlay/`, `voice/`, `agent/`, `llm/` son disjuntos** — diferentes devs trabajan en cada uno sin tocarse.
- **`agent/AgentBus.kt` y `agent/AgentEvents.kt` son el "contrato" central** — se acuerdan en la hora 0 y no se tocan más.
- **`tree/NodeRefRegistry.kt` es clave** — el LLM no manda XPaths ni IDs reales; manda refs cortos (`@e5`) que el registry traduce a `AccessibilityNodeInfo`. Patrón validado por Droidrun y Agent Device de Callstack — reduce tokens hasta 80% al filtrar visibles solamente.
- **`branches/` separa lógica fija de loop** — un dev puede agregar un Intent sin tocar la rama agéntica.

---

## Architectural Patterns

### Pattern 1: Event Bus In-Process (sin Binder)

**Qué:** Singleton `AgentBus` con `SharedFlow<AgentEvent>` y `SharedFlow<AgentCommand>`. Todos los componentes (Service, AccessibilityService, Activity) viven en el mismo proceso y comparten heap, así que no hay AIDL ni Binder.

**Cuándo usarlo:** Apps Android donde varios `Service`/`Activity` del mismo proceso necesitan coordinarse. Es el patrón que más fricción ahorra en hackathon.

**Trade-offs:** + cero boilerplate, debug fácil, type-safe con sealed classes. − si el proceso muere, se pierde todo (aceptable: el FG service evita la muerte).

**Ejemplo:**
```kotlin
sealed class AgentEvent {
  data class VoiceCaptured(val text: String) : AgentEvent()
  data class TreeSnapshot(val nodes: List<NodeRef>) : AgentEvent()
  data class ActionResult(val ok: Boolean, val msg: String?) : AgentEvent()
  data class SpeakRequest(val text: String) : AgentEvent()
}

object AgentBus {
  private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 16)
  val events: SharedFlow<AgentEvent> = _events
  suspend fun emit(e: AgentEvent) = _events.emit(e)
}
```

### Pattern 2: Tool Calling como single source of routing

**Qué:** El LLM ve un set fijo de tools (`send_whatsapp_message`, `make_call`, `agentic_action`, `speak_to_user`). Elige una. El `ActionDispatcher` ejecuta. Branch fija vs agéntica es solo "qué tool eligió el LLM".

**Cuándo usarlo:** Cuando hay un camino "rápido y confiable" (Intents) y un camino "lento y flexible" (loop) y querés que el LLM decida cuál sin código duplicado.

**Trade-offs:** + zero duplicación, prompt más simple. − dependés de que el LLM elija bien (mitigable: descripciones de tool muy explícitas y few-shot en system prompt).

**Ejemplo de tool descriptors:**
```kotlin
val tools = listOf(
  Tool("send_whatsapp_message",
    "Envía un mensaje de WhatsApp. Usar SIEMPRE para WhatsApp en vez de tap_element.",
    params = mapOf("contact" to "string", "message" to "string")),
  Tool("make_call",
    "Llama a un contacto. Usar SIEMPRE en vez de abrir la app de teléfono manualmente.",
    params = mapOf("contact" to "string")),
  Tool("agentic_action",
    "Ejecuta una acción en cualquier app que NO esté cubierta por las tools fijas. " +
    "Recibe un goal en lenguaje natural; entrarás en un loop perceive→act limitado a 5 pasos.",
    params = mapOf("goal" to "string")),
  Tool("speak_to_user",
    "Habla al usuario en tono cálido. Usar para confirmar, pedir aclaración o reportar error.",
    params = mapOf("text" to "string")),
)
```

### Pattern 3: Loop agéntico con presupuesto de iteraciones

**Qué:** ReAct loop con `MAX_ITERATIONS = 5`. Cada iteración: serializa árbol de vistas (filtrado a visible+clickable+focusable), manda al LLM con el goal, recibe `tap(@e3)` o `type(@e7, "...")` o `done(success)` o `done(fail, reason)`. Ejecuta. Re-lee el árbol. Repite.

**Cuándo usarlo:** Cuando el LLM elige `agentic_action`, no hay otra forma. NO usarlo para los top-N (intents son determinísticos).

**Trade-offs:** + cubre apps no previstas. − frágil, lento (3-5s por iter × 5 = hasta 25s), caro en tokens (árbol completo × 5 = ~10-30k tokens en una sesión).

**Por qué 5 y no 10:**
- Producción típica: 10-15 (Augment, Hermes, Oracle blogs).
- Hackathon: latencia importa más que cobertura. 5 iter ≈ 15-25s peor caso. 10 iter ≈ 30-50s — la demo se cuelga.
- Si en 5 pasos no resolvió, lo más probable es que el árbol sea malo (apps bancarias, vistas custom) y más iteraciones no ayudan.

**Criterio de abort:**
1. `iterations >= 5` → TTS "no pude hacerlo, ¿lo intentamos de otra forma?", abort.
2. LLM responde `done(fail, reason)` → TTS con la razón, abort.
3. Árbol no cambia entre iter N y N+1 (`tree.hash == prevTree.hash`) → abort por loop estancado.
4. Timeout total de 30s → abort hard.

**Ejemplo:**
```kotlin
suspend fun runAgenticLoop(goal: String): ActionResult {
  var lastTreeHash: Int? = null
  for (i in 1..MAX_ITERATIONS) {
    val tree = a11y.snapshotVisibleClickable() // filtrado, no todo el árbol
    if (tree.hash == lastTreeHash) return ActionResult.fail("stuck")
    lastTreeHash = tree.hash

    val decision = llm.decide(goal, tree, history) // tool call
    when (decision) {
      is Tap -> a11y.performAction(decision.ref, CLICK)
      is Type -> a11y.performAction(decision.ref, SET_TEXT, decision.text)
      is Done -> return if (decision.success) ActionResult.ok() else ActionResult.fail(decision.reason)
    }
    delay(400) // dejar que la UI se asiente
  }
  return ActionResult.fail("max_iterations")
}
```

---

## Data Flow

### Modo 1: Motor de Acciones (hero)

```
[Usuario tap burbuja]
    │
    ▼
[OverlayBubble] ──launches──► [VoiceCaptureActivity (transparente)]
                                       │
                                       │ RecognizerIntent
                                       ▼
                              [Android Speech Service]
                                       │
                                       │ onActivityResult
                                       ▼
                              [VoiceCaptureActivity finishes]
                                       │ AgentBus.emit(VoiceCaptured(text))
                                       ▼
                              [BetoForegroundService consumer]
                                       │
                                       │ Sanitizer.scrub(text)
                                       ▼
                              [LlmClient.chat(text, tools)] ──HTTP──► [LLM Cloud]
                                                                            │
                                       ┌────────────────────────────────────┘
                                       │ tool_call: send_whatsapp_message
                                       ▼
                              [ActionDispatcher.route(toolCall)]
                                       │
                                       ├─► [IntentBranch.sendWhatsapp(contact, msg)]
                                       │         │
                                       │         ▼
                                       │     [Android Intent → WhatsApp app]
                                       │
                                       └─► (si tool == agentic_action)
                                              [AgenticBranch.runLoop(goal)] ◄───┐
                                                   │                            │
                                                   │ a11y.snapshot()            │
                                                   ▼                            │
                                           [AccessibilityService]               │
                                                   │ tree (filtered)            │
                                                   ▼                            │
                                           [LlmClient.decide(goal, tree)] ──────┘
                                                   │
                                                   ▼ (≤5 iter)
                                           [ActionResult]
                                       │
                                       ▼
                              [AgentBus.emit(SpeakRequest("Listo, le avisé a tu nieto"))]
                                       │
                                       ▼
                              [TtsManager.speak()] ──► [Usuario escucha]
```

### Modo 2: Compañero (long-press)

```
[Usuario long-press burbuja]
    │
    ▼
[OverlayBubble] ──startActivity──► [CompanionActivity]
                                          │
                                          ▼
                                  [chat UI con TextField]
                                          │
                                          │ usuario escribe / habla
                                          ▼
                                  [LlmClient.chat(history, systemPrompt="cálido, simple, corto")]
                                          │
                                          ▼
                                  [LLM responde texto plano — sin tools]
                                          │
                                          ▼
                                  [render en chat] + [TtsManager.speak()]
```

Sin AccessibilityService involucrado. Sin tool calling. Es chat puro con system prompt fuerte.

### State Management

No hay store global tipo Redux — es overkill para hackathon. Cada componente mantiene su estado local:

- **`AgentLoop`**: estado de la sesión actual (turn count, history, lastTreeHash) en variable local de la coroutine.
- **`OverlayBubble`**: posición x/y en `SharedPreferences` (persiste entre boots).
- **`CompanionActivity`**: history de chat en `ViewModel` con `SavedStateHandle`. Se pierde al cerrar — está bien, es stateless por sesión por decisión de scope.

---

## Vision (MediaProjection) — decisión documentada

**Decisión: NO incluir captura de pantalla en MVP. Solo árbol de vistas + texto.**

| Criterio | Solo árbol (texto) | Captura (vision) | Híbrido |
|---|---|---|---|
| Latencia por iter | 200-400ms (snapshot + serialize) | 800-1500ms (capture + encode + upload) | 1000-2000ms |
| Tokens por iter | 1-3k (árbol filtrado a visibles) | 3-8k (imagen encoded + base64) | 4-10k |
| Costo por sesión 5-iter | ~$0.01-0.03 | ~$0.05-0.15 | ~$0.06-0.18 |
| Permisos extra | 0 (ya tenemos AccessibilityService) | dialog `MediaProjection` cada vez (en Android 14 exige confirmación periódica) | ambos |
| Cobertura | Falla en apps con vistas custom (Canvas, Compose sin semantics, juegos) | Funciona en todo lo visible | Mejor cobertura |
| Trabajo de implementación | bajo (ya lo tenés) | alto (`MediaProjectionManager`, `VirtualDisplay`, `ImageReader`, encode JPEG, base64) | muy alto |
| Calidad LLM en es-AR | OK (texto) | OK (Claude/GPT/Gemini multimodales sólidos) | la mejor |

**Razonamiento:**
- WhatsApp, contactos, llamadas, SMS, Maps cumplen con accesibilidad razonable. El árbol alcanza para los Intents y para el 80% de targets agénticos.
- El permiso `MediaProjection` en Android 14+ requiere confirmación de usuario por sesión — frágil para demo.
- Latencia por iteración doblaría el peor caso del loop (de 25s a 50s). Inaceptable.
- Costo por sesión de demo no es el problema (tenemos créditos), pero tokens grandes = más latencia y más fallas de timeout.
- La narrativa "leo lo que vos ves en la pantalla" se cuenta igual con árbol — el usuario no diferencia.

**Hook de extensibilidad:** dejar la `AgenticBranch` con un parámetro opcional `includeScreenshot: Boolean = false`. Post-MVP se prende en el config y se manda imagen junto al árbol. Sin re-arquitectura.

---

## Build Order — paralelización para 5 devs

### Hora 0 — sync de 30 min, todos juntos

Definir y commitear:
1. `agent/AgentEvents.kt` — sealed class de eventos y comandos del bus.
2. `llm/ToolDescriptors.kt` — lista exacta de tools que el LLM verá.
3. `agent/AgentBus.kt` — singleton vacío pero firmado.
4. `BetoApplication.kt` — `onCreate()` con TODOs.

**Una vez mergeado esto, el resto se paraleliza limpio.**

### Hora 1-12 — paralelo total (tracks A-E)

| Track | Dev | Archivos | Bloquea a | Bloqueado por |
|---|---|---|---|---|
| **A. Foreground + Overlay** | 1 | `BetoForegroundService.kt`, `OverlayBubble.kt`, `OverlayManager.kt` | nadie | hora 0 |
| **B. Accessibility + Tree** | 2 | `BetoAccessibilityService.kt`, `tree/TreeSerializer.kt`, `tree/NodeRefRegistry.kt`, `agent/branches/AgenticBranch.kt` (esqueleto, sin LLM) | nadie | hora 0 |
| **C. Voice (STT/TTS)** | 3 | `VoiceCaptureActivity.kt`, `voice/TtsManager.kt` | nadie | hora 0 |
| **D. LLM + Sanitizer + Loop** | 4 | `LlmClient.kt`, `PromptBuilder.kt`, `Sanitizer.kt`, `AgentLoop.kt`, `ActionDispatcher.kt` | nadie | hora 0 |
| **E. Intents + Compañero + Demo** | 5 | `IntentBranch.kt`, `CompanionActivity.kt`, seeding de teléfono (contactos demo, WhatsApp), guion | nadie | hora 0 |

**Por qué no se pisan:** cada track toca archivos distintos. Cuando A necesita disparar STT, emite `RequestVoiceCapture` al bus; C lo consume. Ninguno conoce al otro, solo el contrato del bus.

### Hora 12-18 — primera integración (sync point)

Todos juntos por 1-2h. Smoke test: tap burbuja → STT → LLM mock con tool fijo → Intent abre WhatsApp → TTS confirma. Si esto anda, branch fija está demoable. Si no, todo lo demás es accesorio.

### Hora 18-30 — paralelo selectivo

| Track | Foco |
|---|---|
| A | Pulir UX de burbuja (drag, animación, ícono) |
| B | Mejorar `TreeSerializer` — filtrar ruido, agregar refs `@e1` etc. |
| C | Tunear TTS es-AR (rate, pitch), prompts cálidos pre-grabados como fallback |
| D | Tunear system prompt, agregar few-shot, mejorar el loop con criterios de abort |
| E | Ensayar guion, agregar 2 Intents extra (SMS, Maps), filmar backup |

### Hora 30-36 — solo bug fixing y demo prep

- Cero features nuevas.
- Filmar la demo entera 2 veces como backup ante fallos en vivo.
- Verificar permisos en el teléfono dedicado.
- Charge.

### Análisis de paralelizabilidad

- **Independencia entre tracks: alta.** Cada uno owna un namespace de archivos.
- **Punto único de fricción: `AgentEvents.kt`.** Si alguien quiere agregar un evento, hace PR chico que no toca lógica. Mergea rápido.
- **Riesgo de bottleneck: track D (LLM) bloquea integración real.** Mitigación: track D entrega un `LlmClient` mock con respuestas hardcodeadas en hora 4 para que A/B/C puedan integrar antes de que el cliente real esté listo.
- **Paralelismo razonable: 4 de 5 tracks pueden estar en simultáneo en cualquier momento sin conflict.** El 5to suele ser quien revisa PRs / desbloquea / hace el seeding del teléfono.

---

## Scaling Considerations

| Escala | Ajustes |
|---|---|
| 1 device (demo) | Lo que tenemos. No optimizar nada más. |
| 10 devices (post-hackathon QA) | Agregar feature flags por device, telemetría mínima (Firebase Analytics), crash reporting (Crashlytics). |
| 1k usuarios (beta) | Tier gratis + paid del LLM, rate limit por user, cache de árboles serializados, considerar STT cloud por calidad. |
| 100k+ | Migración a NER on-device (TF Lite), modelo propio para tool routing barato (clasificador local antes de pegarle al LLM grande), cache distribuido de embeddings de comandos comunes. |

### Primer cuello de botella esperado

**Tokens del LLM en sesiones agénticas largas.** Un árbol de 50 nodos × 5 iter × historial acumulado = 15-30k tokens por sesión. Soluciones en orden:
1. Filtrar agresivo (visible + clickable solamente). Reduce 80%.
2. Resumir el árbol en cada iter (mandar solo el diff).
3. Clasificador local que decida si ir a Intent vs agentic sin pegarle al LLM.

---

## Anti-Patterns

### Anti-Pattern 1: AccessibilityService corriendo el LLM client

**Qué hace la gente:** poner el cliente HTTP, el loop del agente y todo el cerebro adentro del `BetoAccessibilityService`.

**Por qué está mal:** el AccessibilityService corre en el proceso main, y bloquearlo con coroutines pesadas o callbacks lentos hace que Android lo mate por ANR (Application Not Responding). Además, cuando el usuario desactiva AS, perdés el cerebro.

**En su lugar:** el AS solo lee árboles y ejecuta acciones. El cerebro (loop + LLM) vive en `BetoForegroundService`. Se hablan por bus.

### Anti-Pattern 2: Foreground Service sin `foregroundServiceType` correcto

**Qué hace la gente:** declarar el FG service sin `foregroundServiceType` o con uno genérico.

**Por qué está mal:** Android 14 (API 34) exige tipos específicos por permiso. Si usás mic en background, necesitás `foregroundServiceType="microphone"` y `FOREGROUND_SERVICE_MICROPHONE` permission. Sin eso, `startForeground()` tira `SecurityException`. Confirmado por docs oficiales.

**En su lugar:**
```xml
<service
  android:name=".service.BetoForegroundService"
  android:foregroundServiceType="microphone"
  android:exported="false" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
```

Si en algún momento agregás `MediaProjection`: `foregroundServiceType="microphone|mediaProjection"`.

### Anti-Pattern 3: Mandar el árbol entero al LLM

**Qué hace la gente:** serializar `rootInActiveWindow` recursivo completo, mandar al LLM como JSON masivo.

**Por qué está mal:** un árbol típico de WhatsApp tiene 200-500 nodos. La mayoría son contenedores, padding, decoración. 80% de tokens son ruido. El LLM se confunde y eligi mal el nodo.

**En su lugar:** serializar solo nodos donde `isVisibleToUser && (isClickable || isLongClickable || isFocusable || hasText)`. Asignar refs cortos `@e1`, `@e2`. Dropear coordenadas absolutas. Validado por Droidrun y Agent Device.

### Anti-Pattern 4: SpeechRecognizer dentro del overlay

**Qué hace la gente:** intentar lanzar `SpeechRecognizer` directamente desde el `OverlayBubble`.

**Por qué está mal:** `SpeechRecognizer` requiere ciclo de vida de `Activity` para algunos engines, y `RecognizerIntent` requiere `startActivityForResult`. El overlay no es una Activity. Además, en Android 12+, capturar audio mientras hay otra app en foreground compite con el mic — la app activa puede tener prioridad y la tuya recibe silencio.

**En su lugar:** una `VoiceCaptureActivity` transparente que se lanza al tap, hace `RecognizerIntent`, devuelve resultado al bus, y se cierra. La AccessibilityService cuenta como "foreground UI" para el sharing del mic — está documentado.

### Anti-Pattern 5: Loop agéntico sin tope ni timeout

**Qué hace la gente:** "que itere hasta lograrlo".

**Por qué está mal:** en demo en vivo es la muerte. El LLM puede entrar en bucle (tap mismo botón infinito), o el árbol puede ser inestable, o un dialog puede aparecer y bloquear. La demo se cuelga 60s y perdiste el pitch.

**En su lugar:** `MAX_ITERATIONS=5`, timeout total 30s, detección de "árbol no cambió" como abort, mensaje TTS de fallo cálido ("perdón, no pude — ¿probamos otra cosa?").

### Anti-Pattern 6: Burbuja flotante con `TYPE_SYSTEM_OVERLAY`

**Qué hace la gente:** copiar tutoriales viejos con `TYPE_SYSTEM_ALERT` o `TYPE_SYSTEM_OVERLAY`.

**Por qué está mal:** ambos están deprecated desde API 26. En Android moderno, `TYPE_APPLICATION_OVERLAY` es el reemplazo. Y si la burbuja vive dentro del AS, `TYPE_ACCESSIBILITY_OVERLAY` es trusted (Android no muestra el banner "esta app está dibujando encima de otras").

**En su lugar:** `TYPE_ACCESSIBILITY_OVERLAY` cuando el AS esté conectado; `TYPE_APPLICATION_OVERLAY` como fallback antes de la conexión.

---

## Integration Points

### External Services

| Servicio | Integración | Notas |
|---|---|---|
| LLM Cloud | HTTPS POST con tool calling | Timeout 15s por call. Sin streaming en MVP. Sanitizer corre antes del POST. |
| Android Speech (STT) | `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` con `EXTRA_LANGUAGE="es-AR"` | Activity transparente. Un dialog del sistema aparece — no podemos esconderlo, pero es rápido. |
| Android TTS | `TextToSpeech` con `Locale("es", "AR")` | Inicializar en `Application.onCreate()`. Voz puede requerir descarga del data pack la primera vez. |
| WhatsApp | `Intent.ACTION_SEND` con `setPackage("com.whatsapp")` y `MIME=text/plain`, o deep link `https://wa.me/<phone>?text=...` | Deep link más confiable porque no requiere lookup de contacto. Usar wa.me. |
| Phone | `Intent.ACTION_CALL` con `tel:` URI | Requiere `CALL_PHONE` permission (peligroso). Pre-otorgado en demo. |
| SMS | `Intent.ACTION_VIEW` con `smsto:` URI | No envía solo, abre la app de SMS con el contenido pre-cargado. Aceptable. |
| Maps | `geo:lat,lng?q=...` o `https://maps.google.com/?q=...` | Deep link funciona sin Google Maps app instalado (cae a browser). |

### Internal Boundaries

| Boundary | Comunicación | Notas |
|---|---|---|
| ForegroundService ↔ AccessibilityService | `AgentBus` SharedFlow | Mismo proceso. Cero Binder. |
| OverlayBubble (en AS) ↔ FG Service | `AgentBus` | El tap emite `RequestVoiceCapture`. |
| VoiceCaptureActivity ↔ FG Service | `AgentBus` (al cerrarse, emite `VoiceCaptured(text)`) | La Activity es transient. |
| LlmClient ↔ AgenticBranch | llamada directa de función | Mismo namespace `agent/`. |
| AgenticBranch ↔ AccessibilityService | método público `snapshotVisibleClickable()` y `performAction(ref, action)` en singleton | El AS expone una interfaz minimalista. |
| Sanitizer ↔ LlmClient | función pura en `LlmClient.scrub()` | Aplicada en interceptor de OkHttp para garantizar que NUNCA salga texto sin scrub. |

---

## Sources

- [AccessibilityService overview & overlays — Android Developers](https://developer.android.com/guide/topics/ui/accessibility/service)
- [WindowManager.LayoutParams — TYPE_APPLICATION_OVERLAY / TYPE_ACCESSIBILITY_OVERLAY](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [Foreground service types — Android 14+](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Foreground service restrictions on starting from background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Sharing audio input — concurrent capture rules](https://developer.android.com/media/platform/sharing-audio-input)
- [SpeechRecognizer API reference](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [MediaProjection API reference](https://developer.android.com/reference/android/media/projection/MediaProjection)
- [How We Optimized Agent Device for Mobile App Automation — Callstack](https://www.callstack.com/blog/how-we-optimized-agent-device-for-mobile-app-automation) — accessibility tree → refs `@e5` pattern, 80% token reduction filtering visibles
- [Droidrun — hybrid accessibility tree + computer vision agent](https://www.blog.d-techstudios.com/2025/07/droidrun-revolutionizing-mobile.html)
- [Do LLMs Need to See Everything? Screentext vs Screenshots benchmark](https://arxiv.org/html/2604.17817v1)
- [ReAct: Reasoning and Acting in LLMs (Yao et al.)](https://www.ibm.com/think/topics/react-agent)
- [AI Agent Loop Token Costs — production MaxIterations 10-15](https://www.augmentcode.com/guides/ai-agent-loop-token-cost-context-constraints)
- [Anatomy of an Agent Loop — Steve Kinney](https://stevekinney.com/writing/agent-loops)

---

*Architecture research for: Android nativo agente multimodal (Beto)*
*Researched: 2026-05-09*
