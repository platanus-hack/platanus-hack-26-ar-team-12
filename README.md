<div align="center">

<img src="./project-logo.png" alt="Beto — Logo" width="180" />

# Beto

**Un copiloto de IA, paciente y cálido, que opera el celular por vos.**

*Platanus Hack 26 — Track Vertical AI — team-12 (Buenos Aires)*

[![Platform](https://img.shields.io/badge/platform-Android%2012%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.10-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![Compose](https://img.shields.io/badge/Compose-BOM%202025.04-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![LLM](https://img.shields.io/badge/LLM-Gemini%202.5%20Flash-4285F4?logo=google&logoColor=white)](https://ai.google.dev/)
[![Status](https://img.shields.io/badge/status-Phases%201–4%20Done-success)](./docs/STATUS.md)
[![Tests](https://img.shields.io/badge/tests-128%20green-brightgreen)]()

</div>

---

## Resumen rápido

Beto es una app Android nativa pensada para **adultos mayores hispanohablantes**. En lugar de tocar menús complicados, el usuario habla en español argentino — *"avisale a mi nieto que ya llegué"*, *"llamá a Pedro"*, *"¿cómo mando un audio por WhatsApp?"* — y Beto entiende, confirma con voz cálida y ejecuta la acción.

Funciona como una **burbuja flotante** sobre cualquier app, con cinco capacidades:

1. **Ejecutor multi-canal** — manda WhatsApp, llama, manda SMS, abre Maps. La primera vez te pregunta el medio o el contacto, y lo recuerda para siempre.
2. **Memoria persistente** — guarda tus alias ("nieto" → Juan), preferencias por contacto, hobbies y datos personales que vos elegís recordar.
3. **Modo Compañero** — long-press en la burbuja abre un chat conversacional. Para charlar, no para ejecutar tareas.
4. **Modo Guía con gestos en pantalla** — *"¿cómo subo el volumen?"* y Beto **dibuja una flecha animada** sobre el botón a tocar mientras te explica con voz.
5. **Voz humana argentina** — voz masculina con voseo (*"dale"*, *"tranquilo"*, *"decime"*), generada por Gemini contextualmente — no frases robóticas pre-grabadas.

Por debajo combina `AccessibilityService` para "ver" la pantalla, `SpeechRecognizer` nativo (con corrección por LLM cuando duda), `TextToSpeech` nativo con la mejor voz neural masculina disponible, y un motor híbrido que prefiere caminos confiables (Intents directos) sobre caminos frágiles.

> Proyecto de hackathon (24-36 h). El foco está en una **demo en vivo robusta**.

---

## Tabla de contenidos

1. [Producto](#producto)
2. [Capacidades](#capacidades)
3. [Arquitectura](#arquitectura)
4. [Stack tecnológico](#stack-tecnológico)
5. [Estructura del proyecto](#estructura-del-proyecto)
6. [Instalación paso a paso](#instalación-paso-a-paso)
7. [Cómo usar Beto](#cómo-usar-beto)
8. [Desarrollo](#desarrollo)
9. [Roadmap](#roadmap)
10. [Equipo](#equipo)

---

## Producto

### El problema
Los adultos mayores quedan afuera de la mensajería, los pagos digitales y la navegación porque las interfaces no fueron pensadas para ellos: tipografías chicas, menús anidados, jerga técnica. Hoy dependen de un nieto, un hijo o un vecino para tareas simples.

### La propuesta
Beto es un **agente multimodal** que reemplaza la interacción táctil por **voz natural en español argentino**. El usuario no aprende botones — le habla al celular como le hablaría a un amigo paciente.

### Caso de uso central
> *"Beto, mandale a mi nieto que ya llegué."*

Si es la primera vez, Beto te pregunta cálidamente *"¿Quién es tu nieto?"*. Le decís un nombre, Beto lo busca en tu libreta de contactos y guarda la asociación. La segunda vez, no pregunta — manda directamente. Pre-llena WhatsApp con el destinatario y el mensaje, y reporta éxito con voz argentina.

### Principios de diseño
- **Confiabilidad sobre sofisticación.** Si un Intent directo resuelve la tarea, se usa Intent. El LLM es fallback, no el camino feliz.
- **Tono cálido y simple.** Vocabulario llano, frases cortas, voseo argentino. Quality checks rechazan respuestas con "usted" formal.
- **Privacidad on-device.** Sanitización con regex de DNI / teléfono / tarjeta antes de cualquier llamada al LLM. Memoria del usuario en `EncryptedSharedPreferences`. Profile facts del Compañero solo se guardan con confirmación explícita.
- **Aprende preguntando.** Cuando le falta info, Beto pregunta una vez. La segunda no.
- **Fallbacks elegantes.** Si la red cae, el matcher determinista toma la posta para los comandos del guion principal sin que el usuario lo note.

---

## Capacidades

Lo que funciona end-to-end al cierre de Phase 4:

| Capacidad | Cómo se ve en uso |
|---|---|
| **Burbuja flotante con 5 estados** | Idle gris → Listening azul (mic) → Thinking ámbar (dots) → Speaking verde (altavoz) → Error rojo (alerta). Anillo + ícono + animación gentle pulse. Honra `prefers-reduced-motion`. |
| **STT en `es-AR` con corrector LLM** | `SpeechRecognizer` nativo + `SttCorrector` que llama a Gemini con timeout 1.5s cuando la confianza está baja, usando los contactos del user como contexto. |
| **Voz humana masculina argentina** | `VoiceSelector` itera todas las voces TTS y elige la mejor masculina-neural-AR. Frases generadas por Gemini contextualmente con cache LRU + fallbacks offline. Quality checks rechazan "usted/su" formal. |
| **Acciones multi-canal** | WhatsApp / SMS / llamada / Maps via Intents directos. La primera vez sin canal preferido, Beto pregunta *"¿Por WhatsApp, SMS o llamada?"* y guarda la respuesta. |
| **Memoria persistente** | `UserMemoryStore` con aliases de contactos, preferencia de canal por contacto, perfil del usuario (hobbies, familia). Encriptada con `EncryptedSharedPreferences`. |
| **Acceso a contactos del sistema** | `ContactRepository` lee `ContactsContract` con permiso runtime. Detecta WhatsApp por `mimetype`. Fallback a `DemoContacts` si el user rechaza el permiso. |
| **Modo Compañero (long-press)** | `ModalBottomSheet` Compose con chat cálido (Gemini Flash Lite, temperature 0.4). Captura **opt-in** de profile facts con confirmación explícita Sí/No. Historial in-memory que se descarta al cerrar. |
| **Modo Guía con gestos** | *"¿cómo mando un audio?"* → flecha animada sobre el botón target dentro de la app real, sincronizada con voz paso a paso. 5 acciones curadas v1: audio WhatsApp, videollamada, agregar contacto, subir volumen, abrir cámara. |
| **Privacidad** | Regex on-device para DNI / teléfono AR / tarjeta antes de cualquier payload al LLM. |
| **Foreground Service** | Notificación persistente, no muere en background. |
| **Onboarding de permisos** | `MainActivity` lleva al user paso a paso por overlay, accesibilidad, micrófono, contactos. Si rechaza contactos, modo limitado con `DemoContacts`. |

Estado vivo en [`docs/STATUS.md`](./docs/STATUS.md) y [`docs/03-DEMO-CHECK.md`](./docs/03-DEMO-CHECK.md).

---

## Arquitectura

Beto usa una arquitectura **desacoplada y orientada a eventos**, optimizada para servicios de fondo en Android.

```text
                              ┌──────────────────────┐
                              │   OverlayBubble      │  Burbuja flotante con 5 estados
                              │  (5 states + drag)   │  visuales (Phase 4-02)
                              └──────────┬───────────┘
                                         │ tap / long-press
                  ┌──────────────────────┼──────────────────────┐
                  ▼                      ▼                      ▼
        ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
        │ StartVoiceCapture│  │  OpenCompanion   │  │   Cancel Guide   │
        └────────┬─────────┘  └────────┬─────────┘  └──────────────────┘
                 │                     │
                 ▼                     ▼
        ┌──────────────────┐  ┌──────────────────┐
        │ VoiceCaptureAct. │  │ CompanionActivity│   Compose ModalBottomSheet
        │ (STT + corrector)│  │ (chat cálido)    │   (Phase 4-03)
        └────────┬─────────┘  └────────┬─────────┘
                 │ VoiceCaptured        │ chat / facts opt-in
                 ▼                      │
        ┌──────────────────────────────────────────────────────┐
        │                    AgentBus (SharedFlow)             │  Sistema nervioso
        └──────────┬─────────────────┬─────────────────┬───────┘
                   │                 │                 │
                   ▼                 ▼                 ▼
       ┌────────────────────┐  ┌──────────────┐  ┌────────────────┐
       │ ActionDispatcher   │  │ OverlayMgr   │  │ GuideController│
       │ (Phase 3 capstone) │  │ (state mach.)│  │ (Phase 4-04)   │
       └─────────┬──────────┘  └──────────────┘  └────────┬───────┘
                 │                                        │
       ┌─────────┴──────────┐                  ┌──────────┴───────┐
       │                    │                  ▼                  ▼
       ▼                    ▼          ┌────────────────┐  ┌────────────┐
┌──────────────┐  ┌─────────────────┐  │BetoAccessibility│  │ Gesture    │
│DeterministicM│  │ Gemini LlmClient│  │ Service (eyes) │  │ Overlay    │
│(Plan C reglas)│  │ + sanitizer    │  │  findNodeBy*   │  │ (flecha)   │
└──────────────┘  └────────┬────────┘  └────────────────┘  └────────────┘
                           │
                  ┌────────┼────────┐
                  ▼        ▼        ▼
        ┌─────────────┐ ┌────────┐ ┌────────────┐
        │ContactRepo  │ │UserMem │ │IntentBranch│
        │(Contacts.AC)│ │(EncSP) │ │(WhatsApp/  │
        │             │ │aliases │ │ SMS/Call/  │
        │             │ │+canal+ │ │ Maps)      │
        │             │ │facts)  │ │            │
        └─────────────┘ └────────┘ └────────────┘
```

### Sistema nervioso: `AgentBus`
Bus reactivo basado en `SharedFlow` que desacopla servicios, UI, voz y motor de acciones. Dos tipos de mensajes:
- **`AgentEvent`** — hechos que ya ocurrieron (`BubbleTapped`, `VoiceCaptured`, `TtsStarted`, `IntentLaunched`, `GuideStarted`, `ToolFailed`, etc.).
- **`AgentCommand`** — instrucciones (`Speak`, `StartVoiceCapture`, `OpenCompanion`).

### Componentes core

| Capa | Componentes |
|---|---|
| **Servicios Android** | `BetoForegroundService` (vida + TTS + dispatch), `BetoAccessibilityService` (lectura de pantalla + `findNodeByText/findNodeByContentDescription`) |
| **Voz** | `TtsManager` (TTS nativo + `VoiceSelector` masculino-neural-AR + `speakAndAwait` suspend), `VoiceCaptureActivity` (STT + `SttCorrector` LLM-based), `RecognizerFactory` (on-device ↔ cloud-backed) |
| **LLM** | `LlmClient` interface + `GeminiLlmClient` (Firebase AI), `Sanitizer` (regex DNI/tel/tarjeta), `LlmCache` (LRU SHA-256), `PromptBuilder` (system prompts + few-shots argentinos), `Decision` sealed type con allow-list |
| **Acciones** | `ActionDispatcher` (orquestador), `ActionRouter`, `IntentBranch` (WhatsApp/SMS/llamada/Maps), `ContactClarifier` (*"¿quién es tu nieto?"*), `ChannelClarifier` (*"¿por WhatsApp o SMS?"*), `DeterministicMatcher` (Plan C) |
| **Memoria** | `UserMemoryStore` (`EncryptedSharedPreferences` + `Mutex` + `StateFlow`), `UserMemory` data class (aliases, channelPrefs, profile facts) |
| **Contactos** | `ContactRepository` (`ContactsContract` con `ContactDataSource` testeable), `ContactInfo` + `PhoneNumber` + `String.toE164()` |
| **Burbuja** | `OverlayManager` (state machine), `OverlayBubble` (drag/tap/long-press), `BubbleState` enum (5 estados), `BubbleStateController` (anim + tint + ícono) |
| **Compañero** | `CompanionActivity` Compose, `CompanionViewModel`, `CompanionLlmClient` (Flash Lite chat + fact extraction), `CompanionSheet` UI |
| **Guía con gestos** | `GuideController` (orquestador de scripts), `GuideScripts` (5 acciones curadas), `GestureOverlay` (flecha animada), `GestureOverlayManager` (WindowManager) |
| **UI tokens** | `BetoTheme` Compose (tipografía senior ≥22sp, paleta WCAG AA), tokens en `colors.xml` / `dimens.xml` |

### Decisiones técnicas relevantes

| Decisión | Por qué |
|---|---|
| Android nativo (no React Native / Flutter) | `AccessibilityService` es API Android profunda — wrappers agregan bridges frágiles. |
| Burbuja con Views clásicas (no Compose) | Compose en Service requiere `ViewTreeLifecycleOwner` manual. Compañero sí es Compose porque vive en su Activity. |
| Voz masculina-neural-AR enforced | Persona del producto exige voz de "amigo argentino". `VoiceSelector` filtra explícito. |
| Frases LLM-generated (no PhraseBank fijo) | Tono más natural + variaciones contextuales. Cache LRU mitiga latencia, fallbacks hardcoded cubren red caída. |
| Modo Guía con scripts curados (no LLM agéntico) | El roadmap viejo planteaba un loop agéntico genérico — alto riesgo, baja confiabilidad. Scripts curados v1 cubren 5 acciones core con flecha animada confiable. |
| Memoria con `EncryptedSharedPreferences` + JSON (no Room) | Hackathon scope. Atómico, encriptado, sin migration overhead. |
| Privacidad: sanitización por regex (no NER on-device) | NER suma 2-3 días. Regex cuenta la historia. |
| LLM: Gemini 2.5 Flash vía Firebase AI Logic | Único SDK Android oficial que funciona desde el cliente sin backend. Free tier sin tarjeta. Compañero usa Flash Lite (más barato + rápido para chat). |
| `minSdk 31` | Permite `createOnDeviceSpeechRecognizer()` y `EncryptedSharedPreferences` modernos. |

Profundización completa en [`CLAUDE.md`](./CLAUDE.md). Plans por phase en [`.planning/phases/`](./.planning/phases/).

---

## Stack tecnológico

| Capa | Tecnología | Versión |
|---|---|---|
| Lenguaje | Kotlin | 2.1.10 |
| Build | Gradle / AGP | 8.10 / 8.7.3 |
| Plataforma | Android | API 31–34 (`minSdk` 31, `targetSdk` 34) |
| LLM (acciones) | Gemini 2.5 Flash | vía `firebase-ai` (Firebase BoM 34.7.0) |
| LLM (Compañero) | Gemini 2.5 Flash Lite | mismo SDK |
| UI declarativa | Jetpack Compose | BOM 2025.04.00 + Material3 |
| Voz STT | `android.speech.SpeechRecognizer` | nativo, `es-AR`, on-device API 31+ |
| Voz TTS | `android.speech.tts.TextToSpeech` | nativo, voz masculina neural argentina |
| Async | Kotlinx Coroutines + Flow | 1.8.1 |
| Serialización | Kotlinx Serialization JSON | 1.7.3 |
| Persistencia segura | `androidx.security:security-crypto` | 1.1.0-alpha07 |
| Logging | Timber | 5.0.1 |
| Tests | JUnit 4 + kotlinx-coroutines-test | 4.13.2 / 1.8.1 |
| Java desugaring | `desugar_jdk_libs` | 2.1.2 |

Catálogo completo en [`android/gradle/libs.versions.toml`](./android/gradle/libs.versions.toml).

---

## Estructura del proyecto

```text
platanus-hack-26-ar-team-12/
├── android/                                          # Proyecto Android Studio
│   ├── app/
│   │   ├── build.gradle.kts                          # Config del módulo (deps, SDKs, Compose, desugaring)
│   │   ├── google-services.json                      # Config Firebase (Gemini AI Logic)
│   │   ├── proguard-rules.pro                        # Reglas R8/ProGuard (release sin minify)
│   │   └── src/
│   │       ├── main/
│   │       │   ├── AndroidManifest.xml               # Permisos, Activities, Services, Companion
│   │       │   ├── java/com/beto/app/
│   │       │   │   ├── BetoApplication.kt            # Boot: Timber + TTS + UserMemoryStore + PhraseGenerator
│   │       │   │   ├── MainActivity.kt               # Pre-flight permisos + onboarding READ_CONTACTS
│   │       │   │   ├── action/                       # Motor de acciones (Phase 3 capstone)
│   │       │   │   │   ├── ActionCollaborators.kt    # Speaker + SuspendableVoiceCapture (interfaces)
│   │       │   │   │   ├── ActionDispatcher.kt       # Orquestador: Plan C → LLM → clarificar → ejecutar
│   │       │   │   │   ├── ActionRouter.kt           # Lógica pura de routing (sin Android deps)
│   │       │   │   │   ├── ChannelClarifier.kt       # "¿WhatsApp, SMS o llamada?" + guarda preferencia
│   │       │   │   │   ├── ContactClarifier.kt       # "¿Quién es tu nieto?" + guarda alias
│   │       │   │   │   ├── DemoContacts.kt           # Fallback hardcoded si no hay permiso de contactos
│   │       │   │   │   ├── DeterministicMatcher.kt   # Plan C — regex offline garantizado
│   │       │   │   │   └── IntentBranch.kt           # WhatsApp / make_call / send_sms / open_maps
│   │       │   │   ├── bus/                          # Sistema nervioso
│   │       │   │   │   ├── AgentBus.kt               # SharedFlow singleton
│   │       │   │   │   └── AgentEvents.kt            # AgentEvent + AgentCommand sealed
│   │       │   │   ├── companion/                    # Modo Compañero (Phase 4-03)
│   │       │   │   │   ├── CompanionActivity.kt      # Activity transparente + Compose
│   │       │   │   │   ├── CompanionLlmClient.kt     # Gemini Flash Lite + fact extraction
│   │       │   │   │   ├── CompanionMessage.kt       # Data classes
│   │       │   │   │   ├── CompanionSheet.kt         # Compose ModalBottomSheet
│   │       │   │   │   └── CompanionViewModel.kt     # State + opt-in fact recording
│   │       │   │   ├── contacts/                     # Acceso a libreta del sistema (Phase 3-03)
│   │       │   │   │   ├── ContactInfo.kt            # Data classes + toE164()
│   │       │   │   │   └── ContactRepository.kt      # ContactsContract + DataSource testeable
│   │       │   │   ├── guide/                        # Modo Guía con gestos (Phase 4-04)
│   │       │   │   │   ├── GestureOverlay.kt         # View con flecha animada
│   │       │   │   │   ├── GuideController.kt        # Orquestador de scripts
│   │       │   │   │   └── GuideScripts.kt           # 5 acciones curadas v1
│   │       │   │   ├── llm/                          # Cerebro Gemini (Phase 3-01)
│   │       │   │   │   ├── GeminiLlmClient.kt        # Firebase AI + retry + cache
│   │       │   │   │   ├── LlmCache.kt               # LRU thread-safe
│   │       │   │   │   ├── LlmClient.kt              # Interface
│   │       │   │   │   ├── LlmModels.kt              # Decision sealed + DecisionJson allow-list
│   │       │   │   │   ├── PromptBuilder.kt          # System prompts es-AR + few-shots
│   │       │   │   │   ├── Sanitizer.kt              # Regex DNI / tel AR / tarjeta
│   │       │   │   │   └── ToolDescriptors.kt        # send_whatsapp, make_call, send_sms,
│   │       │   │   │                                 # open_maps, show_how_to
│   │       │   │   ├── memory/                       # Memoria del usuario (Phase 3-04)
│   │       │   │   │   ├── UserMemory.kt             # data class serializable
│   │       │   │   │   └── UserMemoryStore.kt        # EncryptedSharedPreferences + Mutex
│   │       │   │   ├── overlay/                      # Burbuja flotante (Phase 1 + 4-02)
│   │       │   │   │   ├── BubbleState.kt            # Enum 5 estados + transiciones legales
│   │       │   │   │   ├── BubbleStateController.kt  # Tint + ícono + animación
│   │       │   │   │   ├── OverlayBubble.kt          # Drag + tap + long-press + magnet
│   │       │   │   │   └── OverlayManager.kt         # State machine + AgentBus listener
│   │       │   │   ├── service/
│   │       │   │   │   ├── BetoAccessibilityService.kt # findNodeByText / findNodeByContentDescription
│   │       │   │   │   └── BetoForegroundService.kt    # Vida + TTS + dispatcher wiring
│   │       │   │   ├── ui/
│   │       │   │   │   └── BetoTheme.kt              # Compose theme senior (≥22sp + WCAG AA)
│   │       │   │   ├── util/
│   │       │   │   │   ├── LogTags.kt                # Tags Timber (Beto-LLM, Beto-Memory, etc.)
│   │       │   │   │   └── PreflightCheck.kt         # Validación de permisos críticos
│   │       │   │   └── voice/
│   │       │   │       ├── PhraseFallbacks.kt        # Frases hardcoded para cuando red cae
│   │       │   │       ├── PhraseGenerator.kt        # LLM phrases con quality checks (no "usted")
│   │       │   │       ├── PhraseIntent.kt           # Tipos para PhraseGenerator
│   │       │   │       ├── RecognizerFactory.kt      # On-device vs cloud-backed STT
│   │       │   │       ├── SttCorrector.kt           # Corrige transcripts ambiguos via LLM
│   │       │   │       ├── TtsManager.kt             # TTS + VoiceSelector + speakAndAwait
│   │       │   │       ├── VoiceCaptureActivity.kt   # STT + corrector
│   │       │   │       └── VoiceSelector.kt          # Selecciona mejor voz masculina-neural-AR
│   │       │   └── res/
│   │       │       ├── drawable/                     # bubble_*, ic_state_*, guide_arrow, etc.
│   │       │       ├── layout/overlay_bubble.xml     # Burbuja con ring + logo circular + state icon
│   │       │       ├── values/colors.xml             # Paleta 5 estados + high-contrast
│   │       │       ├── values/dimens.xml             # Tipografía senior + bubble dims
│   │       │       ├── values/strings.xml            # Textos en es-AR
│   │       │       ├── values/styles.xml             # Theme.Beto.Transparent
│   │       │       └── xml/accessibility_service_config.xml
│   │       └── test/                                 # 128 tests JVM (JUnit 4 + coroutines-test)
│   │           └── java/com/beto/app/
│   │               ├── action/{ActionRouter,ChannelClarifier,ContactClarifier,
│   │               │            DeterministicMatcher,IntentBranch}Test.kt
│   │               ├── companion/CompanionViewModelTest.kt
│   │               ├── contacts/ContactRepositoryTest.kt
│   │               ├── guide/GuideScriptsTest.kt
│   │               ├── llm/{LlmCache,PromptBuilder,Sanitizer}Test.kt
│   │               ├── memory/{UserMemory,UserMemoryStore}Test.kt
│   │               ├── overlay/BubbleStateTest.kt
│   │               └── voice/{PhraseGenerator,SttCorrector,VoiceSelector}Test.kt
│   ├── gradle/
│   │   ├── libs.versions.toml                        # Catálogo de versiones (single source of truth)
│   │   └── wrapper/                                  # Gradle Wrapper
│   ├── build.gradle.kts                              # Config raíz del proyecto Android
│   ├── gradle.properties                             # Flags (AndroidX, JVM args)
│   ├── settings.gradle.kts                           # Repos y módulos
│   ├── gradlew / gradlew.bat                         # Scripts del wrapper
│   └── .gitignore                                    # /build, local.properties, etc.
├── docs/
│   ├── STATUS.md                                     # Estado vivo del proyecto
│   └── 03-DEMO-CHECK.md                              # Smoke test E2E checklist
├── .planning/                                        # Roadmap, requirements, plans (GSD workflow)
│   ├── PROJECT.md                                    # Visión + capabilities
│   ├── ROADMAP.md                                    # 7 phases
│   ├── REQUIREMENTS.md                               # 70 requirements trazados
│   ├── STATE.md                                      # Estado actual del milestone
│   └── phases/0[1-7]-*/                              # Plans + summaries por phase
├── .github/                                          # GSD framework + agentes
├── CLAUDE.md                                         # Reglas globales y stack detallado
├── GEMINI.md                                         # Resumen orientado a Gemini CLI
├── README.md                                         # Este archivo
├── project-logo.png                                  # Logo (1000×1000, requisito submission)
├── platanus-hack-project.json                        # Metadata para submission
└── .gitignore                                        # IDEs, builds, .env, .claude/skills locales
```

---

## Instalación paso a paso

> **Importante:** Beto está pensado para correr en un **dispositivo físico Android 12+**. El emulador no soporta bien `AccessibilityService` ni los overlays flotantes. Si solo querés ver el código, igual podés clonar y compilar — pero para usarlo necesitás un teléfono.

### Paso 1 — Instalá Android Studio

Si no lo tenés:

1. Andá a [developer.android.com/studio](https://developer.android.com/studio).
2. Bajá **Android Studio Ladybug Feature Drop** (o más reciente).
3. Instalalo y abrilo. La primera vez tarda 5-10 minutos descargando el Android SDK.
4. Cuando te pregunte qué SDK descargar, asegurate de que esté **Android 14 (API 34)** marcado.

> El JDK lo gestiona Gradle Wrapper automáticamente — no hace falta que instales Java aparte.

### Paso 2 — Cloná el repositorio

Abrí una terminal y ejecutá:

```bash
git clone https://github.com/platanus-hack-26/platanus-hack-26-ar-team-12.git
cd platanus-hack-26-ar-team-12
```

### Paso 3 — Configurá Firebase (necesario para el LLM)

Beto usa **Gemini** vía Firebase AI Logic. Necesitás un proyecto Firebase propio:

1. Andá a [console.firebase.google.com](https://console.firebase.google.com/) y creá un proyecto nuevo (o usá uno existente).
2. Dentro del proyecto, agregá una **app Android** con `applicationId = com.beto.app`.
3. Cuando te dé el `google-services.json`, descargalo y reemplazá el archivo en `android/app/google-services.json`.
4. En el menú de Firebase, andá a **Build → AI Logic → Get Started** y habilitá **Gemini Developer API** (free tier sin tarjeta de crédito alcanza para la demo).

> Si NO querés usar el LLM (solo el Plan C offline funciona): la app igual compila, pero los comandos que requieran Gemini no funcionarán. El comando hero del guion (*"mandale a mi nieto que ya llegué"*) funciona offline porque tiene un matcher determinista de respaldo.

### Paso 4 — Conectá el teléfono

1. En el teléfono Android: andá a **Ajustes → Acerca del teléfono** y **tocá 7 veces el "Número de compilación"**. Aparece un mensaje "Ya eres desarrollador".
2. Volvé a **Ajustes → Sistema → Opciones de desarrollador** y activá:
   - **Depuración USB**
   - **Instalar via USB** (en algunos teléfonos)
3. Conectá el teléfono a la computadora con cable USB. Cuando aparezca el diálogo *"Permitir depuración USB?"* en el teléfono, tocá **Permitir**.
4. Para verificar la conexión, en la terminal:
   ```bash
   adb devices
   ```
   Deberías ver tu teléfono listado. Si dice `unauthorized`, revocá los permisos de depuración en Opciones de desarrollador y reconectá.

### Paso 5 — Compilá e instalá

En la terminal, dentro de la carpeta `android/`:

```bash
cd android

# Compilá el APK debug
./gradlew assembleDebug

# Instalalo en el teléfono conectado
./gradlew installDebug
```

La primera vez tarda 3-5 minutos descargando dependencias. El APK final queda en `android/app/build/outputs/apk/debug/app-debug.apk`.

### Paso 6 — Otorgá permisos en el teléfono

Beto necesita **5 permisos** especiales. La primera vez que abrís la app, `MainActivity` te lleva paso a paso. Si querés hacerlo manualmente o re-validarlo:

| # | Permiso | Cómo activarlo | Por qué |
|---|---|---|---|
| 1 | **Mostrar sobre otras apps** | Ajustes → Aplicaciones → Beto → "Mostrar sobre otras apps" → **Permitir** | Para la burbuja flotante |
| 2 | **Servicio de accesibilidad** | Ajustes → Accesibilidad → Servicios instalados → **Beto** → Activar | Para "ver" la pantalla y dibujar la flecha en Modo Guía |
| 3 | **Micrófono** | Se solicita la primera vez que tocás la burbuja | STT |
| 4 | **Contactos** | Aparece al primer arranque (botón "Dar permiso"). Si saltás, Beto usa contactos demo. | Resolver "llamá a Pedro" en tu libreta real |
| 5 | **Sin restricciones de batería** | Ajustes → Apps → Beto → Batería → **Sin restricciones** | Que el Foreground Service no muera en background |

### Paso 7 — Verificá la voz

Beto necesita una voz **masculina argentina** (o lo más parecido). Si tu teléfono no la tiene:

1. **Ajustes → Idioma e introducción → Voz → Configuración del motor TTS de Google → Idioma → Español (Latinoamérica)**.
2. Si hay opción de descargar voz masculina, descargala. Si no, instalá el motor TTS de Google desde el Play Store si no lo tenés.

Para verificar qué voz eligió Beto, mirá el log:

```bash
adb logcat -s "Beto-TTS:D"
```

Deberías ver al boot:

```
VOICE_SELECTED name=es-mx-x-esd-network locale=es_MX likelyMale=true
```

Si dice `likelyMale=false`, la voz que el teléfono ofreció es femenina. Probá descargar otra desde Settings o agregar el ID al `KNOWN_MALE_IDS` de `VoiceSelector.kt` y recompilar.

### Paso 8 — Verificá que funciona

```bash
# Logs filtrados por todos los tags de Beto
adb logcat -s "Beto-Accessibility:D" "Beto-LLM:D" "Beto-Action:D" "Beto-TTS:D" "Beto-STT:D" "Beto-Intent:D" "Beto-Memory:D"
```

Tocá la burbuja y decí *"mandale a mi nieto que ya llegué"*. Deberías ver:

```
PLAN_C_STT_START
PLAN_C_STT_RESULT elapsedMs=...
PLAN_C_MATCHED
PLAN_C_WHATSAPP_LAUNCHED
DISPATCH_EXECUTED tool=send_whatsapp
```

Y WhatsApp se abre con el mensaje pre-llenado. Listo, Beto está funcionando.

### Troubleshooting

| Problema | Solución |
|---|---|
| `./gradlew` "command not found" | Estás en la carpeta equivocada. Tenés que estar en `android/`. |
| Build falla con "google-services.json missing" | Revisá el Paso 3. Sin Firebase configurado, el Gradle plugin se queja. Podés desactivar el plugin temporalmente para compilar sin LLM. |
| La burbuja no aparece tras instalar | Revisá Paso 6 — falta el permiso de overlay. |
| El recognizer dice "error 7" o no escucha | El recognizer on-device no está disponible en tu device para `es-AR`. La app cae automáticamente al cloud-backed después de un timeout. Asegurate de tener red. |
| Dice voz femenina aunque haya masculina disponible | Bajá una voz masculina vía Settings (Paso 7) y revisá el log `VOICE_AVAILABLE` para ver el ID exacto que usa el TTS engine. |
| La flecha del Modo Guía aparece en otro lado | Los selectores son específicos por versión de WhatsApp. Si la versión de tu device es muy distinta a la testeada, los selectores en `GuideScripts.kt` necesitan actualizarse. |

---

## Cómo usar Beto

Una vez instalado y con permisos:

1. **Tocá la burbuja flotante** que queda visible sobre cualquier app. Beto entra en estado **listening** (azul, mic).

2. **Hablá natural** en español argentino. Algunos ejemplos:

   | Tipo | Comando | Qué pasa |
   |---|---|---|
   | Mensaje | *"Mandale a mi nieto que ya llegué"* | Si es la primera vez, pregunta *"¿quién es tu nieto?"* y guarda. Manda WhatsApp. |
   | Llamada | *"Llamá a Pedro"* | Resuelve "Pedro" en la libreta del sistema y abre el dialer. |
   | Maps | *"Abrime el mapa hasta la farmacia"* | Lanza Google Maps con la búsqueda. |
   | Multi-canal | *"Mandale a Juan que pase por casa"* | La primera vez pregunta *"¿por WhatsApp, SMS o llamada?"*. Guarda preferencia. |
   | **Guía** | *"¿Cómo mando un audio por WhatsApp?"* | Abre WhatsApp si no estás ahí, dibuja flecha animada sobre el botón micrófono y te explica con voz. |

3. **Long-press en la burbuja** (mantener apretada 0.6s) abre el **Modo Compañero** — un chat para charlar con Beto sin pedirle tareas. *"¿Cómo estás?"* / *"Me gusta el tango"* / etc. Si decís algo personal (gusto, familia), Beto te pregunta *"¿Querés que me acuerde?"* — solo guarda con tu confirmación explícita.

4. **Arrastrar la burbuja** hacia el centro inferior de la pantalla la cierra (Beto se apaga). Para volver a abrirla, abrí la app desde el launcher.

> Beto **nunca auto-envía**: WhatsApp / SMS se abren con el mensaje pre-llenado y vos tocás Enviar. Es decisión deliberada para que el usuario tenga control final.

---

## Desarrollo

### Convenciones de código

- **Tono del agente** (system prompts y TTS): vocabulario simple, frases cortas, **voseo argentino** ("decime", "tenés", "dale"). **Prohibido** "usted/ustedes" — `PhraseGenerator` rechaza outputs que lo contengan.
- **Modularidad estricta:** servicios Android, lógica de UI y llamadas LLM en paquetes separados.
- **Privacidad por construcción:** el `Sanitizer` se aplica DENTRO del `LlmClient`, no en callers — garantía estructural de que nada sensible sale.
- **Sin sobre-ingeniería:** este repo es para una demo de hackathon. No agregamos abstracciones para casos hipotéticos.

### Logs

Logging centralizado vía Timber. Tags definidos en [`util/LogTags.kt`](./android/app/src/main/java/com/beto/app/util/LogTags.kt):

| Tag | Para qué |
|---|---|
| `Beto-Accessibility` | Parsing de pantalla, gestos, Modo Guía |
| `Beto-LLM` | Prompts, tool calls, responses, fact extraction |
| `Beto-Action` | ActionDispatcher, routing, clarifications |
| `Beto-Intent` | Intents lanzados (WhatsApp, dialer, Maps) |
| `Beto-STT` | SpeechRecognizer, corrector |
| `Beto-TTS` | TextToSpeech, VoiceSelector |
| `Beto-Memory` | Operaciones sobre UserMemoryStore |
| `Beto-Bus` | Eventos del AgentBus (alta verbosidad) |

Marcadores de log estables (estables = no rotan, podés grepearlos):

```
DISPATCH_START
DISPATCH_PLANC_HIT
DISPATCH_LLM_DECISION tool=send_whatsapp
DISPATCH_CLARIFY_CONTACT alias=nieto resolved=12345
DISPATCH_CLARIFY_CHANNEL contact=12345 channel=WHATSAPP
DISPATCH_EXECUTED tool=send_whatsapp
DISPATCH_FAILED reason=...
INTENT_LAUNCHED tool=send_whatsapp package=com.whatsapp
PHRASE_CACHE_HIT intent=CONFIRM_WHATSAPP
PHRASE_GENERATED intent=SUCCESS_CALL
VOICE_SELECTED name=es-mx-x-esd-network likelyMale=true
VOICE_AVAILABLE name=... genderScore=...
MEMORY_UPDATED key=alias
GUIDE_STARTED action=SEND_WHATSAPP_AUDIO
GUIDE_OVERLAY_SHOWN target=(...)
BUBBLE_STATE_CHANGED from=LISTENING to=THINKING
```

### Tests

```bash
# Todos los unit tests JVM (128 tests)
./gradlew testDebugUnitTest

# Solo un paquete específico
./gradlew testDebugUnitTest --tests "com.beto.app.companion.*"

# Build verification
./gradlew assembleDebug

# Reporte HTML de tests (más legible)
open android/app/build/reports/tests/testDebugUnitTest/index.html
```

Coverage actual:

| Paquete | Tests |
|---|---|
| `action` | 24 (router, clarifiers, matcher, intent, dispatcher) |
| `companion` | 9 (ViewModel + fact opt-in) |
| `contacts` | 9 (repository + permission flow) |
| `guide` | 10 (scripts validation) |
| `llm` | 14 (cache, prompts, sanitizer) |
| `memory` | 12 (model + store) |
| `overlay` | 14 (state machine) |
| `voice` | 36 (selector + corrector + phrase generator) |
| **Total** | **128 verdes** |

### Privacidad

Sanitización mínima on-device antes de enviar payloads al LLM (en `llm/Sanitizer.kt`):

- DNI argentino (7-8 dígitos word-boundary)
- Teléfonos AR (`+54...` o 10-11 dígitos locales)
- Tarjetas (16 dígitos con/sin separadores)

Memoria persistida en `EncryptedSharedPreferences` con `MasterKey AES-256 GCM`.

Profile facts del Compañero **solo se guardan con confirmación explícita** del usuario (botón Sí/No tras cada extracción).

NER on-device queda fuera de scope para hackathon — regex cuenta la historia.

---

## Roadmap

| Phase | Estado | Alcance |
|---|---|---|
| **1 — Foundation** | ✓ Completa | `AgentBus`, burbuja, Foreground Service, TTS, permisos, logs |
| **2 — Plan C Offline** | ✓ Completa | STT `es-AR` + matcher determinista + Intent WhatsApp end-to-end, sin red |
| **3 — Cerebro IA + Memoria + Multi-canal** | ✓ Completa | Gemini client + sanitizer + STT corrector + acceso a contactos del sistema + memoria persistida + router multi-canal con aprendizaje |
| **4 — Voz humana + UX senior + Compañero + Guía con gestos** | ✓ Completa | TTS neural masculino-AR + frases LLM-generated + 5 estados burbuja + tipografía senior + chat Compañero + flecha visual sobre target |
| **5 — Anti-fraude reactivo** | Pendiente | Tool `analyze_for_fraud` + UX *"Beto, ¿esto es estafa?"* sobre último SMS o screenshot compartido |
| **6 — Activación rápida (opcional)** | Pendiente | Mantener volumen-down 2s + spike de wake word *"Hola Beto"* |
| **7 — Demo Readiness** | Pendiente | APK frozen 4h+ antes, hot-spare phone, hotspot dedicado, guion ensayado 5x, video respaldo, submission |

Detalle vivo en [`docs/STATUS.md`](./docs/STATUS.md). Plans por phase en [`.planning/phases/`](./.planning/phases/).

---

## Equipo

team-12 — Buenos Aires, Platanus Hack 26

| Integrante | GitHub |
|---|---|
| Francisco Iturain | [@franiturain](https://github.com/franiturain) |
| Mateo Buela | [@MateoBD](https://github.com/MateoBD) |
| Nahuel Prado | [@NaPrado](https://github.com/NaPrado) |
| Matias Sanchez Novelli | [@MatiNovelli](https://github.com/MatiNovelli) |
| Enzo Canelo | [@enzocanelo](https://github.com/enzocanelo) |

---

<div align="center">

**Track:** Vertical AI · **Hackathon:** Platanus Hack 26 · **Buenos Aires, Argentina**

</div>
