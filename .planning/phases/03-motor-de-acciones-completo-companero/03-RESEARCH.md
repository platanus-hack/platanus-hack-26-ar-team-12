# Phase 3: Motor de Acciones Completo + Companero - Research

**Researched:** 2026-05-09
**Status:** Complete
**Question:** What do I need to know to plan Phase 3 well?

## User Constraints

### Phase Boundary

Esta fase expande el flujo de Beto desde el Plan C offline de WhatsApp hacia un motor de acciones completo y una experiencia conversacional simple:

- Mantiene el matcher determinista primero para comandos criticos de demo.
- Conecta Gemini para comandos arbitrarios en espanol argentino mediante tool calling estricto.
- Ejecuta llamadas, SMS y Maps ademas de WhatsApp.
- Sanitiza datos sensibles antes de cualquier llamada cloud y deja evidencia verificable en logs `Beto-LLM`.
- Resuelve contactos primero con `DemoContacts.kt` y luego, si hace falta, con Android Contacts.
- Long-press en la burbuja abre Modo Companero: una experiencia voice-first, conversacional, calida y simple.
- Desde Companero, Beto puede responder como chat o confirmar y ejecutar una accion sin que el usuario tenga que entender cambios de modo internos.

**Out of scope para esta fase:** loop agentico operativo, auto-operar pantallas por Accessibility como fallback, memoria personal persistente, configuracion de boton fisico lateral, historial de chat persistente, wake word, onboarding real de permisos, NER avanzado, vision con MediaProjection.

### Locked Decisions

- **D-01:** Para los comandos criticos de demo, el ruteo es **deterministic first**. Frases top como "llama a mi hijo", "mandale un sms a Ana" y "abrime el mapa hasta la farmacia" deben pasar por matcher local antes de llamar a Gemini.
- **D-02:** Gemini se usa para phrasing arbitrario, variantes no exactas y comandos fuera de las familias deterministas. El usuario no debe notar si internamente se uso matcher o LLM.
- **D-03:** El Motor de Acciones usa `gemini-2.5-flash` con tool calling estricto y `temperature: 0` para reducir variacion en nombres de tool, campos y estructura.
- **D-04:** Si Gemini devuelve JSON malformado, un tool name desconocido o argumentos invalidos, Beto hace **un retry** con una instruccion de reparacion. Si el retry falla, puede caer a fallback determinista/cache solo si el texto original conserva la misma semantica explicita de accion/contacto/destino/mensaje.
- **D-05:** Ningun fallback puede sustituir contacto, destino, app, mensaje ni accion. Si el usuario dijo "Pedro" y Pedro no se resuelve, Beto no puede llamar a "hijo"; debe pedir aclaracion corta o fallar calidamente.
- **D-06:** La resolucion de contactos es `DemoContacts.kt` primero, Android Contacts segundo. Demo aliases ganan para confiabilidad; nombres arbitrarios como "Pedro" pueden resolverse por contactos del telefono.
- **D-07:** Si Android Contacts devuelve cero matches o multiples matches, Beto no adivina. Debe hacer una pregunta corta de aclaracion o fallar con tono calido.
- **D-08:** `ToolDescriptors.kt` puede incluir `agentic_perform_action` como descriptor de contrato compartido, pero Phase 3 no lo habilita. La allow-list del LLM para esta fase es exactamente: `send_whatsapp`, `make_call`, `send_sms`, `open_maps`.
- **D-09:** Si Gemini devuelve `agentic_perform_action` en Phase 3, `ActionDispatcher` debe rechazarlo, loguearlo con `Beto-LLM` / `Beto-Action`, y responder calidamente. El tool se habilita recien en Phase 4.
- **D-10:** Long-press en la burbuja abre Modo Companero y empieza a escuchar inmediatamente. Debe sentirse como "hablar con Beto", no como entrar a una pantalla compleja.
- **D-11:** Companero es voice-first pero conserva una barra de texto minima para escribir. No debe tener un boton gigante; el usuario objetivo ya lidia con pantallas chicas y complicadas.
- **D-12:** La UI de escucha debe usar una animacion de barras verticales estilo "Hey Google" para mostrar grabacion/escucha de forma estetica y clara.
- **D-13:** El sheet de Companero debe ser simple y liviano: chat/transcript, barra de texto minima y estado de escucha. Evitar settings, paneles pesados o controles que parezcan otra app.
- **D-14:** Companero puede responder de forma mas conversacional que el Motor de Acciones. Puede hacer follow-ups y dar respuestas mas completas cuando sirva, pero con tono argentino, vocabulario simple pero correcto, paciente, calmo y no aburrido.
- **D-15:** Companero usa `gemini-2.5-flash-lite` con temperatura conversacional moderada para tono natural. El Motor de Acciones mantiene `gemini-2.5-flash` con `temperature: 0`.
- **D-16:** Companero puede ejecutar acciones con confirmacion. Si el usuario dice dentro del chat "avisale a Ana..." o "llama a Pedro", Beto puede rutear internamente al Motor de Acciones, confirmar y ejecutar.
- **D-17:** La UX no debe hablar de "modo acciones" vs "modo companero". Internamente puede haber rutas separadas, pero para el adulto mayor debe sentirse como un solo Beto que entiende si tiene que conversar o hacer algo.
- **D-18:** El historial de Companero en Phase 3 es session-only: vive en el `ViewModel` mientras el sheet esta abierto y se descarta al cerrar.

### Deferred Ideas

- Physical side-button activation/configuration for Beto.
- Personal memory for Beto: user name, family members, frequent contacts, hobbies, and preferences.
- Persistent chat history across app restarts.
- Enabling `agentic_perform_action` as an actual runnable fallback.

## Standard Stack

- Native Android Kotlin app, AGP 8.7.3, Kotlin 2.1.10, minSdk 31, targetSdk 34, Java 11. [VERIFIED: `android/gradle/libs.versions.toml`]
- Firebase AI Logic through `com.google.firebase:firebase-ai`, BoM-managed by `firebase-bom = 34.7.0`. [VERIFIED: `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`]
- Kotlin coroutines, `MutableSharedFlow` bus, Timber logging, Kotlinx Serialization strict JSON. [VERIFIED: codebase]
- Existing app uses Android Views for the floating overlay and Activity-based native speech capture. [VERIFIED: `OverlayBubble.kt`, `VoiceCaptureActivity.kt`]
- Existing Phase 3 dependency surface already includes Firebase AI and Kotlin serialization; do not add a server-side agent framework. [VERIFIED: `03-AI-SPEC.md`]

## Architecture Patterns

### Extend the Current Phase 2 Flow

The existing flow is:

`OverlayBubble` -> `AgentEvent.BubbleTapped` -> `BetoForegroundService` -> `PlanCController.startVoiceCapture()` -> `VoiceCaptureActivity` -> `AgentEvent.VoiceCaptured` -> `PlanCController.onVoiceCaptured()` -> `DeterministicMatcher` -> `IntentBranch.sendWhatsapp()` -> `TtsManager`.

Phase 3 should evolve this into:

`VoiceCaptured` -> `ActionRouter` -> deterministic matcher first -> sanitizer -> Gemini action router if needed -> strict `ToolCall` -> `ContactResolver` -> `ActionDispatcher` -> `IntentBranch` -> TTS result.

Keep `AgentBus` as the integration spine. Do not make Companion or LLM classes call service internals directly. [VERIFIED: `AgentBus.kt`, `AgentEvents.kt`, `BetoForegroundService.kt`]

### Action Contracts

Use one sealed model for action decisions:

```kotlin
sealed interface ActionDecision {
    data class SendWhatsapp(val contact: String, val message: String) : ActionDecision
    data class MakeCall(val contact: String) : ActionDecision
    data class SendSms(val contact: String, val message: String) : ActionDecision
    data class OpenMaps(val query: String) : ActionDecision
    data class AskClarification(val prompt: String) : ActionDecision
    data class FailWarmly(val reason: String) : ActionDecision
}
```

Then keep the executable Android tools behind `ActionDispatcher`. This lets deterministic matcher, Gemini tool calls, and Companion action requests share one execution path. [ASSUMED]

### Deterministic First

Expand the deterministic matcher from WhatsApp-only to the four demo families:

- call: "llama/llamame/llamalo a mi hijo"
- SMS: "mandale un sms/mensaje a Ana que ..."
- Maps: "abrime el mapa hasta la farmacia", "llevame a ..."
- WhatsApp: keep existing Phase 2 path

The deterministic layer must preserve entities exactly. No alias fallback can map `Pedro` to `hijo`. [VERIFIED: D-01, D-05]

### Contact Resolution

Create `ContactResolver` with this order:

1. Normalize input and query `DemoContacts`.
2. If no demo contact, query Android Contacts by display name / phone data.
3. Return one of: `Resolved(contact)`, `NoMatch`, `Ambiguous(candidates)`.

Android Contacts docs recommend `PhoneLookup.CONTENT_FILTER_URI` for phone-number lookup and `CONTENT_FILTER_URI` for partial-name suggestions; the plan should use Contacts provider APIs only after checking `READ_CONTACTS`. [CITED: https://developer.android.com/reference/android/provider/ContactsContract.Contacts]

Demo contacts need to be expanded from only `nieto` to at least `hijo` and `Ana`, because Phase 3 success criteria name those contacts. [VERIFIED: `DemoContacts.kt`, `ROADMAP.md`]

### Fixed Intent Tools

- Calls: use `Intent.ACTION_CALL` with `tel:` only when `CALL_PHONE` is granted; otherwise fail warmly or ask the user to grant permission. `CALL_PHONE` is a dangerous permission and directly initiates a call without Dialer confirmation. [CITED: https://developer.android.com/reference/android/Manifest.permission]
- SMS: use `Intent.ACTION_SENDTO` with `smsto:` and `Intent.EXTRA_TEXT` / `sms_body` to prefill text. This avoids needing to hold the hard-restricted `SEND_SMS` permission. [CITED: https://developer.android.com/reference/android/content/Intent]
- Maps: use Maps URLs or Android geo/navigation intents. Google recommends cross-platform Maps URLs for broad handling; Android-specific Maps intents are appropriate for mobile navigation/search. [CITED: https://developers.google.com/maps/documentation/android-sdk/intents]
- WhatsApp: keep current `wa.me` package-targeted path, but expose it through the same dispatcher. [VERIFIED: `IntentBranch.kt`]

### Firebase AI Logic Tool Calling

Use Firebase AI Logic for direct Gemini access from Android. Official Firebase docs cover Android SDK setup, model creation, system instructions, function calling, and structured output. [CITED: https://firebase.google.com/docs/ai-logic]

Planner should keep:

- `GeminiLlmClient` as one implementation of `LlmClient`.
- One action model: `gemini-2.5-flash`, `temperature = 0`.
- One companion model: `gemini-2.5-flash-lite`, `temperature ~= 0.4`.
- Tool descriptions in Spanish.
- Active tool allow-list exactly `send_whatsapp`, `make_call`, `send_sms`, `open_maps`.
- `agentic_perform_action` constant can remain in `ToolDescriptors`, but the active Firebase tool list must not include it in Phase 3. [VERIFIED: `03-AI-SPEC.md`, `ToolDescriptors.kt`]

### Sanitizer Placement

Requirement `PRIV-02` says sanitizer must be an OkHttp interceptor. Firebase AI Logic does not expose a simple project-local OkHttp client hook in the current plan context. The executable plan should implement two safeguards:

1. `Sanitizer.redact(text)` called before every `generateContent` input and before any LLM debug log.
2. A named `SanitizingInterceptor` scaffold/test that proves the intended interceptor behavior if/when a transport hook is exposed.

This is the best Phase 3-compatible path unless the implementation discovers a supported Firebase transport injection point. [ASSUMED], [VERIFIED: `PRIV-01`, `PRIV-02`]

The sanitizer must cover:

- DNI: isolated 7-8 digit Argentine document-like numbers.
- Phone numbers: Argentine mobile/fixed patterns with `+54`, `549`, spaces/dashes/parentheses.
- Cards: 13-19 digit candidates, preferably Luhn-gated to avoid redacting every long number.

Acceptance should assert raw `12345678`, `+54 9 11 6677-8899`, and `4111 1111 1111 1111` are absent from `Beto-LLM` payload logs. [VERIFIED: `PRIV-01`, ROADMAP success criterion]

### Companion UI

The roadmap and context mark Phase 3 as UI-bearing. Existing code has no Compose dependency yet. If the product decision remains "BottomSheet/card Compose" from `COMP-01`, planning needs an explicit UI design contract before coding. The UI gate should run before PLAN.md creation because Companion has locked UX decisions: immediate listening, text bar, vertical bars animation, senior-friendly simplicity, and no mode-language. [VERIFIED: `REQUIREMENTS.md`, `03-CONTEXT.md`, `.planning/config.json`]

If Compose is approved by UI-SPEC, add the minimal Compose stack (`activity-compose`, `lifecycle-viewmodel-compose`) and a single `CompanionActivity` that appears as a sheet-like translucent Activity. If UI-SPEC chooses Views for speed, implement `CompanionActivity` with XML layout and a custom vertical bar view. [ASSUMED]

## Don't Hand-Roll

- Do not hand-roll a cloud/server agent framework. Firebase AI Logic is already selected. [VERIFIED: `03-AI-SPEC.md`]
- Do not hand-roll screen operation or Accessibility agent loop in Phase 3. `agentic_perform_action` must reject. [VERIFIED: D-08, D-09]
- Do not hand-roll contact guessing heuristics that substitute contacts. Unknown/ambiguous contacts ask or fail warmly. [VERIFIED: D-05, D-07]
- Do not request or use `SEND_SMS` for demo SMS if the target behavior is prefilled SMS; `ACTION_SENDTO smsto:` avoids the hard-restricted SMS send permission. [CITED: https://developer.android.com/reference/android/Manifest.permission]
- Do not create persistent memory or chat history. Companion state is ViewModel/session-only. [VERIFIED: D-18]

## Common Pitfalls

1. **Wrong fallback semantics:** A malformed Gemini call cannot fall back to a different contact/action. Plan tests must include "llama a Pedro" not calling `hijo`. [VERIFIED: D-05]
2. **Descriptor exists but active tool disabled:** `agentic_perform_action` can remain in constants, but active Gemini tool declarations and dispatcher allow-list must reject it. [VERIFIED: D-08, D-09]
3. **SMS permission trap:** `SEND_SMS` is hard-restricted; a demo app should prefill SMS with `ACTION_SENDTO` instead of silently failing permission review. [CITED: https://developer.android.com/reference/android/Manifest.permission]
4. **Contacts permission state:** `READ_CONTACTS` exists in manifest, but runtime permission may still be missing. Contact lookup must handle missing permission with a warm failure. [CITED: https://developer.android.com/reference/android/Manifest.permission]
5. **Raw PII logs:** If `Timber.tag(LogTags.LLM).i("text=%s", raw)` appears before sanitizer, the privacy demo fails even if the network payload was sanitized. [VERIFIED: `PRIV-01`, `PRIV-02`]
6. **One prompt for two jobs:** Action routing and Companion tone need different model settings and system instructions. [VERIFIED: `03-AI-SPEC.md`]
7. **UI scope without UI-SPEC:** Companion has enough locked interaction detail that planning without `UI-SPEC.md` risks rework. [VERIFIED: `.planning/config.json`, roadmap `UI hint: yes`]

## Code Examples

### SMS Prefill

```kotlin
val intent = Intent(Intent.ACTION_SENDTO).apply {
    data = Uri.parse("smsto:${Uri.encode(contact.e164)}")
    putExtra("sms_body", message)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
context.startActivity(intent)
```

### Call Intent

```kotlin
if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
    return ActionResult.Failed("missing_call_phone_permission")
}
val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(contact.e164)}"))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
context.startActivity(intent)
```

### Maps Search Intent

```kotlin
val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
val intent = Intent(Intent.ACTION_VIEW, uri)
    .setPackage("com.google.android.apps.maps")
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
context.startActivity(intent)
```

### Strict Tool Parsing

```kotlin
private val strictJson = Json {
    ignoreUnknownKeys = false
    explicitNulls = false
}

@Serializable
data class ToolCallDto(
    val tool: String,
    val contact: String? = null,
    val message: String? = null,
    val query: String? = null,
)

fun ToolCallDto.toDecision(): ActionDecision = when (tool) {
    ToolDescriptors.SEND_WHATSAPP -> ActionDecision.SendWhatsapp(requireNotNull(contact), requireNotNull(message))
    ToolDescriptors.MAKE_CALL -> ActionDecision.MakeCall(requireNotNull(contact))
    ToolDescriptors.SEND_SMS -> ActionDecision.SendSms(requireNotNull(contact), requireNotNull(message))
    ToolDescriptors.OPEN_MAPS -> ActionDecision.OpenMaps(requireNotNull(query))
    ToolDescriptors.AGENTIC_PERFORM_ACTION -> ActionDecision.FailWarmly("agentic_disabled_phase_3")
    else -> ActionDecision.FailWarmly("unknown_tool")
}
```

## Recommended Plan Slices

1. **Action contracts + deterministic fixed tools:** Expand `DemoContacts`, action models, deterministic matcher, `IntentBranch`, and tests for call/SMS/Maps/WhatsApp.
2. **LLM router + sanitizer + cache:** Add `LlmClient`, `PromptBuilder`, Firebase Gemini client, strict parser, sanitizer, one retry, cache, and privacy tests.
3. **Dispatcher integration:** Wire `PlanCController` / service flow through `ActionRouter` and `ActionDispatcher`, including TTS confirmations and logs.
4. **Companion UI and chat:** After UI-SPEC, implement long-press launch, immediate listening, vertical bars, text bar, session-only `ViewModel`, Companion prompt, and confirmed action handoff.

## Verification Architecture

The plan should require these tests/checks:

- `./gradlew test` covers deterministic parser, sanitizer, strict tool parser, contact resolver, and dispatcher rejection of `agentic_perform_action`.
- Grep checks prove active allow-list excludes `agentic_perform_action` from Firebase tool declarations.
- Grep checks prove `DemoContacts.kt` includes `Mi hijo`/`hijo` and `Ana`.
- Grep checks prove `Beto-LLM` logs use sanitized payloads.
- Manual/device smoke checks for:
  - "llama a mi hijo" starts a call in under 5 seconds.
  - "mandale un sms a Ana ..." opens SMS prefilled.
  - "abrime el mapa hasta la farmacia" opens Maps.
  - Long-press opens Companion and starts listening.

## Confidence

- **HIGH:** Existing Phase 2 architecture should be extended through `AgentBus`, `PlanCController`, matcher, and `IntentBranch`.
- **HIGH:** `ACTION_CALL`, `ACTION_SENDTO`, Contacts provider, and Maps intents are appropriate Android primitives for Phase 3 fixed tools.
- **HIGH:** Firebase AI Logic is the selected AI stack and is already in Gradle dependencies.
- **MEDIUM:** Sanitizing OkHttp interceptor can be fully enforced with Firebase AI Logic; plan should include sanitizer as mandatory pre-call wrapper and interceptor scaffold until implementation verifies transport hook availability.
- **MEDIUM:** Compose is the intended Companion implementation from requirements, but UI-SPEC should decide exact UI structure before PLAN.md.

## Sources

- `android/app/src/main/java/com/beto/app/action/PlanCController.kt`
- `android/app/src/main/java/com/beto/app/action/DeterministicMatcher.kt`
- `android/app/src/main/java/com/beto/app/action/IntentBranch.kt`
- `android/app/src/main/java/com/beto/app/action/DemoContacts.kt`
- `android/app/src/main/java/com/beto/app/bus/AgentBus.kt`
- `android/app/src/main/java/com/beto/app/bus/AgentEvents.kt`
- `android/app/src/main/java/com/beto/app/service/BetoForegroundService.kt`
- `android/app/src/main/java/com/beto/app/overlay/OverlayBubble.kt`
- `android/app/src/main/java/com/beto/app/llm/ToolDescriptors.kt`
- `.planning/phases/03-motor-de-acciones-completo-companero/03-CONTEXT.md`
- `.planning/phases/03-motor-de-acciones-completo-companero/03-AI-SPEC.md`
- Android permissions: https://developer.android.com/reference/android/Manifest.permission
- Android Intent `ACTION_SENDTO`: https://developer.android.com/reference/android/content/Intent
- Android Contacts: https://developer.android.com/reference/android/provider/ContactsContract.Contacts
- Google Maps intents: https://developers.google.com/maps/documentation/android-sdk/intents
- Firebase AI Logic: https://firebase.google.com/docs/ai-logic

## RESEARCH COMPLETE
