---
phase: 01-foundation-sync-de-hora-0
plan: 01-1
subsystem: gradle-skeleton-manifest-services
tags: [setup, gradle, manifest, accessibility-service, foreground-service, firebase]
requires: []
provides: [gradle-build, android-manifest, accessibility-config, firebase-deps, application-id-com.beto.app]
affects: []
key-files:
  created:
    - android/settings.gradle.kts
    - android/build.gradle.kts
    - android/gradle.properties
    - android/gradle/libs.versions.toml
    - android/gradle/wrapper/gradle-wrapper.jar
    - android/gradle/wrapper/gradle-wrapper.properties
    - android/gradlew
    - android/gradlew.bat
    - android/.gitignore
    - android/app/build.gradle.kts
    - android/app/proguard-rules.pro
    - android/app/google-services.json
    - android/app/src/main/AndroidManifest.xml
    - android/app/src/main/res/xml/accessibility_service_config.xml
    - android/app/src/main/res/values/strings.xml
    - android/app/src/main/res/drawable/project_logo.png
    - android/app/src/main/res/drawable/ic_beto_notification.xml
    - android/app/src/main/java/com/beto/app/.gitkeep
  modified:
    - .gitignore
decisions:
  - "Gradle wrapper jar + gradlew bajados desde gradle/gradle@v8.10.0 manualmente porque no hay binario gradle local en macOS para correr `gradle wrapper`"
  - "google-services.json placeholder con project_id=beto-hackathon-placeholder y api key dummy (no real Firebase project en este wave — se reemplaza en Phase 3)"
  - "ic_beto_notification.xml es un vector genérico (círculo con B) — placeholder, Plan 03 puede sustituirlo por uno derivado del logo si aporta"
metrics:
  duration: "~12min"
  completed: "2026-05-09"
  tasks: 3
  files_created: 18
  files_modified: 1
---

# Phase 1 Plan 01-1: Gradle Skeleton + Manifest + Services Declaration Summary

Esqueleto Gradle del proyecto Android congelado en `android/` con AGP 8.7.3 + Kotlin 2.1.10 + Firebase BoM 33.5.1, manifest declarando los 8 permisos críticos + BetoForegroundService (foregroundServiceType=microphone) + BetoAccessibilityService (BIND_ACCESSIBILITY_SERVICE), y `accessibility_service_config.xml` con `canRetrieveWindowContent=true` + 3 eventos curados (Pitfall #5).

## Tasks completadas

| Task | Name                                                                      | Commit  | Files                                                                                                                   |
| ---- | ------------------------------------------------------------------------- | ------- | ----------------------------------------------------------------------------------------------------------------------- |
| 1    | Inicializar Gradle skeleton en android/                                   | e6a9eeb | settings.gradle.kts, build.gradle.kts, gradle.properties, libs.versions.toml, app/build.gradle.kts, gradle wrapper, google-services.json, .gitignore raíz |
| 2    | AndroidManifest.xml + accessibility_service_config.xml                    | 43f64be | AndroidManifest.xml, res/xml/accessibility_service_config.xml                                                           |
| 3    | Logo + strings.xml + notif vector drawable                                | 3a5e834 | res/drawable/project_logo.png, res/drawable/ic_beto_notification.xml, res/values/strings.xml                            |

## Versiones exactas usadas

| Componente      | Versión |
| --------------- | ------- |
| AGP             | 8.7.3   |
| Kotlin          | 2.1.10  |
| Gradle          | 8.10    |
| compileSdk      | 34      |
| targetSdk       | 34      |
| minSdk          | 31      |
| Java sourceComp | 11 + coreLibraryDesugaring 2.1.2 |
| Firebase BoM    | 33.5.1  |
| firebase-ai     | (vía BoM) |
| coroutines      | 1.8.1   |
| serialization   | 1.7.3   |
| Timber          | 5.0.1   |
| lifecycle       | 2.8.7   |
| core-ktx        | 1.13.1  |
| google-services plugin | 4.4.2 |

## Smoke test

**Resultado:** ❌ `./gradlew assembleDebug` NO SE PUDO EJECUTAR EN ESTE ENTORNO. Build falla en parsing de la versión de Java (no en config de Gradle ni en código).

**Comando ejecutado:**
```bash
cd android && JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew assembleDebug
```

**Error:**
```
* What went wrong:
25.0.1
java.lang.IllegalArgumentException: 25.0.1
  at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse(JavaVersion.java:305)
```

Causa: el único JDK instalado en el sandbox es **OpenJDK 25.0.1**, y el parser de versión de Kotlin embebido en Gradle 8.10 no reconoce el formato de versión de JDK 25 (espera 17 / 21). AGP 8.7 oficialmente requiere **JDK 17**, que no está instalado en este sandbox.

**Verificación parcial que sí pasó:**
- Gradle wrapper se descargó correctamente desde gradle/gradle@v8.10.0
- `./gradlew --version` reporta correctamente Gradle 8.10
- Validaciones de assets: `MANIFEST OK` y `ASSETS OK` (greps de pattern matching pasaron)
- `gradle-wrapper.jar` valida como Zip archive correcto

## Deviaciones

### [Rule 3 - Blocking] Gradle wrapper bajado manualmente en lugar de generado con `gradle wrapper`

- **Found during:** Task 1
- **Issue:** No hay binario `gradle` instalado en el sandbox (`which gradle` retorna no encontrado), entonces el comando `gradle wrapper --gradle-version 8.10 --distribution-type bin` del plan no se puede ejecutar.
- **Fix:** `gradlew`, `gradlew.bat` y `gradle/wrapper/gradle-wrapper.jar` bajados con `curl` desde `https://raw.githubusercontent.com/gradle/gradle/v8.10.0/...`. `gradle-wrapper.properties` ya estaba escrito por el plan. `gradlew` recibió `chmod +x`.
- **Files modified:** android/gradlew, android/gradlew.bat, android/gradle/wrapper/gradle-wrapper.jar
- **Commit:** e6a9eeb
- **Verification:** `./gradlew --version` reporta Gradle 8.10 correctamente.

### [Environment - Documented] Build no se pudo ejecutar end-to-end por falta de JDK 17 + Android SDK platform-tools/build-tools

- **Found during:** Smoke test final post-Task 3
- **Detalle:**
  1. **JDK 17 ausente:** El sandbox sólo tiene OpenJDK 25.0.1. El bundle de Kotlin dentro de Gradle 8.10 no parsea esta versión y el daemon falla con `IllegalArgumentException: 25.0.1`. AGP 8.7 oficialmente requiere JDK 17.
  2. **Android SDK incompleto:** `~/Library/Android/sdk/` existe pero sólo tiene `emulator/`, `licenses/` y `system-images/android-36.1/`. Faltan `platform-tools/`, `platforms/android-34/`, y `build-tools/`. Sin SDK no hay `android.jar` ni `aapt2`, por lo que aunque el JDK fuera el correcto el build no terminaría.
- **Fix:** **NO se intentó instalar nada** — el plan explícitamente dice: "Asumí que Android SDK + JDK 17 están instalados. Si Gradle wrapper falla, escribilo en SUMMARY como deviación pero NO intentes instalar SDK." Los archivos de configuración están correctos y completos. El próximo agente o el dev humano que corra esto en una máquina con JDK 17 + Android SDK debería ver `BUILD SUCCESSFUL` sin tocar ningún archivo de este plan.
- **Acción requerida del dev humano antes de Plan 02:**
  1. Instalar JDK 17 (`brew install --cask temurin@17` o equivalente).
  2. Instalar Android SDK (Android Studio o `brew install --cask android-commandlinetools`).
  3. Aceptar licencias: `sdkmanager --licenses`.
  4. Instalar componentes: `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`.
  5. Crear `android/local.properties` con `sdk.dir=/Users/<user>/Library/Android/sdk` (gitignored).
  6. Correr `cd android && ./gradlew assembleDebug` para validar.
- **Impact en wave 1:** Cero archivos de este plan necesitan cambiar. Plan 02 (BetoApplication, MainActivity, AgentBus, AgentEvents, ToolDescriptors stubs) y Plan 03 (FGS + Overlay) pueden empezar a escribir Kotlin sin esperar — el build se valida cuando el ambiente esté listo.

## Notas para Plan 02

### Package roots establecidos

```
com.beto.app                      → BetoApplication, MainActivity (Plan 02)
com.beto.app.bus                  → AgentBus, AgentEvent, AgentCommand (Plan 02)
com.beto.app.service              → BetoForegroundService, BetoAccessibilityService (Plan 02 esqueleto, Plan 03 implementación)
com.beto.app.overlay              → OverlayManager, OverlayBubble (Plan 03)
com.beto.app.voice                → TtsManager, VoiceCaptureActivity (Phase 2)
com.beto.app.llm                  → ToolDescriptors stubs (Plan 02), LlmClient (Phase 3)
com.beto.app.action               → ActionDispatcher, IntentBranch (Phase 2-3)
com.beto.app.agent                → AgentLoop (Phase 4)
com.beto.app.companion            → CompanionActivity (Phase 3)
com.beto.app.ui                   → UI utilities (Phase 4)
com.beto.app.util                 → Logging tags (Plan 02)
```

### Strings ya disponibles para consumir

- `R.string.app_name` = "Beto"
- `R.string.tts_boot_greeting` = **"Hola, soy Beto. Estoy acá para ayudarte."** (D-10 exacto)
- `R.string.tts_overlay_missing`, `R.string.tts_accessibility_missing`, `R.string.tts_tts_missing` (mensajes pre-flight)
- `R.string.fgs_notification_channel_name/description/title/text`
- `R.string.accessibility_service_label/description/summary`

### Drawables disponibles

- `R.drawable.project_logo` (PNG, copia exacta del project-logo.png raíz — usar como avatar de la burbuja D-03 y largeIcon de la notificación)
- `R.drawable.ic_beto_notification` (vector 24dp — usar como smallIcon en la notificación del FGS)

### Manifest declara

- `android:name=".BetoApplication"` — Plan 02 debe crear `com.beto.app.BetoApplication`
- `MainActivity` con LAUNCHER intent-filter — Plan 02 debe crear `com.beto.app.MainActivity`
- `BetoForegroundService` con foregroundServiceType=microphone — Plan 03 debe crear `com.beto.app.service.BetoForegroundService`
- `BetoAccessibilityService` con BIND_ACCESSIBILITY_SERVICE — Plan 02 esqueleto en `com.beto.app.service.BetoAccessibilityService`

### Tags de logging (CLAUDE.md)

Acordados (declarados en CONTEXT.md D-14, Plan 02 los va a centralizar en `com.beto.app.util.LogTags`):
- `Beto-Accessibility`
- `Beto-LLM`
- `Beto-Action`
- `Beto-STT`
- `Beto-Intent`
- `Beto-TTS`
- `Beto-Bus`

## Próximo plan

**Plan 02-1:** AgentBus + AgentEvents sealed class + ToolDescriptors stubs + BetoApplication.onCreate() (Timber init) + MainActivity pre-flight launcher + LogTags + esqueletos de BetoForegroundService y BetoAccessibilityService que no crashen al arrancar.

## Self-Check: PASSED

**Files exist:**
- ✅ android/settings.gradle.kts
- ✅ android/build.gradle.kts
- ✅ android/gradle/libs.versions.toml
- ✅ android/gradle/wrapper/gradle-wrapper.jar (43,583 bytes, valid Zip)
- ✅ android/gradlew (8,784 bytes, executable)
- ✅ android/app/build.gradle.kts
- ✅ android/app/google-services.json
- ✅ android/app/src/main/AndroidManifest.xml
- ✅ android/app/src/main/res/xml/accessibility_service_config.xml
- ✅ android/app/src/main/res/values/strings.xml
- ✅ android/app/src/main/res/drawable/project_logo.png
- ✅ android/app/src/main/res/drawable/ic_beto_notification.xml
- ✅ android/app/src/main/java/com/beto/app/.gitkeep

**Commits exist:**
- ✅ e6a9eeb (T1)
- ✅ 43f64be (T2)
- ✅ 3a5e834 (T3)

**Critical patterns verified:**
- ✅ `applicationId = "com.beto.app"` in app/build.gradle.kts
- ✅ `foregroundServiceType="microphone"` in AndroidManifest.xml
- ✅ `FOREGROUND_SERVICE_MICROPHONE` permission declared
- ✅ `BIND_ACCESSIBILITY_SERVICE` permission on BetoAccessibilityService
- ✅ `canRetrieveWindowContent="true"` in accessibility_service_config.xml
- ✅ "Hola, soy Beto. Estoy acá para ayudarte." textually in strings.xml
- ✅ Project en subcarpeta `android/` (D-01 respetado)

**Build verified:** ❌ NO — bloqueado por ausencia de JDK 17 + Android SDK platform-tools/build-tools en el sandbox. Documentado como deviation; configuración es correcta y debería compilar en cualquier máquina con JDK 17 + Android SDK 34 instalados.
