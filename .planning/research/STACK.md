# Stack Research — Beto

**Domain:** Android nativo (Kotlin) — agente multimodal autónomo con AccessibilityService + STT/TTS nativo + LLM cloud con tool calling
**Researched:** 2026-05-09
**Confidence:** HIGH (decisión LLM, stack Android core), MEDIUM (versiones exactas de librerías auxiliares)

---

## TL;DR — Decisión Crítica

**LLM Provider: Google Gemini 2.5 Flash vía Firebase AI Logic SDK** (Confidence: HIGH)

Tiebreaker explícito sobre Claude Haiku 4.5 (ganador secundario): **eliminación del backend para hackathon**. Firebase AI Logic permite llamar Gemini directamente desde el cliente Android con autenticación gestionada por Firebase y App Check, sin que el equipo escriba ni hostee un proxy. En 24-36hs eso son ~6-10 horas ahorradas que se reinvierten en el motor de acciones.

Si Gemini falla en demos en es-AR (improbable — Google entrena fuerte en español), **fallback inmediato a Claude Haiku 4.5 vía `anthropic-java` con OkHttp**, que tiene mejor calidad agéntica/tool calling pero requiere proxy o key embebida (riesgo aceptable en demo controlada).

---

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **Android (target)** | API 34 (Android 14) | Plataforma | API estable, todas las apps demo (WhatsApp, Maps) la soportan, AccessibilityService maduro |
| **Android (minSdk)** | **API 31 (Android 12)** | Min compatibility | El teléfono de demo es "moderno, asumimos 13+" (PROJECT.md). Subir a 31 simplifica Compose en Service, overlays modernos (TYPE_APPLICATION_OVERLAY post-26 ya), y permite usar APIs recientes de SpeechRecognizer (on-device). No hay razón para soportar más viejo en demo single-device. |
| **Kotlin** | 2.1.10 | Lenguaje | Estable con AGP 8.7+, soporta `kotlinx-serialization` compiler plugin. Evitar 2.2.x/2.3.x si surge incompatibilidad con AGP elegido — 2.1.10 es zona segura probada. |
| **Android Gradle Plugin (AGP)** | 8.7.x | Build | Estable, no introduce los breaking changes de AGP 9.0 (Kotlin built-in support). Reduce sorpresas en sprint corto. |
| **Gradle** | 8.10 | Build | Compatible con AGP 8.7 y Kotlin 2.1.x. |
| **Java desugaring** | habilitado (`coreLibraryDesugaring 2.x`) | Java 8+ APIs en API 31 | Requerido por `anthropic-java` (fallback LLM) y útil para `java.time` etc. Costo: una línea en build.gradle. |
| **Jetpack Compose** | BOM 2025.10.00+ | UI declarativa | Para sheet de chat (Modo Compañero) y configuración. **NO** para la burbuja flotante (ver "What NOT to use"). |
| **Android Views (XML/code)** | nativo | Burbuja flotante | `WindowManager` + `View` clásico. Compose dentro de Service requiere `ViewTreeLifecycleOwner` manual y agrega 2-3hs de fricción que no aporta a la demo. |
| **Coroutines + Flow** | kotlinx-coroutines 1.8.1 | Async | Estándar Android. STT, llamadas a LLM, AccessibilityService callbacks → todo se modela mejor con `suspend` y `Flow`. |
| **Hilt** | 2.51.1 | DI | OPCIONAL en hackathon. Solo si el equipo ya lo domina. Sino, factory manual / `object Container` global alcanza. **Recomendación: NO usar Hilt** salvo que sea muscle memory — KSP + setup roba 1-2hs. |

### LLM Stack (Decisión Crítica)

| Decision | Choice | Why |
|----------|--------|-----|
| **Provider** | **Google Gemini 2.5 Flash** | Latencia baja, multimodal nativo (texto + imagen + audio), tool calling soportado, free tier generoso sin tarjeta de crédito, calidad sólida en español (Google entrena fuertemente en es). |
| **SDK** | **Firebase AI Logic Android SDK** (`com.google.firebase:firebase-ai`) | **Único SDK oficial que funciona desde el cliente Android sin backend propio.** Auth via Firebase + App Check protege la API key. Devuelve `GenerativeModel` con API idiomática Kotlin/coroutines. Soporta tool calling y vision. |
| **Backend Gemini** | `GenerativeBackend.googleAI()` (Gemini Developer API) | Free tier sin tarjeta. Si en demo se necesita más estabilidad o cuotas → cambiar 1 línea a `vertexAI()` y usar los $300 de credit de GCP. |
| **Modelo** | `gemini-2.5-flash` | Mejor balance latencia/calidad para tool calling en flujo de voz. **No** usar `gemini-2.5-flash-lite` para el agente (Google explícitamente recomienda upgrade para 5+ tool agent workflows — el loop agéntico va a hacer múltiples calls). Lite sí es válido para Modo Compañero (chat simple). |
| **Modelo de Compañero (chat)** | `gemini-2.5-flash-lite` | Más barato/rápido, suficiente para conversación simple cálida. |
| **Tool calling format** | Function declarations nativas de Gemini | Definir top-N intents (`send_whatsapp`, `make_call`, `open_maps`, etc.) como funciones. Para fallback agéntico, una sola tool `perform_ui_action(node_id, action)` que el LLM llama en loop. |

### Audio Stack

| Component | Choice | Notes |
|-----------|--------|-------|
| **STT** | `android.speech.SpeechRecognizer` con `RecognizerIntent.LANGUAGE_MODEL_FREE_FORM` y `EXTRA_LANGUAGE = "es-AR"` | API nativa, gratis, soporta es-AR, on-device en API 31+ con `SpeechRecognizer.createOnDeviceSpeechRecognizer()`. **Para hackathon usar el cloud-backed default** (más preciso, requiere internet pero la demo lo tiene). |
| **TTS** | `android.speech.tts.TextToSpeech` con `Locale("es", "AR")` | Nativo, gratis. Calidad varía por OEM/Google TTS engine instalado. Si el teléfono de demo tiene Google TTS engine actualizado, la voz es aceptable para tono cálido. **Validar en el teléfono específico antes de la demo** — fallback a `Locale("es", "ES")` si es-AR no está disponible. |
| **Detección de fin de habla** | callback nativo de `SpeechRecognizer.onEndOfSpeech` | No reinventar VAD. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **Firebase BoM** | 33.5.1+ | Versionado coherente Firebase | Siempre que se use Firebase AI Logic |
| **`firebase-ai`** | (vía BoM) | Gemini SDK cliente | LLM principal |
| **`firebase-appcheck-playintegrity`** | (vía BoM) | Proteger API key Gemini | Recomendado pero **OPCIONAL en hackathon** — la app no se publica. Si hay 30min, agregarlo; sino, aceptar el riesgo (es demo controlada) |
| **OkHttp** | 4.12.0 | HTTP client (fallback) | Solo si activamos fallback a Anthropic. Ya viene como transitive de muchas libs. |
| **kotlinx.serialization** | 1.7.3 | JSON | Mejor opción para Kotlin nativo + tool calling args parsing. Compiler plugin = type-safe. Más fácil que Moshi en sprint corto si nadie del equipo trae expertise en Moshi. |
| **kotlinx.coroutines-android** | 1.8.1 | Coroutines en Android | Required |
| **AndroidX Lifecycle** | 2.8.7 | LifecycleScope, ViewModel | Para el sheet de chat Compose |
| **Accompanist Permissions** | 0.36.0 | Helpers de permisos | OPCIONAL — solo si se usa Compose para configuración. En demo asumimos permisos pre-otorgados. |
| **Timber** | 5.0.1 | Logging estructurado | OPCIONAL pero útil — `Timber.tag("Beto-Accessibility").d(...)` matchea exactamente CLAUDE.md |

### LLM Fallback Stack (si Gemini falla)

| Library | Version | Purpose |
|---------|---------|---------|
| **`com.anthropic:anthropic-java`** | 2.30.0+ | Cliente Claude oficial (Java, usable desde Kotlin) |
| **`com.anthropic:anthropic-java-client-okhttp`** | 2.30.0+ | Transport OkHttp |
| **Modelo** | `claude-haiku-4-5` | Sub-2s latencia, vision multimodal, tool calling sólido, $1/M input |

Activación del fallback: leer key de `BuildConfig` (no hardcoded en source). En hackathon es aceptable embeber con ofuscación mínima; **no commitear `local.properties`**.

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **Android Studio** | Ladybug Feature Drop o Meerkat | IDE oficial, mejor soporte AccessibilityService debug |
| **adb logcat** | Debug AccessibilityService en vivo | `adb logcat -s "Beto-Accessibility:D" "Beto-LLM:D" "Beto-Action:D"` — tag scheme alineado con CLAUDE.md |
| **scrcpy** | Mirror del teléfono de demo a laptop | Crítico para iterar sin tocar el dispositivo y para grabar el pitch |
| **Layout Inspector** | Inspeccionar AccessibilityNodeInfo trees | Debug del loop agéntico — entender qué ve el árbol antes de mandárselo al LLM |

---

## Installation

```kotlin
// settings.gradle.kts
pluginManagement {
    plugins {
        id("com.android.application") version "8.7.3"
        kotlin("android") version "2.1.10"
        kotlin("plugin.serialization") version "2.1.10"
        id("com.google.gms.google-services") version "4.4.2"
    }
}

// app/build.gradle.kts
android {
    compileSdk = 34
    defaultConfig {
        minSdk = 31
        targetSdk = 34
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Firebase + Gemini (LLM principal)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-playintegrity") // opcional

    // Kotlin core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Compose (solo para sheet de chat / settings)
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // FALLBACK LLM (mantener comentado salvo que se active)
    // implementation("com.anthropic:anthropic-java:2.30.0")
}
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| **Gemini 2.5 Flash** (Firebase AI Logic) | **Claude Haiku 4.5** (`anthropic-java`) | Si la calidad de tool calling de Gemini decepciona en pruebas iniciales o si el equipo ya tiene API key con créditos en Anthropic. Haiku 4.5 tiene mejor reputación en agentic coding/tool chaining según comparativas independientes. Trade-off: requiere proxy o key embebida → 2-4hs extra de setup. |
| **Gemini 2.5 Flash** | **GPT-4o / GPT-5** vía `aallam/openai-kotlin` 4.1.0 | Si el equipo tiene experiencia previa fuerte con OpenAI tool calling. Trade-off: SDK no oficial (community-maintained), no hay "Firebase AI Logic equivalent" — necesitás proxy o key embebida igual que Anthropic. Sin ventaja sobre Claude Haiku para este caso. |
| **STT nativo Android** | **Whisper / OpenAI Realtime API** | Post-MVP. Mejor calidad en es-AR pero suma latencia y dependencia de red — explícitamente Out of Scope en PROJECT.md. |
| **kotlinx.serialization** | **Moshi 1.15** | Si el equipo viene con Moshi muscle memory. Performance de Moshi con codegen es marginalmente superior, pero no relevante en escala de hackathon. |
| **Min SDK 31** | **Min SDK 26** | Si por alguna razón el teléfono de demo termina siendo Android 8-11. Costo: más casos especiales en overlay/SpeechRecognizer on-device. **No recomendado** — el equipo controla el dispositivo de demo. |
| **Burbuja con Views clásicas** | **Burbuja con Compose** | Solo si el equipo tiene experiencia explícita con `ComposeView` dentro de Service y `ViewTreeLifecycleOwner` manual. En hackathon → no vale la pena. |
| **Manual DI** | **Hilt 2.51** | Si el equipo lo usa diariamente. Sino, en hackathon es overhead. |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **React Native / Flutter / KMP cross-platform** | AccessibilityService es API nativa Android profunda. Cualquier wrapper agrega bridges frágiles para callbacks de alta frecuencia. PROJECT.md ya lo descartó. | Kotlin nativo |
| **Vertex AI directo desde cliente** | Requiere autenticación con service account o token federado complicado de manejar desde Android. | **Firebase AI Logic** (que sí está pensado para cliente) o Gemini Developer API directo |
| **`google-cloud-aiplatform` Java SDK en Android** | SDK pesado (>10MB), pensado para servidor, requiere Application Default Credentials. | Firebase AI Logic SDK |
| **Compose en burbuja flotante (`WindowManager` overlay)** | Requiere `ViewTreeLifecycleOwner`/`ViewTreeSavedStateRegistryOwner` manuales en el View root. Funciona pero agrega 2-3hs de pelea. | `View` + `WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY)` |
| **Porcupine wake word** | Non-commercial en free tier (license risk en demo de hackathon que se sube públicamente) + trabajo on-device adicional. | Botón flotante (PROJECT.md ya lo decidió) |
| **Gson** | Reflexión, lento, +300KB APK, sin soporte nativo de `data class` Kotlin. | kotlinx.serialization (o Moshi) |
| **Retrofit** | Innecesario — Firebase AI Logic SDK ya abstrae HTTP. Si se activa fallback Anthropic, su SDK ya trae OkHttp. | (nada — no agregar capa extra) |
| **Whisper / cloud STT** | Out of Scope explícito. Suma latencia + dependencia de red en vivo. | `SpeechRecognizer` nativo |
| **NER on-device para sanitización** | Out of Scope. Suma 2-3 días. | Regex simple para DNI/teléfono/tarjeta |
| **WorkManager para llamadas LLM** | Las llamadas son sincrónicas-foreground driven by user voice. WorkManager es overkill. | `coroutineScope` + `viewModelScope` directo |
| **Anthropic Java SDK 1.x** | Versiones pre-2.0 tienen API menos pulida. | 2.30.0+ |
| **Claude Sonnet 4.5/4.6 como modelo principal** | 3-5s de latencia es UX desastrosa para agente activado por voz. Reservar para análisis offline si surge. | Haiku 4.5 (sub-2s) |
| **Gemini 2.5 Flash-Lite como agente principal** | Google explícitamente recomienda Flash (no Lite) para 5+ tool agent workflows. | Flash para agente, Lite OK para Compañero |

---

## Stack Patterns by Variant

**Si el demo phone es Android 13+ con Google services actualizados:**
- minSdk 31 + Firebase AI Logic + Gemini 2.5 Flash → camino feliz
- TTS es-AR con Google TTS engine

**Si Gemini 2.5 Flash subperforma en es-AR durante pruebas iniciales:**
- Switchear a Claude Haiku 4.5 vía `anthropic-java`
- Aceptar API key embebida + ofuscar con BuildConfig
- Mantener Firebase project para App Check si hay tiempo

**Si la red en el venue es inestable:**
- Aumentar timeouts de Firebase AI Logic a 30s
- Pre-cachear el primer prompt del Compañero
- Tener caminos de Intent (no LLM) listos como demo "garantizada"

**Si el equipo no logra estabilizar el loop agéntico (AccessibilityNodeInfo → LLM → performAction):**
- Demo solo top-3 Intents directos (WhatsApp, llamada, Maps)
- Mostrar el loop agéntico como "vision en roadmap" en el pitch — no es la promesa principal

---

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Kotlin 2.1.10 | AGP 8.7.x, Gradle 8.10 | Combo probado y estable |
| AGP 8.7.3 | Compose Compiler 1.5.15 (auto-managed por Kotlin Compose plugin desde 2.0) | Si se usa Kotlin 2.0+, el compiler de Compose se gestiona vía `org.jetbrains.kotlin.plugin.compose` |
| Firebase BoM 33.5.1 | minSdk 21+ | Sobra para nuestro minSdk 31 |
| `firebase-ai` | Gemini Developer API + Vertex AI backend | Mismo SDK, switch via `GenerativeBackend.googleAI()` vs `vertexAI()` |
| `anthropic-java` 2.30.0 | Java 8+ con desugaring en Android | Requiere `coreLibraryDesugaring` activado |
| `SpeechRecognizer.createOnDeviceSpeechRecognizer()` | API 31+ | Otra razón para minSdk 31 |
| `TYPE_APPLICATION_OVERLAY` | API 26+ | OK con cualquier minSdk decidido |

---

## Razonamiento Detallado: Por qué Gemini gana sobre Claude/GPT

| Criterio | Gemini 2.5 Flash | Claude Haiku 4.5 | GPT-4o/5 | Ganador |
|----------|------------------|-------------------|----------|---------|
| **SDK oficial Kotlin/Android** | Sí (Firebase AI Logic) | No (Java SDK oficial, usable desde Kotlin) | No (community: `aallam/openai-kotlin`) | **Gemini** |
| **Llamada cliente sin backend** | Sí (Firebase Auth + App Check) | No (key embebida o proxy) | No (key embebida o proxy) | **Gemini** (decisivo en hackathon) |
| **Latencia** | Baja (Flash optimizado para latency-sensitive) | Sub-2s (mejor de la industria para Haiku) | GPT-4o ~1-2s | Empate Gemini/Claude |
| **Multimodal (vision)** | Sí (texto + imagen + audio + video) | Sí (texto + imagen) | Sí (texto + imagen) | Empate (Gemini técnicamente más amplio pero no lo necesitamos) |
| **Tool calling robusto** | Bueno; recomendado Flash (no Lite) para 5+ tools | Excelente — Anthropic líder en agentic | Excelente | **Claude** ligeramente |
| **Calidad en es-AR** | Buena (Google entrena fuerte en es) | Buena (multilingüe sólido) | Buena | Empate, validar en demo |
| **Free tier sin tarjeta** | Sí, generoso | No (requiere billing) | No (requiere billing) | **Gemini** |
| **Costo si pagamos** | $0.30/M input / $2.50/M output (Flash) | $1.00/M input / $5.00/M output (Haiku) | Variable, GPT-4o más caro | **Gemini** |
| **Riesgo de leak de API key** | Bajo (App Check) | Alto (embebida) | Alto (embebida) | **Gemini** |

**Tiebreaker final:** Claude Haiku 4.5 es probablemente el mejor LLM puro para agentic tool calling, pero **Firebase AI Logic elimina el problema #1 del hackathon mobile + LLM: cómo llamar al LLM desde el cliente sin filtrar la key**. Eso vale más que el delta de calidad agéntica para una demo de 3-5 minutos con 4-5 comandos guionados.

Si después del primer test (~hora 4-6 del hackathon) el equipo siente que Gemini Flash no responde bien al tool calling con el tree de Accessibility, **swap a Anthropic toma 1-2hs** (cambiar capa LLMClient, agregar dep, testear). Mantener esa puerta abierta arquitectónicamente — capa `LLMClient` interface con dos implementaciones.

---

## Sources

- [Firebase AI Logic Android docs](https://firebase.google.com/docs/ai-logic/get-started) — HIGH confidence — confirma SDK Android oficial Kotlin/Java + auth via Firebase + tool calling
- [Gemini API pricing (Google AI for Developers)](https://ai.google.dev/gemini-api/docs/pricing) — HIGH — confirma free tier sin tarjeta y precios Flash $0.30/$2.50, Lite $0.10/$0.40
- [Gemini models — Google AI for Developers](https://ai.google.dev/gemini-api/docs/models) — HIGH — confirma `gemini-2.5-flash` para agentes 5+ tools, Flash-Lite para single-tool
- [Anthropic Java SDK GitHub](https://github.com/anthropics/anthropic-sdk-java) — HIGH — confirma OkHttp client, Java 8+ con desugaring para Android, v2.30.0 (abril 2026)
- [Anthropic Java SDK on Maven Central](https://central.sonatype.com/artifact/com.anthropic/anthropic-java) — HIGH — versión actual 2.30.0
- [Claude Haiku 4.5 vs Sonnet 4.5 comparativa (Sider)](https://sider.ai/blog/ai-tools/claude-haiku-4_5-vs-sonnet-4-which-model-wins-on-speed-cost-and-capability) — MEDIUM — sub-2s para Haiku, $1/M input
- [Caylent — Claude Haiku 4.5 deep dive](https://caylent.com/blog/claude-haiku-4-5-deep-dive-cost-capabilities-and-the-multi-agent-opportunity) — MEDIUM — confirma agentic tool calling parity con Sonnet 4
- [Aallam openai-kotlin GitHub](https://github.com/aallam/openai-kotlin) — HIGH — confirma 4.1.0 disponible, vision/tool calling, multiplatform — pero unofficial
- [Android RecognizerIntent docs](https://developer.android.com/reference/android/speech/RecognizerIntent) — HIGH — API 3+, soporta `EXTRA_LANGUAGE` con Locale arbitrario
- [Android SpeechRecognizer docs](https://developer.android.com/reference/android/speech/SpeechRecognizer) — HIGH — `createOnDeviceSpeechRecognizer()` requiere API 31+
- [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) — HIGH — confirma AGP 8.x estable; recomendamos 8.7.x para evitar breaking changes
- [Kotlin Gradle compatibility matrix](https://kotlinlang.org/docs/gradle-configure-project.html) — HIGH — Kotlin 2.1.x + Gradle 8.10 + AGP 8.7 = combo soportado
- [Android AccessibilityService docs](https://developer.android.com/guide/topics/ui/accessibility/service) — HIGH — confirma overlays sobre todo sin SYSTEM_ALERT_WINDOW separado, Android 12 touch event changes
- [Java 8+ APIs available through desugaring](https://developer.android.com/studio/write/java8-support-table) — HIGH — confirma path para usar `anthropic-java` en minSdk bajo
- [Moshi vs kotlinx.serialization benchmarks](https://zacsweers.github.io/json-serialization-benchmarking/) — MEDIUM — kotlinx ligeramente más lento pero diferencia irrelevante a escala demo

---
*Stack research for: Beto — Android nativo agéntico*
*Researched: 2026-05-09*
