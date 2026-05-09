<div align="center">

<img src="./project-logo.png" alt="Beto — Logo" width="180" />

# Beto

**Un copiloto de IA, paciente y cálido, que opera el celular por vos.**

*Platanus Hack 26 — Track Vertical AI — team-12 (Buenos Aires)*

[![Platform](https://img.shields.io/badge/platform-Android%2012%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.10-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![LLM](https://img.shields.io/badge/LLM-Gemini%202.5%20Flash-4285F4?logo=google&logoColor=white)](https://ai.google.dev/)
[![Status](https://img.shields.io/badge/status-MVP%20Plan%20C%20Operativo-success)](./docs/STATUS.md)

</div>

---

## Resumen rápido

Beto es una app Android nativa pensada para **adultos mayores hispanohablantes**. En lugar de tocar menús complicados, el usuario habla en español argentino — *"avisale a mi nieto que ya llegué"* — y Beto entiende, confirma con voz cálida y ejecuta la acción en el celular (mandar un WhatsApp, llamar, abrir Maps).

Funciona como una **burbuja flotante** sobre cualquier app: se toca, se escucha, se actúa. Por debajo combina `AccessibilityService` para "ver" la pantalla, `SpeechRecognizer` nativo para escuchar en `es-AR`, `TextToSpeech` para responder, y un motor híbrido que prefiere caminos confiables (Intents directos) sobre caminos impresionantes pero frágiles (loop agéntico con LLM).

> Proyecto de hackathon (24-36 h). El foco está en una **demo en vivo robusta**, no en cobertura exhaustiva.

---

## Tabla de contenidos

1. [Producto](#producto)
2. [Demo y estado actual](#demo-y-estado-actual)
3. [Arquitectura](#arquitectura)
4. [Stack tecnológico](#stack-tecnológico)
5. [Estructura del proyecto](#estructura-del-proyecto)
6. [Instalación y puesta en marcha](#instalación-y-puesta-en-marcha)
7. [Uso](#uso)
8. [Desarrollo](#desarrollo)
9. [Roadmap](#roadmap)
10. [Equipo](#equipo)

---

## Producto

### El problema
Las personas mayores quedan afuera de la mensajería, los pagos digitales y la navegación porque las interfaces no fueron pensadas para ellas: tipografías chicas, menús anidados, jerga técnica. Hoy dependen de un nieto, un hijo o un vecino para tareas simples.

### La propuesta
Beto es un **agente multimodal** que reemplaza la interacción táctil por **voz natural en español argentino**. El usuario no aprende botones — le habla al celular como le hablaría a una persona.

### Caso de uso central (lo que prueba la tesis)
> *"Beto, mandale a mi nieto que ya llegué."*

Beto reconoce la intención, resuelve el contacto, abre WhatsApp con el mensaje pre-llenado y le dice al usuario: *"Listo, te dejé el mensaje preparado para tu nieto."*. Cero toques adicionales.

### Principios de diseño
- **Confiabilidad sobre sofisticación.** Si un Intent directo resuelve la tarea, se usa Intent. El LLM es fallback, no el camino feliz.
- **Tono cálido y simple.** Vocabulario llano, frases cortas, voseo argentino.
- **Privacidad on-device** primero. Sanitización con regex de DNI / teléfono / tarjeta antes de cualquier llamada al LLM.
- **Fallbacks elegantes.** Si la wake word falla, está la burbuja. Si la red cae, queda el matcher determinista.

---

## Demo y estado actual

**Estado global:** vertical slice **Plan C** operativa offline. Listo para integrar el LLM (Phase 3).

Lo que hoy funciona end-to-end, sin red, sin LLM:

| Capacidad | Detalle |
|---|---|
| Burbuja flotante | Drag, magnet a borde, tap dispara captura de voz. |
| STT en `es-AR` | `SpeechRecognizer` nativo, transcript final en < 3 s. |
| Matcher determinista | Reconoce la familia *"mandale / avisale / decile a mi nieto que…"* sin LLM. |
| Acción WhatsApp | Abre `com.whatsapp` con el mensaje pre-llenado al contacto demo. |
| TTS cálido | Confirma antes de actuar y reporta éxito o fallo en `es-AR`. |
| Foreground Service | Notificación persistente, no muere en background. |
| Onboarding de permisos | Lleva al usuario a habilitar overlay y accesibilidad si faltan. |

Estado vivo y detallado en [`docs/STATUS.md`](./docs/STATUS.md).

---

## Arquitectura

Beto usa una arquitectura **desacoplada y orientada a eventos**, optimizada para servicios de fondo en Android.

```text
                          ┌──────────────────────┐
                          │   OverlayBubble      │  (UI flotante, SYSTEM_ALERT_WINDOW)
                          └──────────┬───────────┘
                                     │ tap
                                     ▼
┌──────────────────────┐    ┌──────────────────────┐    ┌──────────────────────┐
│ BetoForeground       │◄──►│      AgentBus        │◄──►│ VoiceCaptureActivity │
│ Service (TTS, vida)  │    │  (Kotlin SharedFlow) │    │   (STT es-AR)        │
└──────────┬───────────┘    └──────────┬───────────┘    └──────────────────────┘
           │                            │
           ▼                            ▼
┌──────────────────────┐    ┌──────────────────────┐
│ BetoAccessibility    │    │  PlanCController     │
│ Service (ojos)       │    │  (orquestador)       │
└──────────────────────┘    └──────────┬───────────┘
                                       │
                       ┌───────────────┼───────────────┐
                       ▼               ▼               ▼
              ┌────────────────┐ ┌──────────┐ ┌────────────────┐
              │ Deterministic  │ │  Demo    │ │  IntentBranch  │
              │ Matcher        │ │ Contacts │ │  (WhatsApp)    │
              └────────────────┘ └──────────┘ └────────────────┘
```

### Sistema nervioso: `AgentBus`
Un bus reactivo basado en `SharedFlow` que desacopla los servicios, la UI, la captura de voz y el motor de acciones. Define dos tipos de mensajes:
- **`AgentEvent`** — hechos que ya ocurrieron (`BubbleTapped`, `VoiceCaptured(text)`, `ActionExecuted`, `ToolFailed`).
- **`AgentCommand`** — instrucciones (`Speak`, `StartListening`).

### Componentes core
- **`BetoForegroundService`** — Mantiene el proceso vivo y coordina el TTS.
- **`BetoAccessibilityService`** — Los "ojos" de Beto. Esqueleto registrado; uso completo llega en Phase 4.
- **`OverlayManager` + `OverlayBubble`** — Burbuja flotante (View clásico, no Compose, decisión deliberada para evitar fricción con `ViewTreeLifecycleOwner`).
- **`VoiceCaptureActivity`** — Activity transparente que hostea el recognizer nativo y emite el transcript al bus.
- **`TtsManager`** — Wrapper de `TextToSpeech` configurado en `es-AR` con fallback a `es-ES`.
- **`PlanCController`** — Orquesta el flujo completo `voz → match → confirmación → Intent → reporte`.

### Decisiones técnicas relevantes
| Decisión | Por qué |
|---|---|
| Android nativo (no React Native / Flutter) | `AccessibilityService` es una API Android profunda; cualquier wrapper agrega bridges frágiles. |
| Burbuja con Views clásicas (no Compose) | Compose dentro de un Service requiere `ViewTreeLifecycleOwner` manual — overhead innecesario en sprint corto. |
| LLM: Gemini 2.5 Flash vía Firebase AI Logic | Único SDK oficial Android que funciona desde el cliente sin backend propio. Free tier sin tarjeta, multimodal, tool calling robusto. |
| STT/TTS nativos | Gratis, en `es-AR`, sin dependencias externas. |
| `minSdk 31` | El teléfono de demo es moderno; permite `createOnDeviceSpeechRecognizer()` y simplifica overlays. |

Profundización completa de stack y trade-offs en [`CLAUDE.md`](./CLAUDE.md).

---

## Stack tecnológico

| Capa | Tecnología | Versión |
|---|---|---|
| Lenguaje | Kotlin | 2.1.10 |
| Build | Gradle / AGP | 8.10 / 8.7.3 |
| Plataforma | Android | API 31–34 (`minSdk` 31, `targetSdk` 34) |
| LLM | Gemini 2.5 Flash | vía `firebase-ai` (Firebase BoM 34.7.0) |
| Voz (STT) | `android.speech.SpeechRecognizer` | nativo, `es-AR` |
| Voz (TTS) | `android.speech.tts.TextToSpeech` | nativo, `es-AR` |
| Async | Kotlinx Coroutines + Flow | 1.8.1 |
| Serialización | Kotlinx Serialization JSON | 1.7.3 |
| Logging | Timber | 5.0.1 |
| Tests | JUnit 4 | 4.13.2 |
| Java desugaring | `desugar_jdk_libs` | 2.1.2 |

Catálogo completo en [`android/gradle/libs.versions.toml`](./android/gradle/libs.versions.toml).

---

## Estructura del proyecto

```text
platanus-hack-26-ar-team-12/
├── android/                              # Proyecto Android Studio
│   ├── app/
│   │   ├── build.gradle.kts              # Config del módulo app (deps, SDKs, desugaring)
│   │   ├── google-services.json          # Config Firebase (Gemini AI Logic)
│   │   ├── proguard-rules.pro            # Reglas R8/ProGuard (release sin minify)
│   │   └── src/
│   │       ├── main/
│   │       │   ├── AndroidManifest.xml   # Permisos, Activities, Services declarados
│   │       │   ├── java/com/beto/app/
│   │       │   │   ├── BetoApplication.kt              # Init de Timber, canales de notificación, TTS
│   │       │   │   ├── MainActivity.kt                 # Pantalla de bienvenida y pre-flight de permisos
│   │       │   │   ├── action/
│   │       │   │   │   ├── DemoContacts.kt             # Alias hardcodeado del contacto demo "Mi nieto"
│   │       │   │   │   ├── DeterministicMatcher.kt     # Reconocimiento offline de comandos del guion
│   │       │   │   │   ├── IntentBranch.kt             # Disparador del Intent estricto a com.whatsapp
│   │       │   │   │   └── PlanCController.kt          # Orquestador: voz → match → Intent → TTS
│   │       │   │   ├── bus/
│   │       │   │   │   ├── AgentBus.kt                 # Bus reactivo (SharedFlow)
│   │       │   │   │   └── AgentEvents.kt              # Eventos y comandos del sistema
│   │       │   │   ├── llm/
│   │       │   │   │   └── ToolDescriptors.kt          # Contrato de herramientas para el LLM (Phase 3)
│   │       │   │   ├── overlay/
│   │       │   │   │   ├── OverlayBubble.kt            # Burbuja flotante (drag, tap, magnet a borde)
│   │       │   │   │   └── OverlayManager.kt           # Gestión de la ventana de sistema
│   │       │   │   ├── service/
│   │       │   │   │   ├── BetoAccessibilityService.kt # "Ojos" — lectura de pantalla
│   │       │   │   │   └── BetoForegroundService.kt    # Servicio principal y coordinación TTS
│   │       │   │   ├── util/
│   │       │   │   │   ├── LogTags.kt                  # Tags Timber centralizados (Beto-XXX)
│   │       │   │   │   └── PreflightCheck.kt           # Validación de permisos críticos
│   │       │   │   └── voice/
│   │       │   │       ├── TtsManager.kt               # Motor TTS nativo en es-AR
│   │       │   │       └── VoiceCaptureActivity.kt     # Activity transparente para captura STT
│   │       │   └── res/
│   │       │       ├── drawable/                       # Iconos, fondo de la burbuja, logo
│   │       │       ├── layout/overlay_bubble.xml       # Layout XML de la burbuja
│   │       │       ├── values/                         # strings.xml (incluye textos en es-AR), styles.xml
│   │       │       └── xml/accessibility_service_config.xml  # Config del AccessibilityService
│   │       └── test/                     # Tests unitarios (matcher, intent branch)
│   ├── gradle/
│   │   ├── libs.versions.toml            # Catálogo de versiones (single source of truth)
│   │   └── wrapper/                      # Gradle Wrapper
│   ├── build.gradle.kts                  # Config raíz del proyecto Android
│   ├── gradle.properties                 # Flags de build (AndroidX, JVM args)
│   ├── settings.gradle.kts               # Repos y módulos incluidos
│   ├── gradlew / gradlew.bat             # Scripts del wrapper
│   └── .gitignore                        # Ignora /build, local.properties, etc.
├── docs/
│   └── STATUS.md                         # Estado vivo del proyecto, hitos por phase
├── .planning/                            # Roadmap, requirements, planes por phase (GSD workflow)
├── .github/                              # Instrucciones para agentes de IA y manifest GSD
├── CLAUDE.md                             # Reglas globales y stack detallado (input al agente)
├── GEMINI.md                             # Resumen del proyecto orientado a Gemini CLI
├── README.md                             # Este archivo
├── project-logo.png                      # Logo del proyecto (1000×1000, requisito de submission)
├── platanus-hack-project.json            # Metadata para la submission del hackathon
└── .gitignore                            # Ignora IDEs, builds, .env, skills locales
```

---

## Instalación y puesta en marcha

### Prerrequisitos

| Requisito | Versión / Notas |
|---|---|
| Android Studio | Ladybug Feature Drop o más nuevo |
| JDK | 11 (gestionado por Gradle Wrapper) |
| Dispositivo físico | Android 12+ (API 31+) recomendado — el emulador no soporta bien `AccessibilityService` ni overlays |
| Cuenta Firebase | Necesaria para `google-services.json` si vas a usar el LLM (Phase 3+) |

> **Importante:** este proyecto está pensado para correr en un dispositivo físico. Varias APIs clave (overlay, accesibilidad, micrófono en foreground service) tienen comportamiento parcial o roto en emuladores.

### 1. Cloná el repositorio

```bash
git clone https://github.com/platanus-hack-26/platanus-hack-26-ar-team-12.git
cd platanus-hack-26-ar-team-12/android
```

### 2. Configurá Firebase (opcional para Plan C, requerido para Phase 3+)

El repo incluye un `google-services.json` placeholder. Para correr el LLM, reemplazalo por tu propio archivo descargado desde la consola de Firebase con:

- Proyecto Firebase con **Firebase AI Logic** habilitado.
- App Android registrada con `applicationId = com.beto.app`.

Sin LLM (solo Plan C offline) la app compila igual; las llamadas a Gemini simplemente no se ejecutan.

### 3. Compilá

```bash
# APK debug
./gradlew assembleDebug

# APK + instalación en el dispositivo conectado por ADB
./gradlew installDebug
```

El APK queda en `android/app/build/outputs/apk/debug/app-debug.apk` (~13 MB).

### 4. Otorgá permisos manuales en el dispositivo

Beto necesita permisos especiales que **no se piden con el diálogo runtime estándar**. La primera vez que abrís la app, `MainActivity` te lleva guiado, pero también podés hacerlo manualmente:

| Permiso | Ruta en Settings |
|---|---|
| Mostrar sobre otras apps | Ajustes → Aplicaciones → Beto → **Mostrar sobre otras apps** → Permitir |
| Servicio de accesibilidad | Ajustes → Accesibilidad → Servicios instalados → **Beto** → Activar |
| Micrófono | Se solicita runtime al primer uso del recognizer |
| Contactos / Teléfono | Se solicitan runtime cuando hagan falta (Phase 3) |
| Ignorar optimización de batería | Ajustes → Batería → Beto → **Sin restricciones** (recomendado para la demo) |

### 5. Verificá

```bash
# Logs en vivo, filtrados por tags de Beto
adb logcat -s "Beto-Accessibility:D" "Beto-LLM:D" "Beto-Action:D" "Beto-TTS:D" "Beto-Voice:D"
```

Marcadores que deberías ver al disparar un comando:

```
PLAN_C_STT_START
PLAN_C_STT_RESULT elapsedMs=...
PLAN_C_MATCHED
PLAN_C_WHATSAPP_LAUNCHED
```

---

## Uso

1. **Abrí la app una vez** para que `MainActivity` valide permisos y arranque el `BetoForegroundService`.
2. La **burbuja flotante** queda visible sobre cualquier app. Arrastrala donde te quede cómoda — se magnetiza al borde.
3. **Tocá la burbuja** una vez. Beto abre el recognizer en `es-AR`.
4. **Hablá natural.** Por ejemplo:
   > *"Mandale a mi nieto que ya llegué."*
5. Beto **confirma con voz**:
   > *"Abro WhatsApp con el mensaje para tu nieto."*
6. Se abre WhatsApp con el destinatario y el texto **pre-llenados**. El usuario solo toca enviar (decisión deliberada: nunca enviamos automáticamente en MVP).

> Si el comando no matchea o el contacto no se resuelve, Beto pide aclaración corta y cálida sin frustrar al usuario.

---

## Desarrollo

### Convenciones de código

- **Tono del agente** (system prompts y TTS): vocabulario simple, frases cortas, **voseo argentino**. *"Decime qué necesitás"*, no *"Dime qué necesitas"*.
- **Modularidad estricta:** la lógica de UI, los Services de Android y las llamadas al LLM viven en paquetes separados. No mezclar.
- **No re-inventar:** si una API nativa de Android resuelve el caso (Intents, recognizer), preferila al loop agéntico.
- **Sin sobre-ingeniería:** este repo es para una demo de 24-36 h. No agregamos abstracciones para casos hipotéticos.

### Logs

Logging centralizado vía Timber. Tags definidos en [`util/LogTags.kt`](./android/app/src/main/java/com/beto/app/util/LogTags.kt):

| Tag | Para qué |
|---|---|
| `Beto-Accessibility` | Parsing de pantalla y ejecución de gestos |
| `Beto-LLM` | Prompts, tool calls, respuestas |
| `Beto-Voice` | Ciclo STT/TTS |
| `Beto-Action` | Motor de acciones, Plan C controller, Intents |
| `Beto-TTS` | Motor TTS específicamente |

### Tests

```bash
# Unit tests (matcher determinista, intent branch)
./gradlew testDebugUnitTest

# Build verification
./gradlew assembleDebug
```

Los tests críticos viven en `android/app/src/test/java/com/beto/app/action/`.

### Privacidad

Sanitización mínima on-device antes de enviar payloads al LLM:
- Regex para DNI argentino (`\d{7,8}`).
- Regex para teléfonos (`(\+54)?\s?\d{10,12}`).
- Regex para tarjetas (PAN de 13-19 dígitos).

NER on-device queda **explícitamente fuera de scope** para el MVP.

---

## Roadmap

| Phase | Estado | Alcance |
|---|---|---|
| 1 — Foundation | Completa | `AgentBus`, burbuja, Foreground Service, TTS, permisos, logs |
| 2 — Vertical Slice (Plan C) | Completa | STT `es-AR` + matcher determinista + Intent WhatsApp end-to-end, offline |
| 3 — Motor de acciones + Compañero | Próxima | Gemini 2.5 Flash con tool calling, sanitización, Modo Compañero (chat cálido) |
| 4 — Loop agéntico + UX senior | Pendiente | Loop agéntico via `AccessibilityService` cuando un Intent fijo falla, estados visuales de la burbuja, tipografía senior-friendly |
| 5 — Demo readiness | Pendiente | Freeze del APK, hot-spare phone, hotspot dedicado, ensayos, video respaldo, submission |

Detalle vivo en [`docs/STATUS.md`](./docs/STATUS.md). Plans por phase en [`.planning/`](./.planning/).

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
