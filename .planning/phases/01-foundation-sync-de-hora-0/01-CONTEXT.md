# Phase 1: Foundation & Sync de Hora 0 - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase entrega: un proyecto Android instalable en el teléfono de demo con todos los contratos compartidos congelados antes de paralelizar a 5 devs. Concretamente:

- Proyecto Kotlin compilando con AGP 8.7.x / Gradle 8.10 / minSdk 31, Firebase configurado, instalable en el teléfono dedicado.
- `AndroidManifest.xml` con `BetoForegroundService` (`foregroundServiceType="microphone"`), `BetoAccessibilityService`, todos los permisos críticos.
- `accessibility_service_config.xml` con flags y eventos correctos.
- Contratos compartidos mergeados: `AgentBus` singleton (SharedFlow), `AgentEvents.kt` (sealed), `ToolDescriptors.kt`.
- Burbuja flotante visible permanentemente sobre cualquier app (drag + magnet a borde) usando el `project-logo.png` como avatar y un ring de color para los 5 estados.
- `TtsManager` inicializado en `Application.onCreate()` con cascada de Locale; al primer boot del servicio, Beto pronuncia "Hola, soy Beto. Estoy acá para ayudarte."
- Pre-flight check al boot que valida `canDrawOverlays` + `isAccessibilityEnabled` + TTS init OK; si falla, TTS lo informa y abre Settings con deep link automático.
- Logging Timber con tags `Beto-Accessibility`, `Beto-LLM`, `Beto-Action`, `Beto-STT`, `Beto-Intent`.

**Out of scope para esta fase:** captura de voz real (Phase 2), Intents (Phase 2-3), LLM client (Phase 3), loop agéntico (Phase 4), Modo Compañero (Phase 3), tipografía senior + estados visuales completos (Phase 4 — acá solo placeholder de los 5 estados).

</domain>

<decisions>
## Implementation Decisions

### Repo Layout & Package
- **D-01:** El proyecto Android vive en una **subcarpeta `android/`** del repo, no en la raíz. La raíz queda limpia con `README.md`, `.planning/`, `platanus-hack-project.json`, `project-logo.png`, `CLAUDE.md`, `.git/`. Si después se suma backend/web es trivial.
- **D-02:** **Application ID = `com.beto.app`**. Hardcoded en `android/app/build.gradle.kts`. Imports de aplicación: `import com.beto.app.bus.AgentBus`, etc. Estructura inicial de paquetes: `com.beto.app.{bus, service, overlay, voice, llm, action, agent, companion, ui, util}` — superficies disjuntas para que los 5 devs no se pisen.

### Identidad Visual de la Burbuja
- **D-03:** La burbuja muestra el `project-logo.png` (asset existente en raíz, copiar a `android/app/src/main/res/drawable/`) dentro de un círculo. **Cero assets nuevos** — identidad consistente con README/submission.
- **D-04:** Los 5 estados visuales (idle / listening / thinking / speaking / error) se comunican con **el color del ring/anillo alrededor del logo + animación**, NO con cambios al logo. En esta fase solo dejamos placeholder con color por defecto (gris) — los 5 estados completos son trabajo de Phase 4 (`OVERLAY-05`).
- **D-05:** Burbuja dragable con magnet a borde implementado con WindowManager + Views clásicas (NO Compose). Tamaño inicial sugerido: 64dp diámetro. Posición inicial al primer launch: borde derecho, mitad vertical de la pantalla. La persistencia de posición entre boots queda como nice-to-have (no bloquea la fase).

### Pre-flight Check UX
- **D-06:** Al boot del servicio, el pre-flight check valida en este orden: (a) `Settings.canDrawOverlays(context)`, (b) `isAccessibilityEnabled` (verificar que `BetoAccessibilityService` esté listado en `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`), (c) `TtsManager.isReady` (callback `onInit` completado con `SUCCESS`).
- **D-07:** Si **un permiso falta**: TTS dice por voz qué falta ("Para ayudarte necesito permiso para mostrarme arriba de las apps. Te llevo." / "Necesito acceso a la accesibilidad para entender la pantalla. Te llevo.") **+ deep link automático** a la pantalla de Settings respectiva (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`, `Settings.ACTION_ACCESSIBILITY_SETTINGS`). Si TTS falla (init no OK), no hay voz — solo se abre el deep link.
- **D-08:** Si faltan **varios permisos**, flow secuencial: resolver uno → re-check al volver al foreground → resolver siguiente. NO mostrar lista al usuario (sería confuso para adulto mayor).
- **D-09:** El pre-flight check vive en una `MainActivity` minimalista (NO en el servicio) que se lanza al primer install, hace el chequeo y, si todo está OK, arranca el `BetoForegroundService` y se cierra. Si todo está OK al next-launch, también puede ser invisible (intent que arranca el servicio directo).

### Frase de Boot TTS
- **D-10:** Frase de boot exacta: **"Hola, soy Beto. Estoy acá para ayudarte."** Pronunciada por `TtsManager` apenas el `BetoForegroundService` arranca **y** el TTS está listo. Si TTS no está listo aún, la frase se encola en la cola interna pre-init y se reproduce ni bien `onInit` recibe `SUCCESS` (Pitfall #3 — TTS race).

### AgentBus / AgentEvents Contract
- **D-11:** `AgentBus` es un `object` singleton en `com.beto.app.bus`. Expone:
  - `events: SharedFlow<AgentEvent>` (replay = 0, extraBufferCapacity = 64)
  - `commands: SharedFlow<AgentCommand>` (replay = 0, extraBufferCapacity = 16)
  - `suspend fun emit(event: AgentEvent)` y `suspend fun command(cmd: AgentCommand)`
- **D-12:** `AgentEvent` es una `sealed class` (no `sealed interface` por simplicidad de @Serializable a futuro). Variantes que se mergean en esta fase (placeholder, sin lógica): `BootCompleted`, `PermissionsMissing(missing: List<String>)`, `BubbleTapped`, `BubbleLongPressed`, `TtsSpoke(text: String)`, `TtsFailed(reason: String)`, `ServiceStarted`, `ServiceStopped`. Phase 2-4 agregan variantes (VoiceCaptured, IntentClassified, etc.) — esos son TODOs en `AgentEvents.kt`.
- **D-13:** `ToolDescriptors.kt` se mergea con stubs de los 5 tools (`send_whatsapp`, `make_call`, `send_sms`, `open_maps`, `agentic_perform_action`) con schemas en español pero **comentados o con TODO** en esta fase — Phase 3 (LLM-02) los implementa. El propósito de mergearlos en Phase 1 es que los devs vean dónde van y no dupliquen archivos.

### Logging
- **D-14:** Timber inicializado en `BetoApplication.onCreate()`. Tags estándar (uso de `Timber.tag("Beto-XXX").d(...)`):
  - `Beto-Accessibility` — eventos del AccessibilityService
  - `Beto-LLM` — payloads sanitizados, respuestas, errores
  - `Beto-Action` — ejecución de Intents y dispatcher
  - `Beto-STT` — eventos de SpeechRecognizer / VoiceCaptureActivity
  - `Beto-Intent` — Intents armados antes de lanzarlos
  - `Beto-TTS` — síntesis de voz, init, race conditions
  - `Beto-Bus` — emisiones del AgentBus

### Notificación Foreground Service
- **D-15:** Notificación persistente de `BetoForegroundService` con: ícono del logo, título "Beto está acá", texto "Tocame en la burbuja para hablar", canal `beto_service` con `IMPORTANCE_LOW` (sin sonido ni vibración). PendingIntent al tap: lanza `MainActivity`. Texto y título fijos en esta fase — Phase 4 puede agregar variantes según estado del servicio si suma valor.

### Claude's Discretion
- Estructura interna de carpetas dentro de `com.beto.app.*` — los 9 sub-paquetes son guía, podés agruparlos diferente si la organización emerge distinta durante implementación.
- Versiones exactas de dependencias auxiliares (Timber, kotlinx-coroutines, kotlinx-serialization) — usar las del SUMMARY.md, pero si encontrás incompatibilidades, ajustar dentro del rango compatible documentado.
- Iconografía de la notificación del FGS — usar `project-logo.png` redondeado o un drawable derivado, lo que se vea decente con el sistema de notificaciones de Android.
- Color exacto del ring de la burbuja para el placeholder en Phase 1 (`#888888` gris, `#0066CC` azul Beto, lo que se vea bien con el logo) — Phase 4 lo va a sobrescribir con la paleta de los 5 estados.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project-level
- `.planning/PROJECT.md` — Visión, core value, requirements bucket, constraints, key decisions.
- `.planning/REQUIREMENTS.md` — 56 requirements v1 con IDs y traceability a fases.
- `.planning/ROADMAP.md` — 5 fases del MVP, Phase 1 success criteria.
- `.planning/STATE.md` — Estado actual del proyecto.

### Research (decisiones cerradas + pitfalls)
- `.planning/research/SUMMARY.md` — Síntesis ejecutiva con decisiones consolidadas, build order paralelizado, hard limits y guías para el roadmap.
- `.planning/research/STACK.md` — Stack final con versiones (Kotlin 2.1.10, AGP 8.7.x, Firebase AI Logic, Gemini 2.5 Flash, NO Hilt/Compose-en-burbuja, etc.).
- `.planning/research/ARCHITECTURE.md` — Componentes, AgentBus singleton patrón, separación FGS/AS/VoiceCaptureActivity, TYPE_ACCESSIBILITY_OVERLAY vs TYPE_APPLICATION_OVERLAY.
- `.planning/research/PITFALLS.md` — 12 pitfalls con plan B; críticos para Phase 1: #1 (AS desactivado silenciosamente), #3 (TTS race), #8 (FGS killed por OEM), #10 (overlay sobre apps con FLAG_SECURE), #11 (FGS sin foregroundServiceType en Android 14+), #12 (permisos resetean tras `adb install -r`).
- `.planning/research/FEATURES.md` — Categorización table stakes / diferenciadores / anti-features.

### Project conventions
- `CLAUDE.md` — Reglas globales del proyecto (tono Beto, modularidad estricta, logs Beto-XXX, fallback elegante en errores, sin alucinaciones sobre AccessibilityService).
- `README.md` — Identidad de team-12, integrantes, track Vertical AI.
- `platanus-hack-project.json` — Submission file (queda para Phase 5).

### External docs (oficiales Android — para el researcher si hace falta)
- Firebase AI Logic Android: https://firebase.google.com/docs/ai-logic/get-started
- AccessibilityService: https://developer.android.com/guide/topics/ui/accessibility/service
- Foreground service types Android 14+: https://developer.android.com/about/versions/14/changes/fgs-types-required
- WindowManager.LayoutParams (TYPE_ACCESSIBILITY_OVERLAY): https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- TextToSpeech.OnInitListener: https://developer.android.com/reference/android/speech/tts/TextToSpeech.OnInitListener

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `project-logo.png` (raíz del repo, 1000×1000 png) — se copia a `android/app/src/main/res/drawable/` y se usa como avatar de la burbuja flotante y como ícono de la notificación del FGS.
- `CLAUDE.md` (raíz) — reglas globales del proyecto (tono Beto, tags de log, modularidad). El generated CLAUDE.md también vive acá tras `gsd-sdk generate-claude-md`.
- `platanus-hack-project.json` — placeholder de submission (no se toca en Phase 1, queda para Phase 5).

### Established Patterns
- **No hay código Android existente** — proyecto greenfield. Los patrones a establecer en Phase 1 (singleton bus, sealed events, services con Foreground type "microphone", overlay con TYPE_ACCESSIBILITY_OVERLAY, TTS pre-warmed) son contratos para todas las fases siguientes.
- Las decisiones cerradas en SUMMARY.md son patrones obligatorios — desviarse requiere justificación explícita.

### Integration Points
- **`AgentBus` es el único punto de comunicación** entre `BetoForegroundService`, `BetoAccessibilityService`, `MainActivity` y futuras `VoiceCaptureActivity` / `CompanionActivity`. Cualquier dev que agregue un componente debe usarlo (no Binder, no AIDL, no broadcasts custom).
- El `AndroidManifest.xml` declara los servicios con sus tipos, permisos y filters — Phase 2-4 NO modifica el manifest salvo para sumar declaraciones nuevas (ej. CompanionActivity).
- `TtsManager` es singleton accessible desde cualquier componente — Phase 2-4 lo consumen sin re-inicializarlo.

</code_context>

<specifics>
## Specific Ideas

- **Logo del proyecto como identidad de la burbuja** — el `project-logo.png` ya existe en raíz y se usa tanto en la submission como en la burbuja. Identidad consistente sin trabajo extra de design.
- **Frase de boot fija**: "Hola, soy Beto. Estoy acá para ayudarte." — no parametrizable por config, hardcoded en `BetoForegroundService.onCreate()` o `TtsManager.speakBootGreeting()`.
- **Deep link automático a Settings** — Beto guía al usuario sin que tenga que navegar manualmente. Requiere `Settings.ACTION_ACCESSIBILITY_SETTINGS` y `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` con `Intent.FLAG_ACTIVITY_NEW_TASK`.
- **Atajo a Settings → Accessibility desde el código** — DEMO-03 lo pide para Phase 5, pero es trivial implementarlo en Phase 1 como parte del flow del pre-flight (la misma función se reutiliza).

</specifics>

<deferred>
## Deferred Ideas

- **Persistir posición de la burbuja entre boots** (SharedPreferences) — nice-to-have de UX que no bloquea la fase. Si sobra tiempo en Phase 4, sumarlo. Si no, la burbuja vuelve a la posición default al primer launch.
- **Iconografía/expresiones distintas para Beto** (avatar caricaturizado que cambia entre estados) — fue rechazado para no crear assets nuevos en hackathon. Roadmap post-MVP.
- **Notificación del FGS variable según estado** (cambiar título/texto cuando Beto está escuchando o ejecutando) — Phase 4 puede sumarlo si suma valor; Phase 1 lo deja fijo.
- **Onboarding visual completo** (primer launch con tutorial paso-a-paso) — Out of Scope explícito en PROJECT.md, queda v2.
- **Wake word "Beto"** — Out of Scope, queda v2.
- **Persistencia de logs / crash reporting** (Timber tree custom + Firebase Crashlytics) — útil para iteración post-hackathon, no necesario para demo en vivo.

</deferred>

---

*Phase: 1-Foundation & Sync de Hora 0*
*Context gathered: 2026-05-09*
