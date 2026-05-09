---
phase: 01-foundation-sync-de-hora-0
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - android/settings.gradle.kts
  - android/build.gradle.kts
  - android/gradle.properties
  - android/gradle/wrapper/gradle-wrapper.properties
  - android/gradle/libs.versions.toml
  - android/app/build.gradle.kts
  - android/app/proguard-rules.pro
  - android/app/google-services.json
  - android/app/src/main/AndroidManifest.xml
  - android/app/src/main/res/xml/accessibility_service_config.xml
  - android/app/src/main/res/values/strings.xml
  - android/app/src/main/res/drawable/project_logo.png
  - android/app/src/main/res/drawable/ic_beto_notification.xml
  - android/.gitignore
  - .gitignore
autonomous: true
requirements: [SETUP-01, SETUP-02, SETUP-03, SETUP-04]
must_haves:
  truths:
    - "El proyecto Android compila desde la subcarpeta android/ con `./gradlew assembleDebug` sin errores."
    - "El APK generado se puede instalar en el teléfono de demo con `adb install`."
    - "El AndroidManifest.xml declara BetoForegroundService con foregroundServiceType=microphone y BetoAccessibilityService con BIND_ACCESSIBILITY_SERVICE filter (aunque las clases todavía no existan, el manifest las referencia)."
    - "google-services.json placeholder está presente para que el plugin de Google Services no falle el build."
    - "accessibility_service_config.xml existe con los flags y eventos correctos."
  artifacts:
    - path: "android/settings.gradle.kts"
      provides: "Configuración pluginManagement con AGP 8.7.3, Kotlin 2.1.10, plugin.serialization y google-services 4.4.2"
    - path: "android/app/build.gradle.kts"
      provides: "build config con compileSdk 34, minSdk 31, targetSdk 34, applicationId com.beto.app, Java 11 + coreLibraryDesugaring 2.1.2, dependencias Firebase BoM 33.5.1 + firebase-ai + Timber + kotlinx serialization + coroutines"
      contains: "applicationId = \"com.beto.app\""
    - path: "android/app/src/main/AndroidManifest.xml"
      provides: "Permisos críticos + declaración de BetoForegroundService + BetoAccessibilityService + MainActivity"
      contains: "android.permission.FOREGROUND_SERVICE_MICROPHONE"
    - path: "android/app/src/main/res/xml/accessibility_service_config.xml"
      provides: "Configuración del AccessibilityService con canRetrieveWindowContent=true y eventos correctos"
      contains: "canRetrieveWindowContent=\"true\""
    - path: "android/app/google-services.json"
      provides: "Placeholder válido para que el plugin de Google Services no falle el build (puede ser dummy en hackathon)"
  key_links:
    - from: "android/app/src/main/AndroidManifest.xml"
      to: "BetoForegroundService"
      via: "<service> declaration con foregroundServiceType=microphone"
      pattern: "foregroundServiceType=\"microphone\""
    - from: "android/app/src/main/AndroidManifest.xml"
      to: "accessibility_service_config.xml"
      via: "<meta-data android:name=android.accessibilityservice ... android:resource=@xml/accessibility_service_config>"
      pattern: "android.accessibilityservice"
    - from: "android/app/build.gradle.kts"
      to: "Firebase BoM 33.5.1"
      via: "implementation(platform(\"com.google.firebase:firebase-bom:33.5.1\"))"
      pattern: "firebase-bom:33.5.1"
---

<objective>
Crear el esqueleto Gradle del proyecto Android en la subcarpeta `android/`, configurar el manifest con todos los permisos críticos y las declaraciones de los dos servicios (FGS + AccessibilityService), y dejar el archivo de configuración del AccessibilityService listo. Incluye Firebase setup mínimo (deps + google-services.json placeholder) para que el SDK de Gemini se pueda usar más adelante en Phase 3 sin tener que tocar el build.

Purpose: Sin esto NADA compila. Es el primer paso del Walking Skeleton — todo lo demás depende de tener un APK instalable. Concentrar Gradle + manifest + permisos + accessibility config en un solo plan evita 4 PRs de configuración y deja el contrato declarativo congelado de una.

Output: Un proyecto Android compilable con `./gradlew assembleDebug` desde `android/`, un APK que se puede instalar en el teléfono de demo (aunque al abrirlo crashee porque las clases todavía no existen — eso lo resuelve Plan 02 + 03), y todo el manifest/config XML congelado.

User decisions (CONTEXT.md):
- D-01: Proyecto Android en subcarpeta `android/` (NO en raíz)
- D-02: Application ID `com.beto.app` hardcoded
- D-15: Notif del FGS — canal `beto_service` con IMPORTANCE_LOW (declarado en manifest indirectamente via metadata, channel real se crea en código en Plan 03)
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
@.planning/research/STACK.md
@.planning/research/PITFALLS.md
@CLAUDE.md
@project-logo.png

<interfaces>
<!-- No hay código existente. Este plan establece el esqueleto Gradle y XML; las clases Kotlin las crean Plan 02 y 03. -->
<!-- Convenciones de paquete (D-02 — superficies disjuntas para 5 devs): -->

Estructura de paquetes Kotlin objetivo (todavía vacíos en este plan, los crean Plan 02/03):
- `com.beto.app` — BetoApplication, MainActivity
- `com.beto.app.bus` — AgentBus, AgentEvent, AgentCommand
- `com.beto.app.service` — BetoForegroundService, BetoAccessibilityService
- `com.beto.app.overlay` — OverlayManager, OverlayBubble
- `com.beto.app.voice` — TtsManager, VoiceCaptureActivity (Phase 2)
- `com.beto.app.llm` — ToolDescriptors (Phase 1 stubs), LlmClient (Phase 3)
- `com.beto.app.action` — ActionDispatcher, IntentBranch (Phase 2-3)
- `com.beto.app.agent` — AgentLoop (Phase 4)
- `com.beto.app.companion` — CompanionActivity (Phase 3)
- `com.beto.app.ui` — UI utilities (Phase 4)
- `com.beto.app.util` — helpers, Logging tags

Stack pinned (NO cambiar versiones):
- Kotlin 2.1.10
- AGP 8.7.3
- Gradle 8.10
- minSdk 31, targetSdk 34, compileSdk 34
- Java 11 + coreLibraryDesugaring 2.1.2
- Firebase BoM 33.5.1
- kotlinx-coroutines-android 1.8.1
- kotlinx-serialization-json 1.7.3
- Timber 5.0.1
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Inicializar el proyecto Gradle en android/ con AGP 8.7.3 + Kotlin 2.1.10 + Firebase + dependencias core</name>
  <files>
    android/settings.gradle.kts,
    android/build.gradle.kts,
    android/gradle.properties,
    android/gradle/wrapper/gradle-wrapper.properties,
    android/gradle/libs.versions.toml,
    android/app/build.gradle.kts,
    android/app/proguard-rules.pro,
    android/app/google-services.json,
    android/.gitignore,
    .gitignore
  </files>
  <action>
Crear la estructura del proyecto Android en la subcarpeta `android/` (D-01 — NO en raíz). Usar `gradle wrapper` o copiar wrapper estándar de AGP 8.7.3.

**1. Estructura de carpetas mínima:**
```
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── .gitignore
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    ├── google-services.json
    └── src/main/
        ├── AndroidManifest.xml          (Task 2)
        ├── java/com/beto/app/.gitkeep   (placeholder hasta Plan 02)
        └── res/
            ├── drawable/                 (Task 3)
            ├── mipmap-*/                 (íconos lanzador — usar herramienta estándar de AGP)
            ├── values/strings.xml        (Task 3)
            └── xml/accessibility_service_config.xml (Task 2)
```

**2. `android/settings.gradle.kts`:**
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Beto"
include(":app")
```

**3. `android/build.gradle.kts` (root):**
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
}
```

**4. `android/gradle/libs.versions.toml`:**
```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.10"
googleServices = "4.4.2"
coroutines = "1.8.1"
serialization = "1.7.3"
timber = "5.0.1"
firebaseBom = "33.5.1"
desugar = "2.1.2"
lifecycle = "2.8.7"

[libraries]
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-ai = { group = "com.google.firebase", name = "firebase-ai" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }
desugar-jdk-libs = { group = "com.android.tools", name = "desugar_jdk_libs", version.ref = "desugar" }
androidx-lifecycle-service = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycle" }
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.13.1" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

**5. `android/app/build.gradle.kts`:**
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.beto.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.beto.app"  // D-02
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false  // hackathon — no obfuscation
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Firebase + Gemini (LLM se usa desde Phase 3 — solo declarar deps acá)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)

    // Kotlin core
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)

    // Logging
    implementation(libs.timber)

    // Desugaring (necesario por minSdk 31 + Java 11 APIs)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
```

**6. `android/gradle.properties`:**
```
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

**7. `android/gradle/wrapper/gradle-wrapper.properties`:**
```
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

**8. `android/.gitignore`:**
```
.gradle/
build/
local.properties
.idea/
*.iml
captures/
.cxx/
```

**9. Actualizar `.gitignore` raíz** para añadir patrones Android si no existen:
```
android/.gradle/
android/build/
android/app/build/
android/local.properties
android/.idea/
*.iml
```

**10. `android/app/google-services.json`** — placeholder válido (estructura mínima que el plugin Google Services 4.4.2 acepta sin error de parsing). En hackathon, esto es suficiente porque Phase 1 NO inicializa Firebase en runtime — solo necesitamos que el plugin de Gradle no falle el build:
```json
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "beto-hackathon-placeholder",
    "storage_bucket": "beto-hackathon-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000",
        "android_client_info": {
          "package_name": "com.beto.app"
        }
      },
      "oauth_client": [],
      "api_key": [
        { "current_key": "AIzaSyDummyKeyForBuildOnlyHackathonPlaceholderXXXX" }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
```
NOTA: cuando Phase 3 conecte el LLM real, este archivo se reemplaza con el descargado de Firebase Console. El placeholder solo sirve para que `./gradlew assembleDebug` pase.

**11. `android/app/proguard-rules.pro`** — vacío con un comentario `# Hackathon: minify off, no rules needed`.

**12. Generar gradle wrapper** ejecutando `gradle wrapper --gradle-version 8.10 --distribution-type bin` desde `android/` (asume `gradle` instalado, sino bajar el JAR del wrapper manualmente). Si no se puede generar el wrapper en este entorno, dejar TODO en el código y documentar comando para el dev humano.

**13. Crear `android/app/src/main/java/com/beto/app/.gitkeep`** para que git trackee el directorio vacío (las clases reales las crean Plan 02 y 03).

**Convenciones que SE RESPETAN (CLAUDE.md):**
- Modularidad estricta — Gradle config sin lógica
- Sin sobre-ingeniería — NO BuildSrc, NO convention plugins, NO version catalogs custom más allá de libs.versions.toml estándar
- Sin Hilt, sin Compose en este plan (Compose llega en Phase 3 para CompanionActivity)
  </action>
  <verify>
    <automated>
cd android && ./gradlew assembleDebug --offline 2>&1 | tee /tmp/beto-build-1.log; tail -30 /tmp/beto-build-1.log
    </automated>
Smoke test manual:
1. `cd android && ./gradlew assembleDebug` debe terminar con BUILD SUCCESSFUL.
2. `ls android/app/build/outputs/apk/debug/app-debug.apk` debe existir.
3. (Si hay teléfono conectado): `adb install android/app/build/outputs/apk/debug/app-debug.apk` debe instalar sin errores. Al abrir crasheará porque MainActivity no existe — eso es esperado, lo resuelve Plan 02.
  </verify>
  <done>
- `./gradlew assembleDebug` retorna BUILD SUCCESSFUL desde `android/`.
- `app-debug.apk` se genera en `android/app/build/outputs/apk/debug/`.
- `google-services.json` placeholder existe, plugin Google Services no falla.
- Estructura de carpetas creada según D-01 (proyecto en `android/`).
- ApplicationId es `com.beto.app` (verificable con `aapt dump badging app-debug.apk | grep package`).
  </done>
</task>

<task type="auto">
  <name>Task 2: Escribir AndroidManifest.xml con permisos + servicios + accessibility_service_config.xml</name>
  <files>
    android/app/src/main/AndroidManifest.xml,
    android/app/src/main/res/xml/accessibility_service_config.xml
  </files>
  <action>
Crear el manifest con TODAS las declaraciones que Phase 1 exige. Las clases Kotlin todavía no existen — eso es OK, el manifest las referencia y Plan 02/03 las crean. El proyecto compilará en Task 1 antes de este task; al terminar Task 2 el manifest queda completo aunque el `assembleDebug` siga compilando (no hay validación de existencia de clases en tiempo de manifest).

**1. `android/app/src/main/AndroidManifest.xml`** — declarar:

Permisos (CONTEXT.md decisión sobre permisos del manifest + Pitfall #11 — FGS sin foregroundServiceType tira SecurityException en Android 14+):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Permisos críticos Phase 1+ (CONTEXT.md decisión D-XX permisos) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:name=".BetoApplication"
        android:allowBackup="false"
        android:icon="@drawable/project_logo"
        android:label="@string/app_name"
        android:roundIcon="@drawable/project_logo"
        android:theme="@android:style/Theme.Material.Light.NoActionBar"
        android:supportsRtl="true"
        tools:targetApi="34">

        <!-- MainActivity minimalista — pre-flight check launcher (D-09) -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- BetoForegroundService (Plan 03 lo implementa) — Pitfall #11 -->
        <service
            android:name=".service.BetoForegroundService"
            android:exported="false"
            android:foregroundServiceType="microphone" />

        <!-- BetoAccessibilityService (Plan 02 lo crea esqueleto, Phase 4 lo expande) -->
        <service
            android:name=".service.BetoAccessibilityService"
            android:exported="true"
            android:label="@string/accessibility_service_label"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

    </application>
</manifest>
```

NOTAS críticas (Pitfall #11):
- `android:foregroundServiceType="microphone"` y permission `FOREGROUND_SERVICE_MICROPHONE` son OBLIGATORIOS — sin esto Android 14 tira SecurityException al `startForeground()`.
- `android:exported` es obligatorio en API 31+ para todos los componentes con intent-filter.
- `BetoAccessibilityService` debe tener `exported=true` + permission `BIND_ACCESSIBILITY_SERVICE` (sino Android no lista el toggle en Settings).

**2. `android/app/src/main/res/xml/accessibility_service_config.xml`** (CONTEXT.md decisión sobre AS):

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewClicked"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:notificationTimeout="100"
    android:description="@string/accessibility_service_description"
    android:summary="@string/accessibility_service_summary" />
```

NOTAS:
- `canRetrieveWindowContent=true` requerido para leer árbol de vistas (SETUP-03).
- Flags `flagIncludeNotImportantViews|flagReportViewIds` mejoran cobertura de nodos (Pitfall #1 — reduce excusas del sistema para deshabilitar el AS).
- Eventos limitados a 3 tipos (NO `typeViewTextChanged` ni `typeViewScrolled`) — evita ANR en apps con animaciones (Pitfall #5).
- `canPerformGestures=true` lo dejamos previsto para Phase 4 (loop agéntico). Phase 1 no lo usa pero estar declarado no hace daño.

**Logging (SETUP-05 — pertenece al Plan 02 pero acá garantizamos los strings):** ningún cambio.
  </action>
  <verify>
    <automated>
cd /Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12 && grep -q 'foregroundServiceType="microphone"' android/app/src/main/AndroidManifest.xml && grep -q 'FOREGROUND_SERVICE_MICROPHONE' android/app/src/main/AndroidManifest.xml && grep -q 'BIND_ACCESSIBILITY_SERVICE' android/app/src/main/AndroidManifest.xml && grep -q 'canRetrieveWindowContent="true"' android/app/src/main/res/xml/accessibility_service_config.xml && echo "MANIFEST OK"
    </automated>
Smoke test:
1. `cd android && ./gradlew assembleDebug` sigue funcionando (manifest válido).
2. `aapt dump permissions app/build/outputs/apk/debug/app-debug.apk` muestra los 8 permisos declarados.
3. `aapt dump xmltree app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml` muestra el `<service>` con `foregroundServiceType=microphone`.
  </verify>
  <done>
- AndroidManifest.xml declara los 8 permisos críticos.
- BetoForegroundService declarado con `foregroundServiceType="microphone"`.
- BetoAccessibilityService declarado con `BIND_ACCESSIBILITY_SERVICE` permission y meta-data apuntando a accessibility_service_config.
- accessibility_service_config.xml tiene `canRetrieveWindowContent=true` y los 3 eventos correctos.
- `./gradlew assembleDebug` sigue retornando BUILD SUCCESSFUL.
  </done>
</task>

<task type="auto">
  <name>Task 3: Copiar logo + crear strings.xml + drawable de notificación</name>
  <files>
    android/app/src/main/res/drawable/project_logo.png,
    android/app/src/main/res/drawable/ic_beto_notification.xml,
    android/app/src/main/res/values/strings.xml
  </files>
  <action>
Migrar el asset del logo y crear los strings que el manifest referencia.

**1. Copiar `project-logo.png` desde la raíz a `android/app/src/main/res/drawable/project_logo.png`** (D-03 — cero assets nuevos, identidad consistente con README).

```bash
cp project-logo.png android/app/src/main/res/drawable/project_logo.png
```

**2. `android/app/src/main/res/values/strings.xml`:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Beto</string>

    <!-- AccessibilityService -->
    <string name="accessibility_service_label">Beto</string>
    <string name="accessibility_service_description">Beto necesita acceso a la accesibilidad para entender lo que pasa en la pantalla y ayudarte a usar el celular.</string>
    <string name="accessibility_service_summary">Beto te ayuda a usar el teléfono</string>

    <!-- Notificación FGS (D-15) -->
    <string name="fgs_notification_channel_name">Beto activo</string>
    <string name="fgs_notification_channel_description">Notificación que mantiene a Beto siempre disponible.</string>
    <string name="fgs_notification_title">Beto está acá</string>
    <string name="fgs_notification_text">Tocame en la burbuja para hablar</string>

    <!-- TTS frases (D-10) -->
    <string name="tts_boot_greeting">Hola, soy Beto. Estoy acá para ayudarte.</string>
    <string name="tts_overlay_missing">Para ayudarte necesito permiso para mostrarme arriba de las apps. Te llevo a configurarlo.</string>
    <string name="tts_accessibility_missing">Necesito acceso a la accesibilidad para entender la pantalla. Te llevo a configurarlo.</string>
    <string name="tts_tts_missing">No pude prender mi voz. Probá reiniciar el teléfono.</string>
</resources>
```

NOTA — Tono de Beto (CLAUDE.md "Tono de los Prompts Internos"): vocabulario simple, cálido, paciente, frases cortas, transmite seguridad y empatía. Validar que cada string cumpla esto.

**3. `android/app/src/main/res/drawable/ic_beto_notification.xml`** — vector simple para la notificación del FGS (Android Notif requires monochromatic 24dp para small icon — un PNG con color no funciona como statusBar icon). Usamos un vector minimalista del logo o una "B" estilizada:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM10.5,16h3.5c0.83,0 1.5,-0.67 1.5,-1.5 0,-0.65 -0.42,-1.21 -1,-1.42 0.58,-0.21 1,-0.77 1,-1.42v-1.16c0,-0.83 -0.67,-1.5 -1.5,-1.5h-3.5v7zM12,11h2v1h-2v-1zM12,14h2v1h-2v-1z" />
</vector>
```
NOTA: Plan 03 puede sustituir este vector por un drawable derivado del logo si suma valor; CONTEXT.md "Claude's Discretion" lo permite. El logo PNG (`project_logo`) se usa como `largeIcon` y como avatar de la burbuja; este vector solo es para el `smallIcon` de la statusBar.

**Verificar paths**: el manifest de Task 2 referencia `@drawable/project_logo` (icon de la app) y los strings de `accessibility_service_label`, `accessibility_service_description`, `accessibility_service_summary` — todos definidos acá.
  </action>
  <verify>
    <automated>
cd /Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12 && test -f android/app/src/main/res/drawable/project_logo.png && grep -q 'tts_boot_greeting' android/app/src/main/res/values/strings.xml && grep -q 'Hola, soy Beto. Estoy acá para ayudarte' android/app/src/main/res/values/strings.xml && cd android && ./gradlew assembleDebug 2>&1 | tail -5 | grep -q 'BUILD SUCCESSFUL' && echo "ASSETS+BUILD OK"
    </automated>
Smoke test:
1. Logo copiado, drawable resuelve.
2. `./gradlew assembleDebug` sigue verde.
3. `aapt dump strings app-debug.apk | grep 'soy Beto'` retorna la frase de boot exacta.
  </verify>
  <done>
- `project_logo.png` está en `android/app/src/main/res/drawable/`.
- `strings.xml` define todos los strings que Plan 02 y 03 van a consumir (frase de boot, mensajes pre-flight, notif del FGS).
- `ic_beto_notification.xml` vector existe.
- `./gradlew assembleDebug` retorna BUILD SUCCESSFUL.
- APK final pesa <5MB (verificar `ls -lh app-debug.apk`).
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Build → APK | Toolchain Gradle/AGP firma el APK con debug keystore (build env confiable; teléfono dedicado) |
| google-services.json placeholder | Sin Firebase real conectado en Phase 1 — sin secretos en este archivo |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01-01 | I (Information Disclosure) | google-services.json | accept | Placeholder dummy sin API keys reales; cuando Phase 3 conecte Firebase, regenerar y NO commitear (agregar a `.gitignore` si se vuelve real). Hackathon: teléfono dedicado, no se publica en Play. |
| T-01-02 | E (Elevation of Privilege) | AndroidManifest permisos sensibles (RECORD_AUDIO, SYSTEM_ALERT_WINDOW, BIND_ACCESSIBILITY_SERVICE) | accept | Permisos otorgados manualmente por el dev; teléfono dedicado; no se publica. Documentado en `<security_threat_model>` del planning context. |
| T-01-03 | T (Tampering) | APK no firmado para release | accept | Hackathon: solo debug builds firmados con debug keystore (Android Studio default). No se distribuye a usuarios. |
| T-01-04 | S (Spoofing) | BetoAccessibilityService exported=true | mitigate | Permission `BIND_ACCESSIBILITY_SERVICE` es system-only — solo el sistema Android puede bindearse. Configuración estándar Android. |
</threat_model>

<verification>
**Smoke test agregado de Plan 01:**

1. Desde `/Users/msancheznovelli/Developer/platanus/platanus-hack-26-ar-team-12/android`:
   ```bash
   ./gradlew clean assembleDebug
   ```
   Esperado: `BUILD SUCCESSFUL` en <60s (descargas iniciales pueden agregar tiempo).

2. APK existe y es instalable:
   ```bash
   ls -lh app/build/outputs/apk/debug/app-debug.apk   # debe existir
   aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep 'package: name'
   # esperado: package: name='com.beto.app' versionCode='1' versionName='0.1.0'
   ```

3. Permisos correctos:
   ```bash
   aapt dump permissions app/build/outputs/apk/debug/app-debug.apk
   # esperado: 8 uses-permission incluyendo FOREGROUND_SERVICE_MICROPHONE, SYSTEM_ALERT_WINDOW, BIND_ACCESSIBILITY_SERVICE
   ```

4. Si hay teléfono conectado por adb:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   # esperado: Success
   ```
   Al abrir la app desde launcher, va a crashear porque MainActivity no existe — eso es esperado, lo resuelve Plan 02.

**Critical path validations:**
- [ ] `foregroundServiceType="microphone"` en manifest (Pitfall #11)
- [ ] `BIND_ACCESSIBILITY_SERVICE` permission en BetoAccessibilityService
- [ ] `canRetrieveWindowContent=true` en accessibility_service_config.xml
- [ ] `applicationId = "com.beto.app"` (D-02)
- [ ] Proyecto en `android/`, no en raíz (D-01)
- [ ] Frase de boot en strings.xml es EXACTAMENTE "Hola, soy Beto. Estoy acá para ayudarte." (D-10)
</verification>

<success_criteria>
1. `./gradlew assembleDebug` desde `android/` retorna BUILD SUCCESSFUL.
2. APK instalable en el teléfono de demo (aunque crashee al abrir — falta Plan 02/03).
3. Manifest declara los 2 servicios + 8 permisos críticos correctamente.
4. accessibility_service_config.xml válido y conforme a SETUP-03.
5. google-services.json placeholder no rompe el build.
6. Cero advertencias de Lint críticas (`./gradlew lint` puede mostrar warnings menores; CRITICALS = 0).
7. Estructura del repo: `/android/` contiene todo el proyecto Android, raíz queda con `README.md`, `.planning/`, `platanus-hack-project.json`, `project-logo.png`, `CLAUDE.md`, `.git/`.
</success_criteria>

<output>
After completion, create `.planning/phases/01-foundation-sync-de-hora-0/01-01-SUMMARY.md` with:
- Versions usadas exactas (AGP, Kotlin, Gradle).
- Comandos de build exitosos.
- Tamaño del APK debug.
- Cualquier desviación de las versiones target (con justificación).
- Notas para Plan 02 (qué archivos esperar, package roots).
</output>
