---
phase: 01-foundation-sync-de-hora-0
plan: 02
type: execute
wave: 2
depends_on: [01]
files_modified:
  - android/app/src/main/java/com/beto/app/BetoApplication.kt
  - android/app/src/main/java/com/beto/app/MainActivity.kt
  - android/app/src/main/java/com/beto/app/bus/AgentBus.kt
  - android/app/src/main/java/com/beto/app/bus/AgentEvents.kt
  - android/app/src/main/java/com/beto/app/llm/ToolDescriptors.kt
  - android/app/src/main/java/com/beto/app/voice/TtsManager.kt
  - android/app/src/main/java/com/beto/app/util/LogTags.kt
  - android/app/src/main/java/com/beto/app/util/PreflightCheck.kt
  - android/app/src/main/java/com/beto/app/service/BetoAccessibilityService.kt
autonomous: true
requirements: [SETUP-05, BUS-01, BUS-02, BUS-03, VOICE-01, VOICE-02, DEMO-04]
must_haves:
  truths:
    - "BetoApplication.onCreate() inicializa Timber con tags Beto-XXX y arranca TtsManager pre-warmed con cascada de Locale es-AR → es-419 → es-ES → es → en-US."
    - "AgentBus es accesible como `object` singleton desde cualquier componente; emitir un AgentEvent.BootCompleted en el FGS y recibirlo en MainActivity (o test) funciona sin colisiones."
    - "Si el usuario tapea el ícono del launcher, MainActivity ejecuta el pre-flight check (overlay → accessibility → TTS init) y, si todo OK, arranca BetoForegroundService y se cierra; si falta un permiso, TTS lo dice y abre el deep link a Settings."
    - "BetoAccessibilityService esqueleto está conectado: onServiceConnected emite AgentEvent.ServiceStarted al bus; onAccessibilityEvent loguea con tag Beto-Accessibility (sin lógica real — Phase 4 la agrega)."
    - "ToolDescriptors.kt mergea con stubs de los 5 tools (send_whatsapp, make_call, send_sms, open_maps, agentic_perform_action) — cuerpos comentados, schemas en español como TODO Phase 3."
    - "AgentEvent es sealed class con las 8 variantes Phase 1 (BootCompleted, PermissionsMissing, BubbleTapped, BubbleLongPressed, TtsSpoke, TtsFailed, ServiceStarted, ServiceStopped)."
  artifacts:
    - path: "android/app/src/main/java/com/beto/app/bus/AgentBus.kt"
      provides: "Singleton AgentBus con events: SharedFlow<AgentEvent> (replay=0, extraBufferCapacity=64) y commands: SharedFlow<AgentCommand> (replay=0, extraBufferCapacity=16) + suspend fun emit + suspend fun command"
      contains: "object AgentBus"
      min_lines: 25
    - path: "android/app/src/main/java/com/beto/app/bus/AgentEvents.kt"
      provides: "sealed class AgentEvent y sealed class AgentCommand con variantes Phase 1"
      contains: "sealed class AgentEvent"
      min_lines: 35
    - path: "android/app/src/main/java/com/beto/app/llm/ToolDescriptors.kt"
      provides: "Stubs comentados de los 5 tools que Phase 3 implementa"
      contains: "send_whatsapp"
    - path: "android/app/src/main/java/com/beto/app/voice/TtsManager.kt"
      provides: "Singleton TtsManager con init en Application.onCreate(), cascada de Locale, cola interna pre-init, isReady flag, speak(text), speakBootGreeting()"
      contains: "TextToSpeech"
      min_lines: 80
    - path: "android/app/src/main/java/com/beto/app/util/PreflightCheck.kt"
      provides: "Función checkPermissions(context) que retorna lista de missing y deep-link launchers"
      contains: "Settings.canDrawOverlays"
    - path: "android/app/src/main/java/com/beto/app/MainActivity.kt"
      provides: "Activity minimalista que ejecuta pre-flight y arranca BetoForegroundService"
      contains: "MainActivity"
    - path: "android/app/src/main/java/com/beto/app/service/BetoAccessibilityService.kt"
      provides: "Esqueleto del AS — onServiceConnected, onAccessibilityEvent loggers, emite ServiceStarted al bus"
      contains: "AccessibilityService"
    - path: "android/app/src/main/java/com/beto/app/util/LogTags.kt"
      provides: "Constantes con los 7 tags de logging estándar (Beto-Accessibility, Beto-LLM, Beto-Action, Beto-STT, Beto-Intent, Beto-TTS, Beto-Bus)"
      contains: "Beto-Bus"
    - path: "android/app/src/main/java/com/beto/app/BetoApplication.kt"
      provides: "Application class — init Timber + TtsManager.init(this) + log de boot"
      contains: "Timber.plant"
  key_links:
    - from: "android/app/src/main/java/com/beto/app/BetoApplication.kt"
      to: "TtsManager"
      via: "TtsManager.init(this) en onCreate"
      pattern: "TtsManager.init"
    - from: "android/app/src/main/java/com/beto/app/MainActivity.kt"
      to: "PreflightCheck"
      via: "PreflightCheck.checkPermissions(this)"
      pattern: "checkPermissions"
    - from: "android/app/src/main/java/com/beto/app/MainActivity.kt"
      to: "BetoForegroundService (declarado en manifest, implementado en Plan 03)"
      via: "startForegroundService(Intent(this, BetoForegroundService::class.java))"
      pattern: "startForegroundService"
    - from: "android/app/src/main/java/com/beto/app/service/BetoAccessibilityService.kt"
      to: "AgentBus"
      via: "AgentBus.emit(AgentEvent.ServiceStarted) en onServiceConnected"
      pattern: "AgentBus.emit"
    - from: "android/app/src/main/java/com/beto/app/bus/AgentEvents.kt"
      to: "AgentBus.events SharedFlow"
      via: "object AgentBus posee MutableSharedFlow<AgentEvent>"
      pattern: "MutableSharedFlow"
---

<objective>
Mergear los CONTRATOS COMPARTIDOS de Phase 1 — todo lo que los 5 devs van a consumir desde Phase 2 en adelante. Una vez merged este plan, los tracks A-E pueden trabajar en paralelo sobre superficies disjuntas sabiendo que: el bus existe, los eventos están definidos, las tools tienen sus IDs reservados, el TTS habla, el logger tagea consistente, y el pre-flight check funciona.

Purpose: Sin estos contratos, dos devs van a inventar dos AgentBus distintos, dos sets de eventos que no se hablan, dos schemes de logging. Phase 1 falla y la paralelización colapsa. Este plan es el "sync de hora 0" del nombre de la fase.

Output:
- AgentBus singleton funcional con SharedFlow.
- AgentEvent sealed class con 8 variantes Phase 1.
- ToolDescriptors con stubs comentados.
- TtsManager con cascada de Locale + cola pre-init (Pitfall #3).
- BetoApplication que conecta todo en onCreate.
- MainActivity con pre-flight check (DEMO-04) + deep links automáticos a Settings (D-07).
- BetoAccessibilityService esqueleto.

User decisions implementadas (CONTEXT.md):
- D-09: MainActivity minimalista lanza pre-flight; si todo OK arranca BetoForegroundService y se cierra
- D-10: Frase de boot exacta "Hola, soy Beto. Estoy acá para ayudarte."
- D-11: AgentBus en `com.beto.app.bus` con events (replay=0, extraBufferCapacity=64) y commands (replay=0, extraBufferCapacity=16)
- D-12: AgentEvent sealed class con 8 variantes Phase 1
- D-13: ToolDescriptors con 5 stubs comentados
- D-14: Timber con tags Beto-XXX
- D-06, D-07, D-08: Pre-flight order + deep links + flow secuencial si faltan varios

Pitfalls mitigados:
- #3 (TTS race): cola interna pre-init, init en Application.onCreate()
- #11 (FGS sin foregroundServiceType): MainActivity usa startForegroundService() apuntando a la declaración del manifest de Plan 01
- #12 (permisos reset): pre-flight check al boot detecta y guía
</objective>

<execution_context>
@/Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12/.claude/get-shit-done/workflows/execute-plan.md
@/Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/01-foundation-sync-de-hora-0/01-CONTEXT.md
@.planning/phases/01-foundation-sync-de-hora-0/01-01-SUMMARY.md
@.planning/research/ARCHITECTURE.md
@.planning/research/PITFALLS.md
@CLAUDE.md
@android/app/src/main/AndroidManifest.xml
@android/app/build.gradle.kts

<interfaces>
<!-- Contratos que este plan ESTABLECE para que Phase 2-4 los consuman. -->

**AgentBus (target)** — `com.beto.app.bus.AgentBus`:
```kotlin
object AgentBus {
    private val _events = MutableSharedFlow<AgentEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val _commands = MutableSharedFlow<AgentCommand>(replay = 0, extraBufferCapacity = 16)
    val commands: SharedFlow<AgentCommand> = _commands.asSharedFlow()

    suspend fun emit(event: AgentEvent) { _events.emit(event) }
    suspend fun command(cmd: AgentCommand) { _commands.emit(cmd) }
}
```

**AgentEvent (target — Phase 1 variants only)** — `com.beto.app.bus.AgentEvents.kt`:
```kotlin
sealed class AgentEvent {
    object BootCompleted : AgentEvent()
    data class PermissionsMissing(val missing: List<String>) : AgentEvent()
    object BubbleTapped : AgentEvent()
    object BubbleLongPressed : AgentEvent()
    data class TtsSpoke(val text: String) : AgentEvent()
    data class TtsFailed(val reason: String) : AgentEvent()
    object ServiceStarted : AgentEvent()
    object ServiceStopped : AgentEvent()
    // TODO Phase 2-4: VoiceCaptured, IntentClassified, ActionExecuted, ToolFailed, TreeSnapshot, etc.
}

sealed class AgentCommand {
    data class Speak(val text: String) : AgentCommand()
    object StartVoiceCapture : AgentCommand()
    // TODO Phase 2-4: ExecuteToolCall, RunAgenticLoop, etc.
}
```

**TtsManager (target)** — `com.beto.app.voice.TtsManager`:
```kotlin
object TtsManager {
    @Volatile var isReady: Boolean = false
        private set
    fun init(context: Context)
    fun speak(text: String)              // encola si !isReady, flush en onInit SUCCESS
    fun speakBootGreeting()              // exacto: "Hola, soy Beto. Estoy acá para ayudarte."
    fun shutdown()
}
```

**LogTags (target)** — `com.beto.app.util.LogTags`:
```kotlin
object LogTags {
    const val ACCESSIBILITY = "Beto-Accessibility"
    const val LLM = "Beto-LLM"
    const val ACTION = "Beto-Action"
    const val STT = "Beto-STT"
    const val INTENT = "Beto-Intent"
    const val TTS = "Beto-TTS"
    const val BUS = "Beto-Bus"
}
```

**PreflightCheck (target)** — `com.beto.app.util.PreflightCheck`:
```kotlin
data class PreflightResult(
    val overlayOk: Boolean,
    val accessibilityOk: Boolean,
    val ttsOk: Boolean,
) { val allOk: Boolean = overlayOk && accessibilityOk && ttsOk }

object PreflightCheck {
    fun check(context: Context): PreflightResult
    fun openOverlaySettings(context: Context)
    fun openAccessibilitySettings(context: Context)
}
```

**Versiones cerradas (Plan 01 SUMMARY confirma):**
- Kotlin 2.1.10, AGP 8.7.3, Gradle 8.10
- minSdk 31, targetSdk 34
- Firebase BoM 33.5.1, kotlinx-coroutines 1.8.1, kotlinx-serialization 1.7.3, Timber 5.0.1
- coreLibraryDesugaring 2.1.2

**Imports clave que los archivos van a usar:**
- `import kotlinx.coroutines.flow.MutableSharedFlow`
- `import kotlinx.coroutines.flow.SharedFlow`
- `import kotlinx.coroutines.flow.asSharedFlow`
- `import android.speech.tts.TextToSpeech`
- `import android.speech.tts.TextToSpeech.OnInitListener`
- `import android.accessibilityservice.AccessibilityService`
- `import android.view.accessibility.AccessibilityEvent`
- `import android.provider.Settings`
- `import timber.log.Timber`
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Crear AgentBus + AgentEvents + ToolDescriptors stubs + LogTags (los contratos compartidos)</name>
  <files>
    android/app/src/main/java/com/beto/app/bus/AgentBus.kt,
    android/app/src/main/java/com/beto/app/bus/AgentEvents.kt,
    android/app/src/main/java/com/beto/app/llm/ToolDescriptors.kt,
    android/app/src/main/java/com/beto/app/util/LogTags.kt
  </files>
  <action>
Crear los 4 archivos de contratos. Son chicos pero cada uno es load-bearing — un cambio de signatura acá rompe a 5 devs en simultáneo desde Phase 2.

**1. `LogTags.kt`** (D-14, SETUP-05):
```kotlin
package com.beto.app.util

/**
 * Tags estándar para Timber. Todos los components DEBEN usar estos exactamente.
 * Filtrar en logcat: `adb logcat -s "Beto-Accessibility:D" "Beto-LLM:D" ...`
 */
object LogTags {
    const val ACCESSIBILITY = "Beto-Accessibility"
    const val LLM = "Beto-LLM"
    const val ACTION = "Beto-Action"
    const val STT = "Beto-STT"
    const val INTENT = "Beto-Intent"
    const val TTS = "Beto-TTS"
    const val BUS = "Beto-Bus"
}
```

**2. `AgentEvents.kt`** (D-12, BUS-02 — nota: BUS-02 menciona variantes Phase 2-4 (VoiceCaptured, IntentClassified, etc.). Las dejamos como TODO comentado siguiendo D-12 que es explícito sobre qué entra en Phase 1):

```kotlin
package com.beto.app.bus

/**
 * Eventos que cualquier componente puede emitir al AgentBus.
 *
 * Phase 1 mergea las 8 variantes acá. Phase 2-4 agregan más como TODOs:
 *   - VoiceCaptured(text), VoiceCaptureFailed
 *   - IntentClassified(toolCall), ActionExecuted, ActionFailed
 *   - TreeSnapshot, AgenticIterationComplete, AgenticAborted
 */
sealed class AgentEvent {
    /** Emitido cuando BetoForegroundService completó startup y TTS está pronto. */
    object BootCompleted : AgentEvent()

    /** Emitido por MainActivity / PreflightCheck cuando faltan permisos críticos. */
    data class PermissionsMissing(val missing: List<String>) : AgentEvent()

    /** Tap corto en la burbuja flotante. Phase 2 lo conecta a captura de voz. */
    object BubbleTapped : AgentEvent()

    /** Long-press en la burbuja flotante. Phase 3 lo conecta al Modo Compañero. */
    object BubbleLongPressed : AgentEvent()

    /** TTS pronunció una frase exitosamente. */
    data class TtsSpoke(val text: String) : AgentEvent()

    /** TTS falló. Razón típica: init no completado, voz no descargada, idioma no soportado. */
    data class TtsFailed(val reason: String) : AgentEvent()

    /** BetoAccessibilityService o BetoForegroundService onCreate / onServiceConnected. */
    object ServiceStarted : AgentEvent()

    /** BetoAccessibilityService o BetoForegroundService onDestroy. */
    object ServiceStopped : AgentEvent()

    // TODO Phase 2: VoiceCaptured(text: String), VoiceCaptureFailed(reason: String)
    // TODO Phase 2-3: IntentClassified(toolCall: ToolCall), ActionExecuted(name: String), ToolFailed(name: String, reason: String)
    // TODO Phase 4: TreeSnapshot(nodeRefs: List<NodeRef>), AgenticIterationComplete(iter: Int), AgenticAborted(reason: String)
}

/**
 * Comandos que un componente envía a otro vía bus. Diferencia con AgentEvent:
 * los Commands son intent-to-do, los Events son already-happened.
 */
sealed class AgentCommand {
    data class Speak(val text: String) : AgentCommand()
    object StartVoiceCapture : AgentCommand()

    // TODO Phase 2-4: ExecuteToolCall, RunAgenticLoop, OpenCompanion, etc.
}
```

**3. `AgentBus.kt`** (D-11, BUS-01):

```kotlin
package com.beto.app.bus

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

/**
 * In-process pub/sub singleton. ÚNICO punto de comunicación entre BetoForegroundService,
 * BetoAccessibilityService, MainActivity, VoiceCaptureActivity (Phase 2), CompanionActivity (Phase 3).
 *
 * NO usar Binder, AIDL, broadcasts custom — todo va por acá.
 *
 * Replay = 0 (no buffer histórico) + extraBufferCapacity para evitar bloquear emisores rápidos.
 * BufferOverflow.DROP_OLDEST garantiza que si un consumer es lento, no bloquea al emisor.
 */
object AgentBus {
    private val _events = MutableSharedFlow<AgentEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val _commands = MutableSharedFlow<AgentCommand>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val commands: SharedFlow<AgentCommand> = _commands.asSharedFlow()

    suspend fun emit(event: AgentEvent) {
        Timber.tag("Beto-Bus").d("emit -> %s", event::class.simpleName)
        _events.emit(event)
    }

    suspend fun command(cmd: AgentCommand) {
        Timber.tag("Beto-Bus").d("command -> %s", cmd::class.simpleName)
        _commands.emit(cmd)
    }
}
```

**4. `ToolDescriptors.kt`** (D-13, BUS-03):

```kotlin
package com.beto.app.llm

/**
 * Descriptores de las 5 tools que Phase 3 va a registrar en el LLM (vía Firebase AI Logic /
 * Gemini function calling).
 *
 * Phase 1 mergea estos STUBS COMENTADOS para reservar IDs y schemas. Phase 3 (LLM-02) los
 * activa registrando con el SDK de Firebase AI.
 *
 * Reglas (Pitfall #6):
 *  - Descripciones EN ESPAÑOL (mismo idioma que el system prompt — evita confusión bilingüe).
 *  - `required` explícito en cada parámetro.
 *  - Sin parámetros opcionales en Phase 3 (mejorar después).
 *  - Allow-list de nombres = exactamente estos 5.
 */
@Suppress("UnusedPrivateMember", "Unused")
internal object ToolDescriptors {

    // TODO Phase 3 (LLM-02): registrar con FunctionDeclaration de Firebase AI SDK.
    // Estructura tentativa:
    //
    // val SEND_WHATSAPP = FunctionDeclaration.newBuilder()
    //     .setName("send_whatsapp")
    //     .setDescription("Envía un mensaje de WhatsApp al contacto indicado. " +
    //                     "Usar SIEMPRE para WhatsApp en lugar del loop agéntico.")
    //     .addRequiredProperty("contact", "string", "Nombre del contacto (ej: 'mi nieto', 'Ana')")
    //     .addRequiredProperty("message", "string", "Texto del mensaje a enviar")
    //     .build()

    const val SEND_WHATSAPP = "send_whatsapp"
    const val MAKE_CALL = "make_call"
    const val SEND_SMS = "send_sms"
    const val OPEN_MAPS = "open_maps"
    const val AGENTIC_PERFORM_ACTION = "agentic_perform_action"

    val ALL_TOOL_NAMES: Set<String> = setOf(
        SEND_WHATSAPP,
        MAKE_CALL,
        SEND_SMS,
        OPEN_MAPS,
        AGENTIC_PERFORM_ACTION,
    )

    // TODO Phase 3: descripciones completas en español + ejemplos few-shot:
    //
    // make_call(contact: String) — "Llama por teléfono al contacto indicado. Ej: 'llamá a mi hijo'."
    // send_sms(contact: String, message: String) — "Envía un SMS al contacto. Ej: 'mandale un mensaje a Ana'."
    // open_maps(query: String) — "Abre Google Maps con la búsqueda. Ej: 'abrime el mapa hasta la farmacia'."
    // agentic_perform_action(goal: String) — "Ejecuta una acción genérica leyendo la pantalla.
    //                                          Usar SOLO cuando ningún otro tool aplica."
}
```

NOTA: el archivo no exporta funciones ejecutables — solo IDs y allow-list. Phase 3 hace el wiring real con `firebase-ai` SDK.
  </action>
  <verify>
    <automated>
cd /Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12/android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20 | tee /tmp/beto-build-2-1.log; grep -q 'BUILD SUCCESSFUL\|UP-TO-DATE' /tmp/beto-build-2-1.log && grep -v '^#' app/src/main/java/com/beto/app/bus/AgentBus.kt | grep -c 'object AgentBus' && grep -v '^#' app/src/main/java/com/beto/app/bus/AgentEvents.kt | grep -c 'sealed class AgentEvent' && echo "CONTRACTS OK"
    </automated>
Smoke test:
1. `./gradlew :app:compileDebugKotlin` debe compilar sin errores.
2. `grep -r "import com.beto.app.bus.AgentBus" android/app/src/main/java/` retorna 0 (todavía nadie lo importa — Plan 03 lo va a usar).
3. `grep -c 'object AgentBus' android/app/src/main/java/com/beto/app/bus/AgentBus.kt` retorna 1.
  </verify>
  <done>
- AgentBus singleton compila con SharedFlow events + commands (replay/buffer per D-11).
- AgentEvent sealed class con las 8 variantes Phase 1.
- AgentCommand sealed class con Speak + StartVoiceCapture.
- ToolDescriptors con los 5 IDs como constantes + ALL_TOOL_NAMES set + cuerpos comentados.
- LogTags object con las 7 constantes.
- Cero referencias a AgentEvent/AgentCommand variantes Phase 2-4 (esas quedan como TODO comentado).
  </done>
</task>

<task type="auto">
  <name>Task 2: TtsManager con cascada de Locale + cola pre-init + BetoApplication + LogTags wiring</name>
  <files>
    android/app/src/main/java/com/beto/app/voice/TtsManager.kt,
    android/app/src/main/java/com/beto/app/BetoApplication.kt
  </files>
  <action>
Implementar `TtsManager` y `BetoApplication`. El TtsManager mitiga Pitfall #3 (TTS race) — primer `speak()` se pierde si init no terminó. Solución: cola interna que flush en `onInit(SUCCESS)`.

**1. `TtsManager.kt`** (VOICE-01, VOICE-02, D-10):

```kotlin
package com.beto.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Singleton TTS — init en BetoApplication.onCreate(), cola interna pre-init para evitar
 * Pitfall #3 (TTS race condition: primer speak() se pierde porque onInit no completó).
 *
 * Cascada de Locale (D-10): es-AR → es-419 → es-ES → es → en-US.
 *
 * Frase de boot exacta (D-10): "Hola, soy Beto. Estoy acá para ayudarte."
 */
object TtsManager {
    @Volatile
    var isReady: Boolean = false
        private set

    private var tts: TextToSpeech? = null
    private val pendingQueue: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    private val utteranceCounter = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val LOCALE_CASCADE = listOf(
        Locale("es", "AR"),
        Locale("es", "419"),  // Spanish — Latin America (algunos engines la prefieren)
        Locale("es", "ES"),
        Locale("es"),
        Locale("en", "US"),
    )

    fun init(context: Context) {
        if (tts != null) {
            Timber.tag(LogTags.TTS).d("init() called twice — ignoring")
            return
        }
        Timber.tag(LogTags.TTS).d("init() — creating TextToSpeech")
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Timber.tag(LogTags.TTS).e("onInit FAIL status=%d", status)
                emitFailed("init_failed:$status")
                return@TextToSpeech
            }
            val chosen = pickFirstAvailableLocale()
            if (chosen == null) {
                Timber.tag(LogTags.TTS).e("Ningún Locale de la cascada disponible")
                emitFailed("no_locale_available")
                return@TextToSpeech
            }
            Timber.tag(LogTags.TTS).i("onInit SUCCESS — locale=%s", chosen)
            tts?.setOnUtteranceProgressListener(progressListener)
            isReady = true
            flushPending()
        }
    }

    private fun pickFirstAvailableLocale(): Locale? {
        val engine = tts ?: return null
        for (locale in LOCALE_CASCADE) {
            val res = engine.setLanguage(locale)
            if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                return locale
            }
            Timber.tag(LogTags.TTS).w("Locale %s no disponible (code=%d)", locale, res)
        }
        return null
    }

    /**
     * Habla un texto. Si TTS no está listo todavía, encola y se reproduce en onInit SUCCESS.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        if (!isReady) {
            Timber.tag(LogTags.TTS).d("speak() pre-init — encolando: %s", text)
            pendingQueue.add(text)
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val id = "beto-utt-${utteranceCounter.incrementAndGet()}"
        val res = tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        if (res == TextToSpeech.SUCCESS) {
            Timber.tag(LogTags.TTS).d("speak ok id=%s text=%s", id, text)
        } else {
            Timber.tag(LogTags.TTS).e("speak fail res=%s text=%s", res, text)
            emitFailed("speak_failed:$res")
        }
    }

    private fun flushPending() {
        while (true) {
            val next = pendingQueue.poll() ?: break
            Timber.tag(LogTags.TTS).d("flushPending -> %s", next)
            speakNow(next)
        }
    }

    /** Frase de boot exacta de D-10 — pronunciada por BetoForegroundService al primer start. */
    fun speakBootGreeting() {
        speak("Hola, soy Beto. Estoy acá para ayudarte.")
    }

    fun shutdown() {
        Timber.tag(LogTags.TTS).i("shutdown")
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) {
            // emit AgentEvent.TtsSpoke con el texto reproducido
            // (no tenemos el texto acá — el id sirve solo de correlación;
            // emitimos un evento neutral por ahora; Phase 2-3 puede mapear id->texto si necesario)
            scope.launch {
                AgentBus.emit(AgentEvent.TtsSpoke("utterance:$utteranceId"))
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            emitFailed("utterance_error:$utteranceId")
        }
        override fun onError(utteranceId: String?, errorCode: Int) {
            emitFailed("utterance_error:$utteranceId:$errorCode")
        }
    }

    private fun emitFailed(reason: String) {
        scope.launch { AgentBus.emit(AgentEvent.TtsFailed(reason)) }
    }
}
```

NOTAS críticas:
- `setOnUtteranceProgressListener` — listener NO null. Sin esto, `onDone` nunca se invoca (es comportamiento documentado de TextToSpeech).
- `QUEUE_ADD` (no QUEUE_FLUSH) — frases consecutivas no se solapan (Pitfall del manifest "Performance Traps" → preferimos QUEUE_FLUSH para barge-in en interacciones futuras, pero en Phase 1 el único caller es la frase de boot, así que ADD es seguro y menos race-prone).
- Cascada de Locale documentada en CONTEXT.md D-10.

**2. `BetoApplication.kt`** (SETUP-05, VOICE-01):

```kotlin
package com.beto.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.beto.app.util.LogTags
import com.beto.app.voice.TtsManager
import timber.log.Timber

class BetoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Timber primero — todo lo demás puede loguear desde acá en adelante
        Timber.plant(Timber.DebugTree())
        Timber.tag(LogTags.TTS).i("BetoApplication.onCreate — Beto starting up")

        // TTS pre-warmed (Pitfall #3) — init temprano, NO en el momento del primer comando
        TtsManager.init(this)

        // Notif channel del FGS (D-15) — declarado acá para que el canal exista antes
        // de que BetoForegroundService.startForeground lo use.
        ensureNotificationChannel(this)
    }

    companion object {
        const val FGS_CHANNEL_ID = "beto_service"

        fun ensureNotificationChannel(ctx: Context) {
            val nm = ctx.getSystemService<NotificationManager>() ?: return
            if (nm.getNotificationChannel(FGS_CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                FGS_CHANNEL_ID,
                ctx.getString(com.beto.app.R.string.fgs_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = ctx.getString(com.beto.app.R.string.fgs_notification_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
```

NOTAS:
- Timber `DebugTree` — solo en debug. Para release habría que filtrar; en hackathon es OK.
- Notif channel se crea acá (no en el FGS) para evitar race en el primer `startForeground`.
- `IMPORTANCE_LOW` — sin sonido ni vibración (D-15: "sin sonido ni vibración").
- `BetoApplication` está declarado en el manifest de Plan 01 con `android:name=".BetoApplication"`.
  </action>
  <verify>
    <automated>
cd /Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12/android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20 | tee /tmp/beto-build-2-2.log; grep -q 'BUILD SUCCESSFUL\|UP-TO-DATE' /tmp/beto-build-2-2.log && grep -q 'Hola, soy Beto. Estoy acá para ayudarte' app/src/main/java/com/beto/app/voice/TtsManager.kt && grep -q 'Timber.plant' app/src/main/java/com/beto/app/BetoApplication.kt && echo "TTS+APP OK"
    </automated>
Smoke test:
1. `./gradlew :app:compileDebugKotlin` compila.
2. La frase de boot existe LITERAL en TtsManager.
3. (Manual con teléfono): instalar APK, abrir desde launcher (la app crashea porque MainActivity todavía no existe — esperado, lo arregla Task 3). Pero el log muestra `BetoApplication.onCreate` y la cascada de Locale del TTS antes del crash.
  </verify>
  <done>
- TtsManager con cascada Locale es-AR → es-419 → es-ES → es → en-US.
- Cola interna pre-init: speak() encolado si !isReady, flush en onInit SUCCESS.
- BetoApplication inicializa Timber + TtsManager + crea notif channel `beto_service`.
- Frase de boot exacta presente en strings.xml Y referenciada literalmente en TtsManager (D-10).
- Cero crashes al abrir el APK (la app puede crashear por falta de MainActivity, pero TtsManager.init() no debe crashear).
  </done>
</task>

<task type="auto">
  <name>Task 3: PreflightCheck + MainActivity (pre-flight + arranque del FGS) + BetoAccessibilityService skeleton</name>
  <files>
    android/app/src/main/java/com/beto/app/util/PreflightCheck.kt,
    android/app/src/main/java/com/beto/app/MainActivity.kt,
    android/app/src/main/java/com/beto/app/service/BetoAccessibilityService.kt
  </files>
  <action>
Implementar el flow completo de boot: launcher → MainActivity → pre-flight → arrancar FGS o redirigir a Settings.

**1. `PreflightCheck.kt`** (DEMO-04, D-06, D-07, D-08):

```kotlin
package com.beto.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.beto.app.service.BetoAccessibilityService
import com.beto.app.voice.TtsManager
import timber.log.Timber

/**
 * Pre-flight check al boot — DEMO-04.
 *
 * Orden definido por D-06:
 *   1. Settings.canDrawOverlays(context)
 *   2. isAccessibilityEnabled (BetoAccessibilityService listada en ENABLED_ACCESSIBILITY_SERVICES)
 *   3. TtsManager.isReady
 *
 * Si falta uno: TtsManager.speak() dice qué falta + abre deep link a Settings (D-07).
 * Si faltan varios: flow secuencial — resolver uno, re-check al volver, resolver siguiente (D-08).
 * NO mostrar lista al usuario (sería confuso para adulto mayor).
 */
data class PreflightResult(
    val overlayOk: Boolean,
    val accessibilityOk: Boolean,
    val ttsOk: Boolean,
) {
    val allOk: Boolean get() = overlayOk && accessibilityOk && ttsOk
    val missing: List<String> get() = buildList {
        if (!overlayOk) add("overlay")
        if (!accessibilityOk) add("accessibility")
        if (!ttsOk) add("tts")
    }
}

object PreflightCheck {

    fun check(context: Context): PreflightResult {
        val overlayOk = Settings.canDrawOverlays(context)
        val accessibilityOk = isAccessibilityServiceEnabled(context)
        val ttsOk = TtsManager.isReady
        Timber.tag(LogTags.TTS).i(
            "Preflight: overlay=%s accessibility=%s tts=%s",
            overlayOk, accessibilityOk, ttsOk
        )
        return PreflightResult(overlayOk, accessibilityOk, ttsOk)
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedId = "${context.packageName}/${BetoAccessibilityService::class.java.name}"
        // Robusto: usar AccessibilityManager.getEnabledAccessibilityServiceList,
        // y ALSO chequear Settings.Secure como fallback (algunos OEMs no listan
        // el service hasta el primer connect).
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledList = am?.getEnabledAccessibilityServiceList(0) ?: emptyList()
        if (enabledList.any { it.id.contains(context.packageName) }) return true

        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return flat.split(":").any { it.equals(expectedId, ignoreCase = true) }
    }

    /** Abre Settings → Display over other apps (overlay permission) — Pitfall #1 D-07 */
    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Abre Settings → Accessibility — DEMO-03 reusa esta función */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
```

**2. `MainActivity.kt`** (D-09, DEMO-04):

```kotlin
package com.beto.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.service.BetoForegroundService
import com.beto.app.util.LogTags
import com.beto.app.util.PreflightCheck
import com.beto.app.util.PreflightResult
import com.beto.app.voice.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Activity minimalista — D-09. Lanza el pre-flight check al primer install:
 *   - Si todo OK → arranca BetoForegroundService y se cierra.
 *   - Si falta algo → TTS lo dice + deep link automático a Settings (D-07).
 *   - Flow secuencial: al volver a foreground (onResume), re-check (D-08).
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(LogTags.TTS).i("MainActivity.onCreate")
        // No setContentView — Activity invisible. Theme NoActionBar declarado en manifest.
    }

    override fun onResume() {
        super.onResume()
        runPreflightAndProceed()
    }

    private fun runPreflightAndProceed() {
        // TTS puede no estar listo aún (init es async). Damos un grace window de 2s
        // antes de declararlo "missing" — sino el primer launch siempre dispara
        // tts_missing aunque la voz sí cargue.
        scope.launch {
            var result = PreflightCheck.check(this@MainActivity)
            var grace = 0
            while (!result.ttsOk && grace < 4) {
                delay(500)
                grace++
                result = PreflightCheck.check(this@MainActivity)
            }
            handlePreflightResult(result)
        }
    }

    private fun handlePreflightResult(result: PreflightResult) {
        Timber.tag(LogTags.TTS).i(
            "Preflight result: overlay=%s a11y=%s tts=%s",
            result.overlayOk, result.accessibilityOk, result.ttsOk
        )
        if (result.allOk) {
            startFgsAndFinish()
            return
        }
        // Emitir evento al bus para tracking (BUS-02).
        scope.launch { AgentBus.emit(AgentEvent.PermissionsMissing(result.missing)) }

        // Resolver el primer faltante en orden D-06: overlay > accessibility > tts.
        when {
            !result.overlayOk -> {
                TtsManager.speak(getString(R.string.tts_overlay_missing))
                PreflightCheck.openOverlaySettings(this)
                // No finish() — al volver a foreground, onResume re-checkea (D-08)
            }
            !result.accessibilityOk -> {
                TtsManager.speak(getString(R.string.tts_accessibility_missing))
                PreflightCheck.openAccessibilitySettings(this)
            }
            !result.ttsOk -> {
                // No podemos decirlo por voz si TTS falló — solo log + (futuro) toast/Compose hint
                // En Phase 1 simplemente fallamos elegantemente (CLAUDE.md: fallback elegante).
                Timber.tag(LogTags.TTS).e("TTS no inicializa — no podemos hablar al usuario")
                // No abrimos Settings — no hay un deep link a "Reinstalar voz TTS"
                // Documentado en checklist físico de Phase 5.
            }
        }
    }

    private fun startFgsAndFinish() {
        Timber.tag(LogTags.TTS).i("Preflight OK — starting BetoForegroundService and finishing")
        val svcIntent = Intent(this, BetoForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svcIntent)
        } else {
            startService(svcIntent)
        }
        finish()
    }
}
```

NOTA — sobre tipo "Activity" vs "AppCompatActivity": usamos `android.app.Activity` para evitar dependencia AppCompat en este plan. La app no tiene UI Compose en Phase 1 (solo Phase 3 para CompanionActivity). Tema declarado en manifest de Plan 01 es `Theme.Material.Light.NoActionBar`.

**3. `BetoAccessibilityService.kt`** (esqueleto — Phase 4 lo expande con loop agéntico):

```kotlin
package com.beto.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Esqueleto del AccessibilityService — Phase 1 solo conecta y emite ServiceStarted.
 * Phase 4 (AGENTIC-01..05) expande con:
 *   - lectura de rootInActiveWindow filtrado a visibles+clickables (max 50 nodos)
 *   - performAction
 *   - hosting del overlay (la burbuja se monta acá cuando el AS está conectado, según D-05)
 *
 * Pitfall #1: log heartbeat para detectar si el sistema deshabilita el AS silenciosamente.
 */
class BetoAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.tag(LogTags.ACCESSIBILITY).i("onServiceConnected — Beto AS connected")
        scope.launch { AgentBus.emit(AgentEvent.ServiceStarted) }
        // Phase 4: registrar overlay (burbuja) acá si AS está conectado (D-05 — TYPE_ACCESSIBILITY_OVERLAY)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Phase 1: solo log para detectar que el AS está vivo (Pitfall #1 heartbeat).
        // Phase 4: aquí se procesan typeWindowStateChanged / typeWindowContentChanged para AgenticBranch.
        event ?: return
        Timber.tag(LogTags.ACCESSIBILITY).v(
            "event type=%s pkg=%s",
            AccessibilityEvent.eventTypeToString(event.eventType),
            event.packageName
        )
    }

    override fun onInterrupt() {
        Timber.tag(LogTags.ACCESSIBILITY).w("onInterrupt — system asked us to pause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(LogTags.ACCESSIBILITY).i("onDestroy — AS being torn down")
        scope.launch { AgentBus.emit(AgentEvent.ServiceStopped) }
    }
}
```
  </action>
  <verify>
    <automated>
cd /Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12/android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -30 | tee /tmp/beto-build-2-3.log; grep -q 'BUILD SUCCESSFUL\|UP-TO-DATE' /tmp/beto-build-2-3.log && grep -v '^#' app/src/main/java/com/beto/app/util/PreflightCheck.kt | grep -c 'Settings.canDrawOverlays' && grep -v '^#' app/src/main/java/com/beto/app/MainActivity.kt | grep -c 'startForegroundService' && grep -v '^#' app/src/main/java/com/beto/app/service/BetoAccessibilityService.kt | grep -c 'AgentBus.emit' && echo "PREFLIGHT+ACTIVITY+AS OK"
    </automated>
Smoke test (manual con teléfono — Plan 03 todavía no existe BetoForegroundService, pero MainActivity intenta arrancarlo):
1. `./gradlew installDebug`
2. Abrir app desde launcher.
3. Si NO se otorgó SYSTEM_ALERT_WINDOW: la app debe (a) decir "Para ayudarte necesito permiso..." por TTS, (b) abrir Settings → Display over other apps. Otorgar manualmente.
4. Volver a la app: re-check, ahora pide accessibility. Otorgar.
5. Tercer return: la app intenta `startForegroundService` para BetoForegroundService — va a CRASHEAR con `ClassNotFoundException` porque Plan 03 todavía no implementó esa clase. **Esto es esperado y se resuelve en Plan 03.**
6. Logcat (filtrar `Beto-`):
   - `BetoApplication.onCreate` → log
   - `TTS init` → log + cascada de Locale
   - `MainActivity.onResume` → preflight check log
   - `Preflight result: overlay=true a11y=true tts=true` (después de otorgar permisos)
  </verify>
  <done>
- PreflightCheck.check() retorna PreflightResult con 3 booleans + lista de missing.
- PreflightCheck tiene deep links para overlay y accessibility (DEMO-03 ya cubierto en Phase 1).
- MainActivity ejecuta preflight en onResume con grace window de 2s para TTS init.
- MainActivity llama TtsManager.speak para guiar al usuario por voz cuando falta un permiso (D-07).
- MainActivity arranca BetoForegroundService y se cierra cuando todo OK (D-09).
- BetoAccessibilityService esqueleto compila, emite ServiceStarted al bus, loguea con tag Beto-Accessibility.
- `./gradlew assembleDebug` retorna BUILD SUCCESSFUL.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| MainActivity → System Settings | Deep links lanzados con `FLAG_ACTIVITY_NEW_TASK` — sistema decide qué Settings abrir |
| AccessibilityService ← Sistema | Sistema invoca callbacks; nuestro código solo lee — no expone superficie de attack |
| TtsManager → Speech engine | Texto local del usuario va al TTS engine on-device — sin riesgo de leak externo |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-05 | I (Information Disclosure) | Timber DebugTree en debug build | accept | Hackathon: solo debug build, teléfono dedicado, no logs persistentes a remote. Phase 5 puede agregar Tree custom que filtre PII si suma valor. |
| T-01-06 | T (Tampering) | AccessibilityService trust | accept | Permisos otorgados manualmente por el dev; AS sólo lee/loguea en Phase 1, no ejecuta acciones. Phase 4 agrega `performAction` con audit trail. |
| T-01-07 | E (EoP) | MainActivity exported=true (LAUNCHER) | mitigate | Activity no acepta extras sensitive; solo lanza pre-flight + FGS. Otras apps que llamen al launcher no obtienen capabilities. |
| T-01-08 | D (Denial of Service) | TtsManager queue overflow | mitigate | `ConcurrentLinkedQueue` no tiene bound, pero solo Phase 1 encola la frase de boot (1 elemento). Phase 2-4 emisores deben ser conscientes; agregar bound si suma. |
| T-01-09 | R (Repudiation) | AgentBus events sin source attribution | accept | In-process, single user, single device — no requerimos audit trail. Phase 5 puede sumar metadata si se publica. |
</threat_model>

<verification>
**Smoke test agregado de Plan 02 (manual con teléfono):**

1. Build + install:
   ```bash
   cd android && ./gradlew installDebug
   ```

2. Abrir app desde launcher por primera vez (sin permisos otorgados):
   - Logcat (`adb logcat -s "Beto-Accessibility" "Beto-LLM" "Beto-Action" "Beto-STT" "Beto-Intent" "Beto-TTS" "Beto-Bus"`):
     - `BetoApplication.onCreate — Beto starting up`
     - `TtsManager init() — creating TextToSpeech`
     - `TtsManager onInit SUCCESS — locale=es_AR` (o el primero disponible de la cascada)
     - `MainActivity.onCreate`
     - `MainActivity.onResume → Preflight: overlay=false a11y=false tts=true`
     - `MainActivity → TTS speak "Para ayudarte necesito permiso..."`
   - TTS audible en el teléfono.
   - Settings → Display over other apps abre automáticamente.

3. Otorgar overlay → volver a la app:
   - Re-check: ahora pide accessibility.
   - TTS dice "Necesito acceso a la accesibilidad..."
   - Settings → Accessibility abre.

4. Otorgar accessibility (toggle Beto):
   - `BetoAccessibilityService.onServiceConnected — Beto AS connected` en log
   - `AgentBus emit -> ServiceStarted`
   - Volver a la app → preflight all OK.

5. **EXPECTED FAILURE:** MainActivity intenta startForegroundService(BetoForegroundService) — la clase NO existe todavía (Plan 03). Crash con `ClassNotFoundException`. Esto valida que el hand-off a Plan 03 está bien definido.

**Critical path validations:**
- [ ] AgentBus singleton compila y los flows se pueden subscribir
- [ ] AgentEvent sealed class tiene las 8 variantes Phase 1, sin variantes Phase 2-4
- [ ] TtsManager.init() se invoca en BetoApplication.onCreate ANTES del primer speak (Pitfall #3)
- [ ] Cola pre-init funciona: speakBootGreeting() llamado pre-init se reproduce post-init
- [ ] PreflightCheck respeta orden D-06: overlay → accessibility → tts
- [ ] Deep links a Settings funcionan: overlay y accessibility (D-07)
- [ ] Logging usa exactamente los 7 tags Beto-XXX de LogTags
- [ ] BetoAccessibilityService emite ServiceStarted al conectar
- [ ] Frase de boot LITERAL: "Hola, soy Beto. Estoy acá para ayudarte." (D-10)
</verification>

<success_criteria>
1. `./gradlew assembleDebug` retorna BUILD SUCCESSFUL.
2. AgentBus + AgentEvent + AgentCommand contratos congelados — sin variantes Phase 2-4 mezcladas.
3. TtsManager con cascada de Locale es-AR → es-419 → es-ES → es → en-US y cola pre-init.
4. BetoApplication inicializa Timber + TtsManager + crea notif channel.
5. MainActivity ejecuta pre-flight (DEMO-04) y guía al usuario por voz a Settings cuando falta un permiso (D-07).
6. BetoAccessibilityService esqueleto emite ServiceStarted al conectar.
7. ToolDescriptors mergea con stubs (sin cuerpos ejecutables — solo IDs + ALL_TOOL_NAMES set).
8. Logcat filtra por tags Beto-XXX correctamente.
9. **MainActivity hand-off a BetoForegroundService preparado** — Plan 03 lo conecta, no se cambia más esta superficie.
</success_criteria>

<output>
After completion, create `.planning/phases/01-foundation-sync-de-hora-0/01-02-SUMMARY.md` with:
- Cualquier desviación de los contratos de las interfaces (hubo que renombrar algo, ajustar buffer sizes, etc.).
- Confirmación de que el TTS cargó es-AR en el teléfono de demo (o cuál Locale de la cascada eligió).
- Logs de boot exactos de un cold start fresco.
- Notas para Plan 03: qué variantes de AgentEvent usar (BootCompleted al startForeground OK, BubbleTapped/BubbleLongPressed desde el OverlayBubble), cómo invocar TtsManager.speakBootGreeting().
</output>
