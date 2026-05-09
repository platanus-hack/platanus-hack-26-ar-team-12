---
phase: 01-foundation-sync-de-hora-0
plan: 03
type: execute
wave: 3
depends_on: [01, 02]
files_modified:
  - android/app/src/main/java/com/beto/app/service/BetoForegroundService.kt
  - android/app/src/main/java/com/beto/app/overlay/OverlayManager.kt
  - android/app/src/main/java/com/beto/app/overlay/OverlayBubble.kt
  - android/app/src/main/res/drawable/bubble_background.xml
  - android/app/src/main/res/layout/overlay_bubble.xml
autonomous: true
requirements: [OVERLAY-01, OVERLAY-02]
must_haves:
  truths:
    - "Tras instalar el APK y otorgar overlay+accessibility, MainActivity arranca BetoForegroundService que persiste con notif `Beto está acá`."
    - "BetoForegroundService al startForeground exitoso: TtsManager.speakBootGreeting() — el teléfono dice 'Hola, soy Beto. Estoy acá para ayudarte.' sin race condition."
    - "OverlayManager monta una burbuja flotante usando TYPE_ACCESSIBILITY_OVERLAY si BetoAccessibilityService está conectado, fallback a TYPE_APPLICATION_OVERLAY antes."
    - "La burbuja muestra project_logo dentro de un círculo con un ring placeholder gris (D-04 — Phase 4 implementa los 5 estados visuales completos)."
    - "La burbuja se puede arrastrar con el dedo y al soltar, hace 'magnet' al borde más cercano (izquierdo o derecho)."
    - "La burbuja aparece sobre WhatsApp, Maps, y la home screen (verificar manualmente con teléfono — Pitfall #10 conocido sobre apps con FLAG_SECURE)."
    - "Tap corto en la burbuja emite AgentEvent.BubbleTapped (Phase 2 lo conecta a captura de voz). Long-press emite BubbleLongPressed (Phase 3 al Modo Compañero)."
    - "Smoke test final: emitir AgentEvent.BootCompleted desde BetoForegroundService y suscribir collector temporal en MainActivity (vía Logcat) confirma que el bus funciona end-to-end."
  artifacts:
    - path: "android/app/src/main/java/com/beto/app/service/BetoForegroundService.kt"
      provides: "Service con startForeground(channel beto_service, type microphone), bus collector que reacciona a comandos, dispara speakBootGreeting() al primer start, emite BootCompleted"
      contains: "startForeground"
      min_lines: 60
    - path: "android/app/src/main/java/com/beto/app/overlay/OverlayManager.kt"
      provides: "Singleton que añade/remueve la burbuja del WindowManager con tipo correcto según AS conectado"
      contains: "TYPE_ACCESSIBILITY_OVERLAY"
      min_lines: 40
    - path: "android/app/src/main/java/com/beto/app/overlay/OverlayBubble.kt"
      provides: "Custom View con drag + magnet a borde + tap/long-press detection que emite eventos al bus"
      contains: "OnTouchListener"
      min_lines: 80
    - path: "android/app/src/main/res/layout/overlay_bubble.xml"
      provides: "Layout de la burbuja: ImageView circular con project_logo + ring placeholder"
    - path: "android/app/src/main/res/drawable/bubble_background.xml"
      provides: "Drawable circular gris (placeholder del ring) que envuelve al logo"
  key_links:
    - from: "android/app/src/main/java/com/beto/app/service/BetoForegroundService.kt"
      to: "TtsManager.speakBootGreeting()"
      via: "invocación en onStartCommand después del startForeground exitoso"
      pattern: "speakBootGreeting"
    - from: "android/app/src/main/java/com/beto/app/service/BetoForegroundService.kt"
      to: "OverlayManager"
      via: "OverlayManager.show(context) en onStartCommand"
      pattern: "OverlayManager.show"
    - from: "android/app/src/main/java/com/beto/app/overlay/OverlayBubble.kt"
      to: "AgentBus"
      via: "AgentBus.emit(AgentEvent.BubbleTapped) y BubbleLongPressed en OnTouchListener"
      pattern: "AgentEvent.Bubble"
    - from: "android/app/src/main/java/com/beto/app/overlay/OverlayManager.kt"
      to: "WindowManager"
      via: "windowManager.addView(bubble, params)"
      pattern: "windowManager.addView"
---

<objective>
Cerrar el Walking Skeleton de Phase 1: el Foreground Service que mantiene a Beto vivo, la burbuja flotante visible sobre cualquier app (entry point único — el usuario no toca nada más en la app, solo la burbuja), y el wiring del TTS de bienvenida que prueba que todo el pipeline funciona end-to-end.

Purpose: Tras este plan, alguien puede instalar el APK, otorgar permisos, y ver/escuchar a Beto funcionando. Es la **demo mínima** de Phase 1 que cumple los 4 success criteria del roadmap. Sin este plan, Plan 01 + 02 son infrastructure invisible.

Output:
- BetoForegroundService con startForeground correcto (Pitfall #11), notif persistente, dispara speakBootGreeting al boot.
- OverlayManager con TYPE_ACCESSIBILITY_OVERLAY (preferido) → TYPE_APPLICATION_OVERLAY fallback (Pitfall #10).
- OverlayBubble: drag + magnet a borde, tap → bus, long-press → bus.
- Layout y drawables de la burbuja con project_logo + ring placeholder gris.

User decisions implementadas (CONTEXT.md):
- D-03: project_logo dentro de círculo, cero assets nuevos
- D-04: 5 estados visuales se comunican con color del ring (Phase 1 solo placeholder gris)
- D-05: Burbuja con WindowManager + Views clásicas, NO Compose. 64dp diámetro. Posición inicial borde derecho mitad vertical.
- D-15: Notif del FGS — título "Beto está acá", texto "Tocame en la burbuja para hablar", canal `beto_service` (creado en Plan 02 BetoApplication)

Pitfalls mitigados:
- #3 (TTS race): speakBootGreeting() invocado solo después de startForeground exitoso, y TtsManager ya tiene cola pre-init de Plan 02
- #8 (FGS killed): startForeground con type microphone declarado (Plan 01) + IGNORE_BATTERY_OPTIMIZATIONS solicitado en checklist físico Phase 5
- #10 (overlay sobre apps con FLAG_SECURE): cambiar TYPE según AS conectado para maximizar trust
- #11 (FGS sin foregroundServiceType): runtime startForeground(id, notif, type) explícito
</objective>

<execution_context>
@/Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12/.claude/get-shit-done/workflows/execute-plan.md
@/Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/phases/01-foundation-sync-de-hora-0/01-CONTEXT.md
@.planning/phases/01-foundation-sync-de-hora-0/01-01-SUMMARY.md
@.planning/phases/01-foundation-sync-de-hora-0/01-02-SUMMARY.md
@.planning/research/ARCHITECTURE.md
@.planning/research/PITFALLS.md
@CLAUDE.md
@android/app/src/main/AndroidManifest.xml
@android/app/src/main/java/com/beto/app/BetoApplication.kt
@android/app/src/main/java/com/beto/app/MainActivity.kt
@android/app/src/main/java/com/beto/app/voice/TtsManager.kt
@android/app/src/main/java/com/beto/app/bus/AgentBus.kt
@android/app/src/main/java/com/beto/app/bus/AgentEvents.kt
@android/app/src/main/java/com/beto/app/util/LogTags.kt
@android/app/src/main/java/com/beto/app/service/BetoAccessibilityService.kt

<interfaces>
<!-- APIs del sistema y contratos internos que este plan consume -->

**Desde Plan 02 (ya mergeado):**
- `TtsManager.init(context)` — invocado en BetoApplication.onCreate
- `TtsManager.speakBootGreeting()` — habla "Hola, soy Beto. Estoy acá para ayudarte." (D-10)
- `TtsManager.speak(text)` — encola si !isReady, flush en onInit SUCCESS
- `TtsManager.isReady` — Bool
- `AgentBus.emit(event)` — suspend, in-process pub/sub
- `AgentEvent.BootCompleted`, `AgentEvent.BubbleTapped`, `AgentEvent.BubbleLongPressed`, `AgentEvent.ServiceStarted/Stopped`
- `AgentCommand.Speak(text)` — para que otros componentes pidan TTS sin acoplarse al singleton (Phase 2+ lo va a usar; en Plan 03 podemos suscribir un collector que reacciona a Speak commands)
- `BetoApplication.FGS_CHANNEL_ID = "beto_service"` — canal ya creado
- `LogTags.ACCESSIBILITY`, `LogTags.TTS`, `LogTags.BUS`, etc.
- `BetoAccessibilityService` — clase con onServiceConnected emitiendo ServiceStarted al bus

**APIs del sistema clave:**

`WindowManager.LayoutParams` constants:
- `TYPE_ACCESSIBILITY_OVERLAY` (preferido — solo si AS conectado, ARCHITECTURE.md "Component Responsibilities")
- `TYPE_APPLICATION_OVERLAY` (fallback antes que el AS esté conectado)
- Flags: `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_NOT_TOUCH_MODAL`

`Service` API:
- `startForeground(id: Int, notification: Notification, foregroundServiceType: Int)` — la firma de 3 args es OBLIGATORIA en API 29+ cuando el manifest tiene `foregroundServiceType` (Pitfall #11)
- Constante: `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE`

`Notification.Builder` API:
- `setOngoing(true)` para que no sea swipeable
- `setSmallIcon(R.drawable.ic_beto_notification)` (vector creado en Plan 01)
- `setContentTitle(getString(R.string.fgs_notification_title))` → "Beto está acá"
- `setContentText(getString(R.string.fgs_notification_text))` → "Tocame en la burbuja para hablar"
- `setContentIntent(pendingIntentToMainActivity)` per D-15

**Resources usados (creados en Plan 01):**
- `R.drawable.project_logo` — PNG del logo
- `R.drawable.ic_beto_notification` — vector small icon
- `R.string.fgs_notification_title` — "Beto está acá"
- `R.string.fgs_notification_text` — "Tocame en la burbuja para hablar"
- `R.string.fgs_notification_channel_name`, `R.string.fgs_notification_channel_description`

**Constantes que este plan define:**
- `BetoForegroundService.NOTIFICATION_ID = 1001`
- `OverlayManager.BUBBLE_DIAMETER_DP = 64` (D-05)
- `OverlayBubble.DRAG_THRESHOLD_PX = 16` (separa tap de drag)
- `OverlayBubble.LONG_PRESS_MS = 600` (long-press detection)
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: BetoForegroundService — startForeground correcto + speakBootGreeting + emite BootCompleted al bus</name>
  <files>
    android/app/src/main/java/com/beto/app/service/BetoForegroundService.kt
  </files>
  <action>
Implementar el FGS que mantiene a Beto vivo. NO incluir lógica de captura de voz / LLM / loop agéntico (esos son Phases 2-4). Solo: notif persistente + dispara TTS de bienvenida + emite BootCompleted + monta el overlay vía OverlayManager (creado en Task 2).

```kotlin
package com.beto.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.beto.app.BetoApplication
import com.beto.app.MainActivity
import com.beto.app.R
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentCommand
import com.beto.app.bus.AgentEvent
import com.beto.app.overlay.OverlayManager
import com.beto.app.util.LogTags
import com.beto.app.voice.TtsManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Foreground Service principal — dueño del proceso de Beto. Phases siguientes le agregan
 * loop agéntico (Phase 4) y orquestación del LLM (Phase 3). En Phase 1 solo:
 *   - startForeground con type=microphone (Pitfall #11)
 *   - dispara TtsManager.speakBootGreeting() al primer start
 *   - emite AgentEvent.BootCompleted al bus
 *   - monta el overlay (burbuja) vía OverlayManager
 *   - suscribe AgentCommand.Speak para reactionary TTS
 */
class BetoForegroundService : LifecycleService() {

    private var bootGreetingPlayed = false

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Timber.tag(LogTags.TTS).i("BetoForegroundService.onCreate")
        BetoApplication.ensureNotificationChannel(this)
        startForegroundCorrectly()

        // Subscriber a AgentCommand.Speak — ahora cualquier componente puede pedir TTS
        // sin acoplarse al singleton TtsManager directamente.
        lifecycleScope.launch {
            AgentBus.commands.collect { cmd ->
                if (cmd is AgentCommand.Speak) {
                    Timber.tag(LogTags.TTS).d("Cmd Speak -> %s", cmd.text)
                    TtsManager.speak(cmd.text)
                }
            }
        }

        lifecycleScope.launch { AgentBus.emit(AgentEvent.ServiceStarted) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Timber.tag(LogTags.TTS).i("onStartCommand startId=%d", startId)

        // Montar la burbuja flotante (OVERLAY-01, OVERLAY-02).
        OverlayManager.show(this)

        // Frase de boot — solo la primera vez que arranca el FGS.
        if (!bootGreetingPlayed) {
            bootGreetingPlayed = true
            Timber.tag(LogTags.TTS).i("Disparando speakBootGreeting (D-10)")
            TtsManager.speakBootGreeting()
            lifecycleScope.launch { AgentBus.emit(AgentEvent.BootCompleted) }
        }

        // START_STICKY — si el sistema mata el proceso, lo recrea con intent=null.
        // Pitfall #8: combinado con FGS + battery exemption (checklist físico Phase 5),
        // suficiente para 30+ min screen lock.
        return START_STICKY
    }

    private fun startForegroundCorrectly() {
        val notif = buildNotification()
        // API 29+ con foregroundServiceType en manifest: firma de 3 args es OBLIGATORIA.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            // minSdk = 31, este branch nunca se ejecuta — pero compilador no lo sabe.
            startForeground(NOTIFICATION_ID, notif)
        }
        Timber.tag(LogTags.TTS).i("startForeground OK type=microphone")
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, tapIntent, flags)

        return NotificationCompat.Builder(this, BetoApplication.FGS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_beto_notification)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(getString(R.string.fgs_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    override fun onDestroy() {
        Timber.tag(LogTags.TTS).i("BetoForegroundService.onDestroy")
        OverlayManager.hide(this)
        lifecycleScope.launch { AgentBus.emit(AgentEvent.ServiceStopped) }
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001

        fun startIntent(context: Context): Intent =
            Intent(context, BetoForegroundService::class.java)
    }
}
```

NOTAS:
- `LifecycleService` (de `androidx.lifecycle.lifecycle-service` — declarado en libs.versions.toml de Plan 01) — provee `lifecycleScope` que cancela las coroutines en onDestroy automáticamente.
- `NotificationCompat` — viene transitivo de `androidx.core.core-ktx` ya declarado.
- `START_STICKY` — pertinente porque queremos que el sistema recree el FGS si lo mata (Pitfall #8).
- `bootGreetingPlayed` flag evita que cada `onStartCommand` (puede dispararse múltiples veces si MainActivity vuelve a llamar) repita la frase de boot.
- `OverlayManager.show/hide` — implementados en Task 2.

**IMPORTANTE — solapamiento con Plan 01:**
- Plan 01 declaró el manifest `<service ...foregroundServiceType="microphone"/>`.
- Plan 02 BetoApplication.ensureNotificationChannel creó el channel `beto_service`.
- Acá nos apoyamos en ambos. Si alguno está mal, este plan crashea.
  </action>
  <verify>
    <automated>
cd /Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12/android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20 | tee /tmp/beto-build-3-1.log; grep -q 'BUILD SUCCESSFUL\|UP-TO-DATE' /tmp/beto-build-3-1.log && grep -v '^#' app/src/main/java/com/beto/app/service/BetoForegroundService.kt | grep -c 'startForeground' && grep -v '^#' app/src/main/java/com/beto/app/service/BetoForegroundService.kt | grep -c 'speakBootGreeting' && echo "FGS OK"
    </automated>
Smoke test (manual con teléfono — necesita Task 2 + 3 implementados para evitar crash en `OverlayManager.show`):
1. Compila sin errores.
2. (Después de Task 2 + 3): `./gradlew installDebug && adb shell am start -n com.beto.app/.MainActivity`
3. Logcat:
   - `BetoForegroundService.onCreate`
   - `startForeground OK type=microphone`
   - `Disparando speakBootGreeting (D-10)`
   - `TtsManager speak ok ...`
   - `AgentBus emit -> BootCompleted`
4. Notificación "Beto está acá / Tocame en la burbuja para hablar" visible en status bar.
5. **Voz**: el teléfono dice "Hola, soy Beto. Estoy acá para ayudarte." en español.
  </verify>
  <done>
- BetoForegroundService compila.
- startForeground usa la firma de 3 args con `FOREGROUND_SERVICE_TYPE_MICROPHONE` (Pitfall #11).
- Notificación correctamente construida con título/texto/PendingIntent a MainActivity (D-15).
- speakBootGreeting() invocado UNA VEZ al primer start (flag bootGreetingPlayed).
- Suscriptor de AgentCommand.Speak en lifecycleScope.
- AgentEvent.BootCompleted emitido al bus tras el speak inicial.
- onDestroy emite ServiceStopped y llama OverlayManager.hide.
  </done>
</task>

<task type="auto">
  <name>Task 2: OverlayManager — adds/removes la burbuja al WindowManager con tipo correcto (TYPE_ACCESSIBILITY_OVERLAY preferido)</name>
  <files>
    android/app/src/main/java/com/beto/app/overlay/OverlayManager.kt,
    android/app/src/main/res/layout/overlay_bubble.xml,
    android/app/src/main/res/drawable/bubble_background.xml
  </files>
  <action>
Implementar el manager que decide qué tipo de window usar y maneja el lifecycle del overlay.

**1. `bubble_background.xml`** (D-04 — ring placeholder gris para Phase 1):
```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Ring placeholder gris (Phase 4 lo cambia por color de estado) -->
    <item>
        <shape android:shape="oval">
            <solid android:color="#888888" />
        </shape>
    </item>
    <!-- Inner blanco para que el logo respire -->
    <item
        android:bottom="4dp"
        android:left="4dp"
        android:right="4dp"
        android:top="4dp">
        <shape android:shape="oval">
            <solid android:color="#FFFFFF" />
        </shape>
    </item>
</layer-list>
```

**2. `overlay_bubble.xml`** (D-03, D-05 — 64dp, project_logo dentro del ring):
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/bubble_root"
    android:layout_width="64dp"
    android:layout_height="64dp"
    android:background="@drawable/bubble_background"
    android:padding="8dp"
    android:elevation="8dp">

    <ImageView
        android:id="@+id/bubble_logo"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_gravity="center"
        android:src="@drawable/project_logo"
        android:scaleType="fitCenter"
        android:contentDescription="@string/app_name" />

</FrameLayout>
```

**3. `OverlayManager.kt`** (OVERLAY-01, OVERLAY-02, D-05):
```kotlin
package com.beto.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import com.beto.app.R
import com.beto.app.service.BetoAccessibilityService
import com.beto.app.util.LogTags
import timber.log.Timber

/**
 * OverlayManager singleton — gestiona el ciclo de vida de la burbuja flotante.
 *
 * Tipo de window (D-05, OVERLAY-02):
 *   - TYPE_ACCESSIBILITY_OVERLAY si BetoAccessibilityService está conectado (trusted, no aparece banner del sistema)
 *   - TYPE_APPLICATION_OVERLAY como fallback (requiere SYSTEM_ALERT_WINDOW)
 *
 * Posición inicial (D-05): borde derecho, mitad vertical de la pantalla.
 * Tamaño: 64dp (ver overlay_bubble.xml).
 *
 * Pitfall #10: en apps con FLAG_SECURE (bancarias) la burbuja se oculta — fuera del scope
 * del demo (PROJECT.md ya excluye apps bancarias).
 */
object OverlayManager {

    private var bubbleView: View? = null

    fun show(context: Context) {
        if (bubbleView != null) {
            Timber.tag(LogTags.ACCESSIBILITY).d("OverlayManager.show — already visible")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Timber.tag(LogTags.ACCESSIBILITY).w("show() pero canDrawOverlays=false — abort")
            return
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            Timber.tag(LogTags.ACCESSIBILITY).e("WindowManager no disponible")
            return
        }

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_bubble, null, false)

        // Configurar OnTouchListener (drag + magnet + tap/long-press) — viene de OverlayBubble.attach.
        OverlayBubble.attach(view, wm, ::computeInitialParams)

        val params = computeInitialParams(context, wm)
        try {
            wm.addView(view, params)
            bubbleView = view
            Timber.tag(LogTags.ACCESSIBILITY).i(
                "Bubble shown — type=%s pos=(%d,%d)",
                windowTypeName(params.type), params.x, params.y
            )
        } catch (e: Exception) {
            Timber.tag(LogTags.ACCESSIBILITY).e(e, "addView falló")
            bubbleView = null
        }
    }

    fun hide(context: Context) {
        val view = bubbleView ?: return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        try {
            wm.removeView(view)
            Timber.tag(LogTags.ACCESSIBILITY).i("Bubble removed")
        } catch (e: Exception) {
            Timber.tag(LogTags.ACCESSIBILITY).w(e, "removeView falló — ya removido?")
        }
        bubbleView = null
    }

    private fun computeInitialParams(context: Context, wm: WindowManager): WindowManager.LayoutParams {
        val type = pickOverlayType(context)
        val flags = (
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        // Posición inicial D-05: borde derecho, mitad vertical
        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(it)
        }
        val sizePx = (64 * metrics.density).toInt()
        params.x = metrics.widthPixels - sizePx - (8 * metrics.density).toInt()
        params.y = (metrics.heightPixels / 2) - (sizePx / 2)
        return params
    }

    private fun pickOverlayType(context: Context): Int {
        val asConnected = isAccessibilityServiceConnected(context)
        val type = if (asConnected) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        Timber.tag(LogTags.ACCESSIBILITY).d(
            "pickOverlayType — asConnected=%s -> %s",
            asConnected, windowTypeName(type)
        )
        return type
    }

    private fun isAccessibilityServiceConnected(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        return am.getEnabledAccessibilityServiceList(0).orEmpty()
            .any { it.id.contains(context.packageName) }
    }

    private fun windowTypeName(type: Int): String = when (type) {
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY -> "APPLICATION_OVERLAY"
        else -> "type_$type"
    }
}
```

NOTAS:
- TYPE_ACCESSIBILITY_OVERLAY requiere que el AS esté CONECTADO (no solo habilitado en Settings) — la verificación con `getEnabledAccessibilityServiceList` es razonable proxy. Si el AS aún no llamó `onServiceConnected`, caemos a TYPE_APPLICATION_OVERLAY temporalmente.
- TODO Phase 4: cuando el AS se conecte (después de la primera burbuja), idealmente se podría re-attach con el tipo trusted. Phase 1 lo deja simple — la burbuja queda con el tipo elegido al primer show. Si el dev quiere refrescar, cierra y abre de nuevo (o killing app + reopen).
- `computeInitialParams` se pasa como callback a `OverlayBubble.attach` para que el touch listener pueda re-aplicar params al hacer drag.
  </action>
  <verify>
    <automated>
cd /Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12/android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20 | tee /tmp/beto-build-3-2.log; grep -q 'BUILD SUCCESSFUL\|UP-TO-DATE' /tmp/beto-build-3-2.log && grep -v '^#' app/src/main/java/com/beto/app/overlay/OverlayManager.kt | grep -c 'TYPE_ACCESSIBILITY_OVERLAY' && grep -v '^#' app/src/main/java/com/beto/app/overlay/OverlayManager.kt | grep -c 'TYPE_APPLICATION_OVERLAY' && test -f app/src/main/res/layout/overlay_bubble.xml && test -f app/src/main/res/drawable/bubble_background.xml && echo "OVERLAY MANAGER OK"
    </automated>
Smoke test (necesita Task 3 para tener OnTouch funcionando):
1. Compila.
2. (Después de Task 3): instalar y abrir app → la burbuja aparece en el borde derecho mitad vertical.
3. Logcat: `Bubble shown — type=ACCESSIBILITY_OVERLAY pos=(...)`. Si dice APPLICATION_OVERLAY es porque el AS no estaba conectado al moment del show.
  </verify>
  <done>
- OverlayManager compila.
- show() agrega el view al WindowManager con el tipo correcto según AS connected.
- hide() limpia state.
- pickOverlayType decide TYPE_ACCESSIBILITY_OVERLAY si AS conectado, fallback a TYPE_APPLICATION_OVERLAY (OVERLAY-02).
- Posición inicial = borde derecho, mitad vertical (D-05).
- Tamaño = 64dp x 64dp (D-05).
- overlay_bubble.xml + bubble_background.xml existen.
  </done>
</task>

<task type="auto">
  <name>Task 3: OverlayBubble — drag + magnet a borde + tap/long-press emisión al bus</name>
  <files>
    android/app/src/main/java/com/beto/app/overlay/OverlayBubble.kt
  </files>
  <action>
Implementar el touch handling de la burbuja: drag, magnet a borde más cercano, tap, long-press.

```kotlin
package com.beto.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Touch handling de la burbuja — implementado con OnTouchListener clásico (NO Compose,
 * D-05). Detecta:
 *   - Tap corto (<DRAG_THRESHOLD movement, <LONG_PRESS_MS duration) -> AgentEvent.BubbleTapped
 *   - Long-press (sin mover, >LONG_PRESS_MS) -> AgentEvent.BubbleLongPressed
 *   - Drag (>DRAG_THRESHOLD movement) -> reposiciona; al soltar magnet al borde más cercano
 *
 * NO maneja persistencia de posición entre boots (Deferred Item — nice-to-have Phase 4).
 */
object OverlayBubble {

    private const val DRAG_THRESHOLD_DP = 8f
    private const val LONG_PRESS_MS = 600L
    private const val MAGNET_DURATION_MS = 180L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Adjunta el touch listener a `view`. `paramsBuilder` se usa solo para obtener las
     * dimensiones de pantalla iniciales — el view ya fue agregado por OverlayManager con
     * params propios. Aquí actualizamos params en cada drag.
     */
    fun attach(
        view: View,
        wm: WindowManager,
        paramsBuilder: (Context, WindowManager) -> WindowManager.LayoutParams,
    ) {
        val context = view.context
        val density = context.resources.displayMetrics.density
        val dragThresholdPx = DRAG_THRESHOLD_DP * density

        val handler = Handler(Looper.getMainLooper())

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragStarted = false
        var longPressFired = false

        val longPressRunnable = Runnable {
            if (!dragStarted) {
                longPressFired = true
                Timber.tag(LogTags.ACCESSIBILITY).i("Bubble long-pressed")
                scope.launch { AgentBus.emit(AgentEvent.BubbleLongPressed) }
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }

        view.setOnTouchListener { v, event ->
            val params = v.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragStarted = false
                    longPressFired = false
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!dragStarted && hypot(dx, dy) > dragThresholdPx) {
                        dragStarted = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (dragStarted) {
                        params.x = (initialX + dx).toInt()
                        params.y = (initialY + dy).toInt()
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (dragStarted) {
                        magnetToEdge(v, wm, params)
                    } else if (!longPressFired) {
                        // Tap simple
                        Timber.tag(LogTags.ACCESSIBILITY).i("Bubble tapped")
                        scope.launch { AgentBus.emit(AgentEvent.BubbleTapped) }
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Magnet a borde más cercano (izquierdo o derecho) con animación corta.
     * D-05: magnet a borde.
     */
    private fun magnetToEdge(
        view: View,
        wm: WindowManager,
        params: WindowManager.LayoutParams,
    ) {
        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(it)
        }
        val viewWidth = view.width.takeIf { it > 0 } ?: (64 * metrics.density).toInt()
        val centerX = params.x + viewWidth / 2
        val targetX = if (centerX < metrics.widthPixels / 2) {
            (8 * metrics.density).toInt()  // borde izquierdo con padding 8dp
        } else {
            metrics.widthPixels - viewWidth - (8 * metrics.density).toInt()  // borde derecho
        }
        // Clamp Y dentro de la pantalla (no permitir que la burbuja salga por arriba/abajo)
        val statusBarPadding = (24 * metrics.density).toInt()
        val maxY = metrics.heightPixels - view.height - statusBarPadding
        val clampedY = params.y.coerceIn(statusBarPadding, maxY)

        val startX = params.x
        val animator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = MAGNET_DURATION_MS
            addUpdateListener { va ->
                params.x = va.animatedValue as Int
                params.y = clampedY
                runCatching { wm.updateViewLayout(view, params) }
            }
        }
        animator.start()
        Timber.tag(LogTags.ACCESSIBILITY).d(
            "magnet from x=%d to x=%d (clampedY=%d)", startX, targetX, clampedY
        )
    }
}
```

NOTAS:
- `view.performClick()` es necesario para que Lint no se queje de accessibility (a11y best practice — el OnTouchListener debería invocar performClick).
- `performHapticFeedback` da feedback táctil al long-press — UX para adultos mayores.
- DRAG_THRESHOLD_DP = 8dp evita que un tap con leve movimiento se interprete como drag.
- `magnetToEdge` con animación de 180ms es suave pero rápida — adultos mayores no quieren latencia perceptible.
- Posición persistente entre boots = Deferred Item (CONTEXT.md). Phase 1 no lo implementa.
  </action>
  <verify>
    <automated>
cd /Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12/android && ./gradlew assembleDebug 2>&1 | tail -20 | tee /tmp/beto-build-3-3.log; grep -q 'BUILD SUCCESSFUL' /tmp/beto-build-3-3.log && grep -v '^#' app/src/main/java/com/beto/app/overlay/OverlayBubble.kt | grep -c 'AgentEvent.BubbleTapped' && grep -v '^#' app/src/main/java/com/beto/app/overlay/OverlayBubble.kt | grep -c 'AgentEvent.BubbleLongPressed' && grep -v '^#' app/src/main/java/com/beto/app/overlay/OverlayBubble.kt | grep -c 'magnetToEdge' && echo "BUBBLE OK"
    </automated>
**Smoke test FINAL DE FASE 1 (manual con teléfono — el más importante):**

1. Build + install:
   ```bash
   cd android && ./gradlew installDebug
   ```

2. Abrir Beto desde launcher. Otorgar permisos siguiendo el flow (TTS guiando):
   - Display over other apps → ON
   - Accessibility → toggle Beto ON

3. **Smoke tests obligatorios para cerrar Phase 1:**
   - [ ] La voz dice "Hola, soy Beto. Estoy acá para ayudarte." en español sin trabarse.
   - [ ] La notificación "Beto está acá" aparece en status bar y NO se puede swipe-dismiss.
   - [ ] La burbuja aparece en el borde derecho de la pantalla.
   - [ ] Abrir WhatsApp → la burbuja sigue visible encima.
   - [ ] Abrir Maps → la burbuja sigue visible encima.
   - [ ] Volver a la home screen → la burbuja sigue visible.
   - [ ] Arrastrar la burbuja al medio de la pantalla y soltar → animación corta y se pega al borde más cercano.
   - [ ] Tap corto en la burbuja → log `Bubble tapped` + log `AgentBus emit -> BubbleTapped`.
   - [ ] Long-press 600ms+ en la burbuja → vibración + log `Bubble long-pressed` + log `AgentBus emit -> BubbleLongPressed`.

4. **Test de pre-flight con permiso revocado:**
   - Settings → Accessibility → toggle Beto OFF
   - Re-abrir Beto desde launcher → la voz dice "Necesito acceso a la accesibilidad..." + Settings se abre.

5. **Test de bus end-to-end (verificación SC#4 del roadmap):**
   - Logcat filtrado: `adb logcat -s "Beto-Bus:D"`
   - Tap en burbuja debe mostrar:
     ```
     Beto-Bus: emit -> BubbleTapped
     ```
   - Tap largo:
     ```
     Beto-Bus: emit -> BubbleLongPressed
     ```
   - Esto confirma que cualquier dev en Phase 2-4 puede emitir eventos al bus y otros componentes los reciben.
  </verify>
  <done>
- OverlayBubble compila.
- Tap corto emite AgentEvent.BubbleTapped.
- Long-press (>600ms sin drag) emite AgentEvent.BubbleLongPressed con haptic feedback.
- Drag actualiza posición vía updateViewLayout.
- Al soltar drag: animación a borde más cercano (180ms).
- Y se clampea dentro de la pantalla (no se sale por arriba/abajo).
- **Smoke test final manual passes** (los 4 success criteria de Phase 1 del roadmap se cumplen):
  1. ✓ Burbuja aparece sobre WhatsApp + Maps + home, draggable con magnet a borde
  2. ✓ Beto dice "Hola, soy Beto. Estoy acá para ayudarte." al primer boot
  3. ✓ Si falta un permiso, Beto avisa por voz qué falta
  4. ✓ Bus end-to-end: BubbleTapped emitido + recibible por collectors
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Overlay → otras apps | La burbuja se renderiza encima de cualquier app (excepto FLAG_SECURE) — no captura input de la app debajo |
| FGS → sistema | El FGS con type microphone reserva el mic, pero Phase 1 no lo usa (Phase 2 sí) |
| OnTouchListener bubble | Solo procesa eventos de su propia view — no inyecta input |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-10 | T (Tampering — UI redress / clickjacking) | Overlay sobre otras apps | accept | Hackathon: teléfono dedicado, no se publica. Pitfall #10 documenta que apps con FLAG_SECURE ocultan el overlay automáticamente — protección del sistema vigente. |
| T-01-11 | I (Information Disclosure — overlay screenshots) | Burbuja podría capturar screenshot del background app | accept | Burbuja NO accede a contenido de otras apps (eso requiere AccessibilityService API que Phase 1 no usa para reading). Phase 4 cuando agregue lectura de tree, agrega audit con tag Beto-Accessibility. |
| T-01-12 | D (DoS — FGS death) | OEMs agresivos matando FGS | mitigate | foregroundServiceType=microphone declarado (Pitfall #11). Battery exemption en checklist físico Phase 5 (Pitfall #8). START_STICKY para auto-restart. |
| T-01-13 | E (EoP) | TYPE_ACCESSIBILITY_OVERLAY trust | mitigate | Solo se usa si AS está conectado (lo cual requiere que el usuario haya otorgado el permiso explícitamente en Settings) — fallback transparente a TYPE_APPLICATION_OVERLAY no eleva privilegios. |
</threat_model>

<verification>
**Phase 1 Final Verification — los 4 success criteria del ROADMAP:**

| Roadmap SC | Validation | Cubierto por |
|-----------|------------|--------------|
| 1. Burbuja aparece sobre WhatsApp/Maps/home + drag + magnet | Smoke test manual Task 3 paso 3 | Plan 03 Task 2 + 3 |
| 2. TTS de boot "Hola, soy Beto..." sin race condition | Smoke test manual Task 3 paso 3 (primer item) | Plan 02 (TtsManager cola pre-init) + Plan 03 Task 1 (speakBootGreeting al startForeground OK) |
| 3. Falta permiso → Beto avisa por voz qué falta + deep link | Test paso 4 (revocar accessibility, re-abrir) | Plan 02 (PreflightCheck + MainActivity.handlePreflightResult) |
| 4. AgentBus end-to-end funciona | Test paso 5 (logcat Beto-Bus durante tap/long-press) | Plan 02 (AgentBus + AgentEvents) + Plan 03 Task 3 (OverlayBubble emite) |

**Cobertura de los 13 requirements de Phase 1:**

| REQ | Plan | Verificación |
|-----|------|--------------|
| SETUP-01 (Kotlin/AGP/Gradle/minSdk/Java) | 01 Task 1 | `./gradlew assembleDebug` pasa con configs target |
| SETUP-02 (manifest services + permisos) | 01 Task 2 | `aapt dump permissions` muestra los 8 + servicios |
| SETUP-03 (accessibility_service_config.xml) | 01 Task 2 | grep `canRetrieveWindowContent="true"` |
| SETUP-04 (APK instalable + Firebase config) | 01 Task 1 + 3 | `adb install` exitoso + google-services.json placeholder no falla |
| SETUP-05 (Timber con tags Beto-XXX) | 02 Task 1 + 2 | LogTags object + Timber.plant en BetoApplication |
| BUS-01 (AgentBus singleton SharedFlow) | 02 Task 1 | grep `object AgentBus` + flows accesibles |
| BUS-02 (AgentEvents sealed) | 02 Task 1 | sealed class AgentEvent con 8 variantes Phase 1 |
| BUS-03 (ToolDescriptors stubs) | 02 Task 1 | ALL_TOOL_NAMES con 5 IDs |
| OVERLAY-01 (burbuja persistente + drag + magnet) | 03 Task 2 + 3 | Smoke test manual |
| OVERLAY-02 (TYPE_ACCESSIBILITY_OVERLAY ↔ TYPE_APPLICATION_OVERLAY) | 03 Task 2 | OverlayManager.pickOverlayType |
| VOICE-01 (TtsManager cascada Locale) | 02 Task 2 | LOCALE_CASCADE con 5 entries + cola pre-init |
| VOICE-02 (TTS frase de boot) | 02 Task 2 + 03 Task 1 | speakBootGreeting() invocado en FGS onStartCommand |
| DEMO-04 (pre-flight check) | 02 Task 3 | PreflightCheck.check + MainActivity.handlePreflightResult |

**Final integration test (después de los 3 plans):**

```bash
cd android
./gradlew clean assembleDebug   # BUILD SUCCESSFUL
./gradlew installDebug          # Success
adb shell am start -n com.beto.app/.MainActivity  # Lanza la app
adb logcat -s "Beto-Accessibility:D" "Beto-LLM:D" "Beto-Action:D" "Beto-STT:D" "Beto-Intent:D" "Beto-TTS:D" "Beto-Bus:D"
```

Validar logs en orden:
1. `BetoApplication.onCreate — Beto starting up`
2. `TtsManager init() — creating TextToSpeech`
3. `TtsManager onInit SUCCESS — locale=es_AR` (o cualquiera de la cascada)
4. `MainActivity.onCreate`
5. `Preflight result: overlay=true a11y=true tts=true`
6. `Preflight OK — starting BetoForegroundService and finishing`
7. `BetoForegroundService.onCreate`
8. `startForeground OK type=microphone`
9. `Bubble shown — type=ACCESSIBILITY_OVERLAY` (o APPLICATION si AS no conectado)
10. `Disparando speakBootGreeting (D-10)`
11. `TtsManager speak ok ... text=Hola, soy Beto. Estoy acá para ayudarte.`
12. `AgentBus emit -> BootCompleted`
</verification>

<success_criteria>
1. `./gradlew assembleDebug` retorna BUILD SUCCESSFUL.
2. APK instalable + abrible sin crashes.
3. Pre-flight check guía al usuario por voz a otorgar permisos faltantes (DEMO-04).
4. BetoForegroundService persiste con notif "Beto está acá" no swipe-dismissable.
5. Voz de bienvenida pronuncia "Hola, soy Beto. Estoy acá para ayudarte." al primer start (sin race — Pitfall #3 mitigated).
6. Burbuja flotante de 64dp con project_logo + ring placeholder gris aparece en borde derecho mitad vertical.
7. Burbuja visible sobre WhatsApp, Maps, home (Pitfall #10 documentado para apps FLAG_SECURE — fuera de scope demo).
8. Burbuja arrastrable; al soltar, magnet al borde más cercano con animación 180ms.
9. Tap corto emite AgentEvent.BubbleTapped al bus; long-press emite BubbleLongPressed con haptic feedback.
10. **Phase 1 Walking Skeleton completo: APK instalable + burbuja visible + voz hablando + bus end-to-end funcional.**
</success_criteria>

<output>
After completion, create `.planning/phases/01-foundation-sync-de-hora-0/01-03-SUMMARY.md` with:
- Confirmación de los 4 success criteria del roadmap (con timestamps de logcat).
- Cualquier issue encontrado en el teléfono específico de demo (Locale TTS elegido, problemas con OEM, etc.).
- Notas para Phase 2: cómo VoiceCaptureActivity debe disparar (collector de AgentEvent.BubbleTapped en BetoForegroundService llama un Intent transparente).
- Notas para Phase 3: cómo CompanionActivity debe levantar (collector de AgentEvent.BubbleLongPressed).
- Versiones reales del APK debug pesado (`ls -lh app-debug.apk`).
- Lista de pitfalls verificados como mitigados: #1 (heartbeat AS), #3 (TTS pre-warmed), #8 (FGS persiste 30+ min screen lock — TEST CON TIMER), #10 (overlay sobre apps demo), #11 (foregroundServiceType microphone), #12 (pre-flight detecta permisos faltantes).
</output>
