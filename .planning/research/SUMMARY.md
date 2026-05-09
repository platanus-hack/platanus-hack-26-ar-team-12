# Project Research Summary

**Project:** Beto — agente Android multimodal autónomo para adultos mayores
**Domain:** Android nativo (Kotlin) + AccessibilityService + LLM con tool calling + STT/TTS + overlay flotante
**Researched:** 2026-05-09
**Confidence:** HIGH (decisiones cerradas en stack, arquitectura, pitfalls); MEDIUM (parametrización fina del loop agéntico bajo presión de demo)
**Context:** Hackathon Platanus Hack 26 — track Vertical AI — 24-36hs — 5 devs full-stack Android — demo en vivo de 3-5 min

---

## Executive Summary

Beto es un agente Android nativo que opera el celular por adultos mayores: oye un comando en español argentino ("avisale a mi nieto que ya llegué"), entiende la intención, ejecuta la acción correcta sobre WhatsApp / llamadas / Maps, y confirma con voz cálida. La tesis del producto se prueba en una demo en vivo de 3-5 min frente al jurado: si esa demo no funciona, el proyecto pierde, así que toda decisión de diseño se evalúa contra "¿cuánto puede romper la demo?" antes que contra "¿cuán elegante es?".

La arquitectura recomendada es híbrida: **Intents directos para los 3-4 comandos del guion principal (camino confiable) + loop agéntico de respaldo sobre AccessibilityNodeInfo para el comando "ambicioso" (camino que prueba la visión universal una vez)**. El cerebro vive en un `BetoForegroundService` que owna el agent loop y el cliente LLM; el `BetoAccessibilityService` solo lee el árbol de vistas y ejecuta `performAction()`; una `VoiceCaptureActivity` transparente hace de host del `RecognizerIntent`; todo se coordina en un `AgentBus` (singleton + `SharedFlow`) en el mismo proceso. **El LLM es Gemini 2.5 Flash vía Firebase AI Logic SDK** — la única vía con SDK oficial Android Kotlin que llama al modelo desde el cliente sin backend propio (ahorro de ~6-10 horas críticas en hackathon). Claude Haiku 4.5 vía `anthropic-java` queda como fallback arquitectural si Gemini decepciona en es-AR durante las primeras horas.

El riesgo dominante no es algorítmico, es de demo en vivo: AccessibilityService que el sistema desactiva silenciosamente, FGS que muere por OEM (Xiaomi/Samsung), TTS race en el primer comando, WhatsApp Intent con número mal formateado, loop agéntico que se cuelga, red del venue caída con el LLM cloud no respondiendo. La mitigación es estructural: **hard limits sagrados en el loop (5 iter / 15s / 4K tokens), Plan C offline-first con matcher determinista + Intents + TTS hardcoded sin LLM, freeze del APK 4hs antes, hot-spare phone idénticamente configurado, hotspot personal del dev como red dedicada, y una fase explícita de Demo Readiness con checklist físico**. Vision (MediaProjection) queda fuera del MVP — solo árbol de vistas filtrado.

---

## Key Findings

### Recommended Stack

Stack Android nativo moderno y mínimo: Kotlin 2.1.10, AGP 8.7.x, Gradle 8.10, **minSdk 31 (Android 12)** justificado para tener acceso a `SpeechRecognizer.createOnDeviceSpeechRecognizer()` y simplificar overlays modernos sobre un teléfono de demo controlado. Compose solo para sheet de chat del Compañero; **Views clásicas para la burbuja flotante** (Compose dentro de Service requiere `ViewTreeLifecycleOwner` manual y roba 2-3hs sin valor para la demo).

**Core technologies:**
- **Gemini 2.5 Flash** (`com.google.firebase:firebase-ai`, BoM 33.5.1+) — LLM principal con tool calling — único SDK oficial Android sin backend propio, free tier sin tarjeta, latencia baja, multimodal nativo
- **Gemini 2.5 Flash-Lite** — modelo del Modo Compañero (chat simple) — más barato/rápido que Flash, suficiente para conversación cálida
- **`SpeechRecognizer` nativo Android (`RecognizerIntent` con `EXTRA_LANGUAGE="es-AR"`)** — STT — gratis, on-device en API 31+, suficiente con guion ensayado
- **`TextToSpeech` nativo (`Locale("es","AR")` con cascada de fallback es-419 → es-ES → es)** — TTS — pre-warmup en `Application.onCreate()` obligatorio para evitar race en primer comando
- **Kotlin coroutines + Flow 1.8.1** — async backbone — STT/LLM/AS callbacks se modelan limpiamente con `suspend` y `SharedFlow`
- **kotlinx.serialization 1.7.3** — JSON + tool calling args parsing — type-safe con compiler plugin
- **`anthropic-java` 2.30.0+ con Claude Haiku 4.5** — fallback LLM arquitectural — capa `LlmClient` con dos implementaciones, swap en 1-2hs si Gemini subperforma
- **Timber 5.0.1** — logging con tags `Beto-Accessibility`, `Beto-LLM`, `Beto-Action` (alineado con CLAUDE.md)
- **Java desugaring** (`coreLibraryDesugaring 2.x`) — habilitado para soportar `anthropic-java` y `java.time` en API 31

**Lo que NO se usa:** React Native / Flutter (incompatibles con AccessibilityService profundo), Compose en burbuja flotante, Hilt (KSP roba 1-2hs sin retorno), Porcupine wake word (non-commercial license risk), Whisper / cloud STT (Out of Scope), NER on-device (Out of Scope), Retrofit (innecesario), `TYPE_SYSTEM_OVERLAY` deprecated.

Detalle completo: `.planning/research/STACK.md`

### Expected Features

**Must have (table stakes):**
- Tipografía grande + alto contraste en toda UI propia
- Confirmación por voz antes de acción destructiva
- Latencia <2s end-to-end (target, no gating)
- Feedback constante de estado (idle/listening/thinking/speaking) en la burbuja
- Vocabulario simple, cálido y argentino en TODAS las respuestas
- Burbuja flotante como single entry point claro
- Tolerancia a errores STT (fuzzy matching de apps y contactos)
- Llamar y mandar mensaje (WhatsApp/SMS) por voz a contacto por nombre
- Funcionar degradado sin internet (mensaje cálido de error)
- Filtrado regex on-device de DNI/teléfono/tarjeta antes de cloud LLM
- Onboarding-zero (permisos pre-otorgados manualmente en demo)

**Should have (diferenciadores):**
- **Loop agéntico universal sobre AccessibilityNodeInfo** — la tesis (basta UNA demo exitosa fuera del top-N)
- **Híbrido Intents fijos + loop agéntico**
- **Tono empático en es-AR** ("dale", "tranqui", "ya está")
- **Modo Compañero conversacional** integrado con el Motor de Acciones
- **Sanitizador on-device** (regex simple) — narrativa de privacidad
- **Resolución de entidades fuzzy** (LLM con tool calling)
- **Demo en vivo confiable** ensayada 5+ veces

**Defer (v2+):**
- Wake word "Beto"
- **Escudo Antiestafas** (solo mockup en pitch deck)
- Onboarding asistido de permisos
- Cloud STT (Whisper / Realtime API)
- NER on-device profundo
- Persistencia de historial entre sesiones
- Notificaciones push proactivas
- Multi-dispositivo / cuenta familiar
- iOS / Web

Detalle completo: `.planning/research/FEATURES.md`

### Architecture Approach

**3 procesos lógicos vivos en simultáneo dentro del mismo proceso Android** (`com.beto.app`), comunicados por un singleton `AgentBus` con `SharedFlow<AgentEvent>` — sin Binder, sin AIDL, type-safe via sealed classes. **Vision (MediaProjection) queda fuera del MVP**: solo árbol de vistas filtrado a `isVisibleToUser && (isClickable || isLongClickable || isFocusable || hasText)`, serializado con refs cortos `@e1`, `@e2` (patrón Droidrun / Callstack — 80% reducción de tokens).

**Major components:**
1. **`BetoForegroundService`** — owner del agent loop y del cliente LLM, mantiene el proceso vivo, `foregroundServiceType="microphone"` (Android 14+ obligatorio)
2. **`BetoAccessibilityService`** — lee `rootInActiveWindow`, ejecuta `performAction`, owner del overlay (`TYPE_ACCESSIBILITY_OVERLAY` trusted, NO `TYPE_SYSTEM_ALERT` deprecated)
3. **`VoiceCaptureActivity`** (transparente) — host del `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, devuelve texto al bus y se cierra
4. **`AgentBus`** — singleton `SharedFlow<AgentEvent>` + `SharedFlow<AgentCommand>`, contrato congelado en hora 0
5. **`ActionDispatcher`** — recibe `ToolCall` del LLM, rutea a `IntentBranch` (top-N) o `AgenticBranch` (loop)
6. **`AgentLoop`** — ReAct con `MAX_ITERATIONS=5`, timeout 15s, max 4K tokens por turn, abort por árbol estancado (hash) o `done(fail)`
7. **`LlmClient`** — interface con dos impls (Gemini default + Anthropic fallback), `Sanitizer.scrub()` en interceptor OkHttp
8. **`TtsManager`** — singleton init en `Application.onCreate()`, cascada de Locale, cola interna pre-init
9. **`OverlayBubble`** — View clásica con drag/tap/long-press; tap = STT, long-press = `CompanionActivity`
10. **`NodeRefRegistry`** — traduce refs `@e5` que el LLM emite a `AccessibilityNodeInfo` reales

**Patrones clave:**
- Tool calling como single source of routing (zero duplicación entre branch fija y agéntica)
- Una acción por turno LLM + `nodeInfo.refresh()` antes de cada `performAction`
- `@e1` refs en lugar de XPath/IDs reales

Detalle completo: `.planning/research/ARCHITECTURE.md`

### Critical Pitfalls

1. **AccessibilityService desactivado silenciosamente** (Pitfall #1) — Xiaomi/Samsung/Android 13+ "Restricted settings". Prevención: heartbeat + pre-flight check + checklist físico + freeze APK + hot-spare.
2. **TTS race en primer comando** (Pitfall #3) — `speak()` antes de `onInit` se descarta silenciosamente. Prevención: init en `Application.onCreate()`, cola interna, frase de boot, voz es-AR pre-descargada manualmente.
3. **Loop agéntico colgado / infinito** (Pitfall #7) — Prevención: hard limits sagrados (5/15s/4K), prompt estructurado, solo 1 comando "wow" usa el loop.
4. **WhatsApp Intent malformado** (Pitfall #4) — Prevención: tabla hardcoded `name → e164_phone`, `setPackage("com.whatsapp")`, desinstalar WhatsApp Business, fallback al loop.
5. **Red del venue caída + LLM no responde** (Pitfall #9) — Prevención: hotspot personal del dev + Plan C offline-first (matcher determinista + Intents + TTS hardcoded) + cache por hash.
6. **FGS muerto por OEM / Doze** (Pitfall #8) — Prevención: `foregroundServiceType="microphone"` en manifest Y runtime, battery unrestricted manualmente, screen timeout = Never durante demo.
7. **LLM tool calling JSON malformado / tool inventado** (Pitfall #6) — Prevención: schemas estrictos, `temperature: 0`, allow-list, caída al matcher determinista.
8. **AccessibilityNodeInfo stale tras WindowContentChanged** (Pitfall #5) — Prevención: `refresh()` antes de cada acción, una acción por turno, throttle 200ms.
9. **Overlay no aparece sobre apps con FLAG_SECURE / Android 14 HIDE_OVERLAY_WINDOWS** (Pitfall #10) — Prevención: excluir bancos del scope, atajo en homescreen como entry secundario, video pre-grabado de respaldo.
10. **Permisos resetean tras `adb install -r` / signing change** (Pitfall #12) — Prevención: freeze APK ≥4hs antes, una sola keystore, checklist post-install (6 toggles).

Detalle completo + 2 pitfalls adicionales (SpeechRecognizer en service, apps bancarias bloqueando AS): `.planning/research/PITFALLS.md`

---

## Decisiones Consolidadas (cerradas)

| # | Decisión |
|---|----------|
| 1 | LLM principal: **Gemini 2.5 Flash vía Firebase AI Logic SDK** (tiebreaker: cero backend) |
| 2 | LLM fallback: **Claude Haiku 4.5 vía `anthropic-java`** detrás de interface `LlmClient` |
| 3 | Modelo Compañero: **Gemini 2.5 Flash-Lite** |
| 4 | minSdk 31 / targetSdk 34 / compileSdk 34 |
| 5 | Kotlin 2.1.10 + AGP 8.7.x + Gradle 8.10 + Java desugaring habilitado |
| 6 | Compose **solo** para sheet del Compañero; Views clásicas para burbuja |
| 7 | NO Hilt, NO Retrofit, NO Gson, NO Compose-en-Service |
| 8 | STT: `RecognizerIntent` lanzado desde `VoiceCaptureActivity` transparente |
| 9 | TTS: cascada `es-AR` → `es-419` → `es-ES` → `es` → `en-US`, init en `Application.onCreate()` |
| 10 | Overlay: `TYPE_ACCESSIBILITY_OVERLAY` (trusted) cuando AS conectado, `TYPE_APPLICATION_OVERLAY` antes |
| 11 | FGS: `foregroundServiceType="microphone"` manifest + runtime + permission `FOREGROUND_SERVICE_MICROPHONE` |
| 12 | **Vision (MediaProjection) FUERA del MVP** — solo árbol de vistas filtrado |
| 13 | Loop agéntico: **MAX_ITERATIONS=5, timeout 15s, max 4K tokens por turn**, abort por hash |
| 14 | Una acción por turno LLM + `nodeInfo.refresh()` antes de cada `performAction` |
| 15 | Routing único vía tool calling; `ActionDispatcher` ejecuta |
| 16 | **Sync de hora 0**: `AgentBus.kt`, `AgentEvents.kt`, `ToolDescriptors.kt`, `BetoApplication.kt` mergeados antes de paralelizar |
| 17 | Híbrido Motor de Acciones: 3-4 Intents fijos + 1 demo agéntica; guion principal NO depende del loop |
| 18 | Tabla hardcoded `name → e164_phone`; `setPackage("com.whatsapp")`; desinstalar WhatsApp Business |
| 19 | Sanitizer regex en interceptor OkHttp = zero leak |
| 20 | **Plan C offline-first**: matcher determinista + Intents + TTS hardcoded — demo principal funciona SIN LLM |
| 21 | Hotspot personal del dev como red dedicada — NO Wi-Fi venue |
| 22 | Freeze APK ≥4hs antes; misma keystore todo el sprint; hot-spare phone idénticamente configurado |
| 23 | Sin wake word, sin Escudo en código (solo mockup), sin onboarding |
| 24 | Bancos / pagos fuera del scope agéntico |
| 25 | Tags de log: `Beto-Accessibility`, `Beto-LLM`, `Beto-Action`, `Beto-STT`, `Beto-Intent` |

---

## Stack Final con Versiones

```kotlin
// Plugins
com.android.application       8.7.3
org.jetbrains.kotlin.android  2.1.10
plugin.serialization          2.1.10
com.google.gms.google-services 4.4.2

// Build
compileSdk 34, targetSdk 34, minSdk 31
JavaVersion.VERSION_11 + coreLibraryDesugaring 2.1.2

// LLM principal
platform("com.google.firebase:firebase-bom:33.5.1")
"com.google.firebase:firebase-ai"
"com.google.firebase:firebase-appcheck-playintegrity"   // opcional

// LLM fallback (comentado salvo activación)
// "com.anthropic:anthropic-java:2.30.0"
// "com.anthropic:anthropic-java-client-okhttp:2.30.0"

// Kotlin core
"org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1"
"org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3"

// UI (solo sheet Compañero)
platform("androidx.compose:compose-bom:2024.10.01")
"androidx.compose.material3:material3"
"androidx.compose.ui:ui"
"androidx.activity:activity-compose:1.9.3"
"androidx.lifecycle:lifecycle-runtime-ktx:2.8.7"
"androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7"

// Logging
"com.jakewharton.timber:timber:5.0.1"
```

**Modelos LLM:**
- Agente: `gemini-2.5-flash` (Google explícitamente recomienda Flash, NO Lite, para 5+ tool agent workflows)
- Compañero: `gemini-2.5-flash-lite`
- Fallback: `claude-haiku-4-5`

**Permissions críticos en manifest:**
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<service android:name=".service.BetoForegroundService"
  android:foregroundServiceType="microphone" android:exported="false" />
<service android:name=".service.BetoAccessibilityService"
  android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
  android:exported="true">
  <intent-filter><action android:name="android.accessibilityservice.AccessibilityService" /></intent-filter>
  <meta-data android:name="android.accessibilityservice"
    android:resource="@xml/accessibility_service_config" />
</service>
```

`accessibility_service_config.xml`: `canRetrieveWindowContent="true"`, `accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds"`, `accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewClicked"`.

---

## Implications for Roadmap — Build Order Paralelizado

### Fase 0 — Sync de Hora 0 (30-45 min, todo el equipo)
**Rationale:** Sin contrato de bus + sealed events + tool descriptors mergeados antes de paralelizar, los 5 tracks colisionan en hora 12.
**Delivers:** `AgentBus.kt`, `AgentEvents.kt` (sealed), `ToolDescriptors.kt`, `BetoApplication.kt` con TODOs, `AndroidManifest.xml` con services + permisos, `accessibility_service_config.xml`.
**Avoids:** Pitfall #11 (FGS sin type) prevenido en manifest.
**Research flag:** NO.

### Fase 1 — Setup Base + Permisos + Overlay (Track A — 1 dev)
**Rationale:** La burbuja es root del árbol de dependencias. Pitfalls #1, #8, #10, #12 se previenen acá o nunca.
**Delivers:** `BetoForegroundService.kt` con notificación heartbeat, `BetoAccessibilityService.kt` esqueleto + heartbeat, `OverlayBubble.kt` + `OverlayManager.kt`, pre-flight check (`canDrawOverlays` + `isAccessibilityEnabled`), atajo en homescreen.
**Avoids:** Pitfall #1, #8, #10, #12.
**Open questions:** ¿Burbuja sobrevive sobre cada app del guion? ¿`canDrawOverlays` es true post-install?

### Fase 2 — Voz: STT + TTS (Track C — 1 dev)
**Rationale:** TTS es task de día 1. Pitfall #3 (race) requiere init en `Application.onCreate()`. Pitfall #2 obliga a Activity transparente.
**Delivers:** `TtsManager` con cascada Locale + cola pre-init + frase de boot, `VoiceCaptureActivity` transparente, frases hardcoded de feedback.
**Avoids:** Pitfall #2, #3.
**Open questions:** ¿Voz es-AR descargada en el teléfono? ¿`isRecognitionAvailable` true?

### Fase 3 — Motor de Acciones (Camino Confiable: Intents) (Track E — 1 dev)
**Rationale:** Es **el** camino que se demuestra en vivo. Guion principal NO depende del loop. Pitfall #4 es el riesgo central.
**Delivers:** `IntentBranch.kt` con send_whatsapp/make_call/send_sms/open_maps, tabla `DemoContacts.kt` E.164, normalizer, `setPackage("com.whatsapp")`, seeding del teléfono (WhatsApp Business desinstalado, contactos demo).
**Avoids:** Pitfall #4.
**Open questions:** ¿Cada contacto produce Intent que pre-llena texto? (test 5x cada uno)

### Fase 4 — LLM + Sanitizer + Loop Agéntico (Track D — 1-2 devs, ruta crítica)
**Rationale:** Track D bloquea integración real. Mitigación: entregar `LlmClient` mock en hora 4 para desbloquear A/B/C/E.
**Delivers:** `LlmClient` interface + `GeminiLlmClient` (default) + `AnthropicLlmClient` (comentado), `Sanitizer` en interceptor OkHttp, `PromptBuilder` con few-shots argentinos + tool descriptions en español, `AgentLoop` con hard limits sagrados, `TreeSerializer` + `NodeRefRegistry` con limit 50 nodos, `ActionDispatcher`, **`DeterministicMatcher` con regex de los 4 comandos del guion (precedencia sobre LLM)**, allow-list, cache por hash, validación con `ignoreUnknownKeys=false` + retry 1x.
**Avoids:** Pitfall #5, #6, #7, #9.
**Research flag:** **SÍ** — disparar `/gsd-research-phase` solo si smoke test en hora 4-6 muestra Gemini Flash falla > 20% en tool calling — eso activa swap a Anthropic.
**Open questions:** ¿Gemini Flash devuelve tool calling estable en es-AR 20/20? ¿Matcher cubre los 4 comandos sin LLM (test modo avión)? ¿Latencia p95 desde el venue <3s?

### Fase 5 — Modo Compañero (Track E parte 2)
**Rationale:** "Casi gratis" una vez que `LlmClient` y UI básica existen. Alma del producto.
**Delivers:** `CompanionActivity` con BottomSheet/card Compose, system prompt cálido distinto al Motor, modelo Lite con `temperature: 0.4`, history en `ViewModel` (stateless entre sesiones).
**Open questions:** ¿Tono argentino sale natural en Lite? (validar 10-15 frases tipo)

### Fase 6 — Tipografía Senior + Estados de Burbuja + UX Polish (Track A parte 2)
**Rationale:** Credibilidad con jurado. Estados visuales de burbuja mueven la aguja UX.
**Delivers:** `textStyleHero` 28sp+, `textStyleBody` 22sp+, alto contraste, estados burbuja (idle/listening/thinking/speaking/error) con color + icono + animación 200ms, drag con magnet a borde + auto-min, TTS de 1 frase máximo, errores cálidos.
**Avoids:** UX pitfalls (verbosidad, sin feedback, errores técnicos).

### Fase 7 — Demo Readiness (FASE EXPLÍCITA — todo el equipo, hora 30-36)
**Rationale:** **No es polish, es seguro.** La mayoría de proyectos hackathon falla en vivo.
**Delivers:**
- Freeze APK ≥4hs antes
- Hot-spare phone idénticamente configurado
- Hotspot personal del dev como red dedicada
- Pre-cargar créditos pagos en Gemini (Vertex $300 GCP credit) Y Anthropic
- Plan C offline-first verificado en modo avión
- Cache de respuestas LLM por hash
- Voz es-AR pre-descargada en ambos teléfonos
- WhatsApp Business desinstalado en ambos
- Settings configurados manualmente (Accessibility ON, Display over apps ON, Allow restricted settings ON, Battery → Unrestricted, Pause app activity if unused → OFF, Screen timeout → Never, volumen máximo)
- Atajo en homescreen muy visible
- Atajo a Settings → Accessibility desde la burbuja (<8s re-toggle)
- Video pre-grabado (10s + 3min) como respaldo absoluto
- Guion ensayado 5+ veces, backup de 2-3 contactos por si uno falla
- **Checklist físico en papel** (16 items: toggles, voz TTS, contactos, modo avión OFF, hotspot ON, pre-flight verde, TTS de boot habla, test ritual "decime hola", test cada comando 1x, APK no actualizado en 4hs)

**Avoids:** Pitfall #1, #8, #9, #12.
**Open questions:** ¿Latencia p95 desde el venue aceptable? ¿Guion completo corre en avión? ¿Hot-spare testeado con mismo guion?

### Build Order Visual

```
HORA 0 ─── HORA 1 ──────────────────────── HORA 12 ─── HORA 18 ────── HORA 30 ── HORA 36
│          │                               │           │               │           │
│ Sync 30m  Track A: Setup+Overlay         │ SYNC 1-2h │ paralelo      │ Demo      │
│ Bus+Evts  Track B: AS+Tree+AgenticBr     │ smoke     │ selectivo:    │ Readi-    │
│ Tools     Track C: Voice (STT/TTS)       │ test:     │ - A pulir UX  │ ness      │
│ Manifest  Track D: LLM+Sanit+Loop        │ tap→STT→  │ - B mejorar   │ freeze    │
│           Track E: Intents+Compañero     │ LLM mock→ │   serializer  │ hot-spare │
│           [D entrega LLM mock en hora 4  │ Intent→   │ - C tunear    │ ensayar 5x│
│            para desbloquear integración] │ WhatsApp→ │   TTS         │ checklist │
│                                          │ TTS       │ - D tunear    │ físico    │
│                                          │           │   prompt+loop │           │
│                                          │           │ - E ensayar   │           │
│                                          │           │   guion       │           │
```

### Phase Ordering Rationale

- **Fase 0 antes que todo** — el `AgentBus` es el contrato compartido.
- **Fases 1-5 en paralelo** — superficies de archivos disjuntas (`service/`, `overlay/`, `voice/`, `agent/`, `llm/`, `companion/`).
- **LLM mock en hora 4** desbloquea integración temprana — Track D no es bottleneck.
- **Sync en hora 12** valida branch fija end-to-end — si esto no anda, todo lo demás es accesorio.
- **Fases 6-7 al final** — consolidación, no creación. Cero features nuevas hora 30-36.

### Research Flags

**Phases needing deeper research:**
- **Fase 4 (LLM + Loop):** parametrización fina de hard limits, prompt engineering en es-AR, estabilidad de tool calling Gemini Flash. Disparar `/gsd-research-phase` solo si smoke test en hora 4-6 falla.
- **Fase 7 (Demo Readiness):** medir latencia desde el venue (p50/p99) — si p95 > 3s, ajustar timeouts y reforzar Plan C.

**Phases with standard patterns (skip research):**
- Fase 0, 1, 2, 3, 5, 6 — patrones bien documentados con docs oficiales Android.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | **HIGH** | Decisiones cerradas con docs oficiales (Firebase AI Logic, Anthropic Java, Android Developers). Único riesgo: behavior real de Gemini Flash en es-AR — mitigado con fallback arquitectural. |
| Features | **MEDIUM-HIGH** | Patrones de competidores bien analizados. Incertidumbre menor: subset que cabe en 24-36hs sin ensayar. |
| Architecture | **HIGH** | Componentes verificados con docs oficiales; patrón `AgentBus` + tool calling como router validado en Droidrun/Callstack. MEDIUM en parámetros del loop (5 iter, 15s) — basados en literatura ReAct + benchmarks GUI, validables solo en runtime. |
| Pitfalls | **HIGH** | 12 pitfalls cubiertos con fuentes oficiales + post-mortems comunidad + dontkillmyapp.com. Cada uno tiene plan B + fase de prevención + verificación. |

**Overall confidence:** **HIGH**.

### Gaps to Address

- **Behavior real de Gemini 2.5 Flash con tool calling en es-AR + tree de Accessibility** — solo medible en hora 4-6. Mitigación: arquitectura `LlmClient` con dos impls.
- **Latencia p95 desde el venue al LLM cloud** — medible solo en sitio. Mitigación: hotspot personal + Plan C offline-first + cache por hash.
- **Calidad de TTS es-AR en el teléfono físico de demo** — depende del Google TTS engine instalado, OEM, descarga de voice data. Mitigación: cascada de Locale + voice data pre-descargada manualmente + frase de boot.
- **Estabilidad real del overlay sobre cada app del guion** — variable por OEM. Mitigación: test individual + atajo en homescreen + video pre-grabado.
- **Behavior del AS en el teléfono específico tras OTA / inactividad** — Xiaomi/Samsung son los peores. Mitigación: heartbeat + pre-flight check + checklist físico + hot-spare.
- **Calidad del fuzzy matching de contactos** — depende del LLM y del seeding. Mitigación: tabla hardcoded `name → e164_phone`.

Ninguno de estos gaps puede resolverse en research adicional — todos requieren testing en el dispositivo físico durante el sprint. El roadmap los expone como "open questions" en cada fase.

---

## Sources

### Primary (HIGH confidence)
- [Firebase AI Logic Android docs](https://firebase.google.com/docs/ai-logic/get-started)
- [Gemini API pricing & models](https://ai.google.dev/gemini-api/docs/pricing)
- [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java)
- [AccessibilityService overview & overlays — Android Developers](https://developer.android.com/guide/topics/ui/accessibility/service)
- [WindowManager.LayoutParams reference](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [Foreground service types — Android 14+](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [TextToSpeech.OnInitListener](https://developer.android.com/reference/android/speech/tts/TextToSpeech.OnInitListener)
- [AccessibilityNodeInfo refresh semantics](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo)
- [Behavior changes Android 15](https://developer.android.com/about/versions/15/behavior-changes-15)

### Secondary (MEDIUM confidence)
- [Callstack — How We Optimized Agent Device for Mobile App Automation](https://www.callstack.com/blog/how-we-optimized-agent-device-for-mobile-app-automation)
- [Droidrun — hybrid accessibility tree + computer vision agent](https://www.blog.d-techstudios.com/2025/07/droidrun-revolutionizing-mobile.html)
- [Claude Haiku 4.5 vs Sonnet 4.5 (Sider)](https://sider.ai/blog/ai-tools/claude-haiku-4_5-vs-sonnet-4-which-model-wins-on-speed-cost-and-capability)
- [ReAct: Reasoning and Acting in LLMs](https://www.ibm.com/think/topics/react-agent)
- [AI Agent Loop Token Costs](https://www.augmentcode.com/guides/ai-agent-loop-token-cost-context-constraints)
- [dontkillmyapp.com](https://dontkillmyapp.com)
- [Function Calling & Tool Use Complete Guide 2026](https://ofox.ai/blog/function-calling-tool-use-complete-guide-2026/)
- [Voice User Interface (VUI) Design Principles](https://www.parallelhq.com/blog/voice-user-interface-vui-design-principles)
- [Google Design — UI & UX Principles for Voice Assistants](https://design.google/library/speaking-the-same-language-vui)
- [WhatsApp Click-to-Chat — wa.me documentation](https://faq.whatsapp.com/5913398998672934)

### Tertiary (LOW confidence — narrativa competitiva)
- [Apple Intelligence & Siri 2026 — App Intents](https://developer.apple.com/documentation/appintents/integrating-actions-with-siri-and-apple-intelligence)
- [Gemini Screen Automation Galaxy S26](https://www.androidcentral.com/apps-software/gemini-screen-automation-rolling-out-for-galaxy-s26)
- [Meta WhatsApp scam warnings](https://techcrunch.com/2025/10/21/whatsapp-and-messenger-add-new-warnings-to-help-older-people-avoid-online-scams/)
- [Necta Launcher / BIG Launcher / ElliQ / Meela](http://biglauncher.com/)

---

*Research completed: 2026-05-09*
*Ready for roadmap: yes*
*Synthesis sources: STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md*
