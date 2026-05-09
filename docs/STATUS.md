# Estado Actual del Proyecto - Beto 🇦🇷

**Fecha:** 9 de mayo de 2026
**Proyecto:** Beto (Platanus Hack 26 - Vertical AI)
**Estado Global:** ✅ Vertical Slice Plan C Operativa (offline) / 🛠️ Listo para Integración LLM (Phase 3)

---

## 🤖 ¿Qué es Beto? (Resumen para todos)

Beto es un acompañante digital diseñado especialmente para que las personas mayores puedan usar su celular sin complicaciones. En lugar de navegar por menús difíciles, Beto permite que el usuario dé órdenes con su voz (en español argentino), y él se encarga de hacer las cosas por ellos.

### 🌟 ¿Qué puede hacer hoy?
Ya no son solo cimientos: Beto entiende y actúa sobre un comando real **completamente offline**.

- **La Burbuja de Beto:** Aparece una burbuja flotante sobre cualquier app. Se arrastra con el dedo y queda magnetizada al borde. Al tocarla, dispara la captura de voz.
- **Te Escucha en Argentino:** Tras tocar la burbuja, Beto abre el reconocedor de voz nativo de Android en `es-AR` y devuelve el transcript final en menos de 3 segundos (medido vía marcador `PLAN_C_STT_RESULT elapsedMs=...`).
- **Te Manda el WhatsApp:** Si decís *"mandale a mi nieto que ya llegué"*, Beto reconoce la intención sin LLM, resuelve el contacto demo "Mi nieto" y abre WhatsApp con el mensaje pre-llenado al número correcto.
- **Te Habla con Calidez:** Beto confirma antes de actuar (*"Abro WhatsApp con el mensaje para tu nieto."*), reporta éxito (*"Listo, te dejé el mensaje preparado."*) y falla con frases cálidas si algo no sale.
- **Sabe Pedir Permiso:** Al iniciar te lleva de la mano a configurar overlay y accesibilidad si faltan.
- **Siempre Atento:** Corre como Foreground Service con notificación persistente para no morir en background.

> **Importante:** El flujo actual es **Plan C offline**. No hay LLM, no hay red, no hay auto-send. Es la base confiable para la demo, sobre la cual se montará la inteligencia conversacional en Phase 3.

---

## 🏗️ Arquitectura Técnica

Beto utiliza una arquitectura desacoplada basada en eventos, optimizada para reactividad y robustez en servicios de fondo de Android.

### 1. Sistema Nervioso: `AgentBus`
El corazón de la comunicación es el `AgentBus`, un bus de eventos reactivo basado en **Kotlin SharedFlow**. Permite que servicios, UI, captura de voz y motor de acciones se comuniquen sin acoplamiento directo.
- **`AgentEvent`**: Hechos que ya ocurrieron (ej: `BubbleTapped`, `VoiceCaptured(text)`, `ActionExecuted`, `ToolFailed`).
- **`AgentCommand`**: Instrucciones (ej: `Speak`, `StartListening`).

### 2. Componentes Core (Servicios)
- **`BetoForegroundService`**: Gestiona el ciclo de vida del agente. Mantiene el proceso vivo con notificación persistente y coordina la salida de audio (TTS).
- **`BetoAccessibilityService`**: Los "ojos" de Beto. Registrado para leer pantalla y ejecutar gestos de forma autónoma (uso completo llega en Phase 4).
- **`OverlayManager`**: Controla la `OverlayBubble`, una interfaz tipo `SYSTEM_ALERT_WINDOW` que permite la interacción desde cualquier app.

### 3. Captura de Voz
- **`VoiceCaptureActivity`**: Activity transparente que hostea el recognizer nativo de Android (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`) en `es-AR`. Emite el transcript final al `AgentBus` y se autodestruye.
- **`TtsManager`**: Envoltorio sobre el TTS nativo de Android, configurado para `es-AR` (con fallback a `es-ES` si la voz argentina no está disponible).

### 4. Motor de Acciones (Plan C Offline)
- **`PlanCController`**: Orquesta el flujo `VoiceCaptured → match → confirmación TTS → Intent → reporte`. Implementa el retry cálido, la aclaración corta para mensaje/contacto faltantes y los marcadores de log estables.
- **`DeterministicMatcher`**: Reconoce la familia de comandos del guion (variantes de *mandale/avisale/decile a mi nieto que…*). Normaliza tildes, puntuación y filler words. **No** intenta lenguaje natural arbitrario.
- **`DemoContacts`**: Alias map hardcodeado (`nieto`, `mi nieto`, nombre real) → número E.164 del contacto demo.
- **`IntentBranch`**: Dispara el Intent estricto a `com.whatsapp` con `wa.me/PHONE?text=...`. No auto-envía. WhatsApp Business queda fuera de Phase 2.

### 5. LLM (preparado, no activo)
- **`ToolDescriptors`**: Definición de las "herramientas" (`send_whatsapp`, `make_call`, `open_maps`, etc.) que el LLM podrá invocar en Phase 3. Hoy son contrato/stub; el cliente Gemini se conecta en la próxima fase.

---

## 📁 Estructura del Proyecto y Archivos

```text
android/app/src/main/java/com/beto/app/
├── action/
│   ├── DemoContacts.kt          <-- Alias map hardcodeado del contacto demo "Mi nieto".
│   ├── DeterministicMatcher.kt  <-- Reconocimiento offline de la familia de comandos del guion.
│   ├── IntentBranch.kt          <-- Disparador del Intent estricto a com.whatsapp.
│   └── PlanCController.kt       <-- Orquestador del flujo voz → matcher → Intent → TTS.
├── bus/
│   ├── AgentBus.kt              <-- Bus de eventos (SharedFlow).
│   └── AgentEvents.kt           <-- Eventos y comandos del sistema.
├── llm/
│   └── ToolDescriptors.kt       <-- Contrato de herramientas para el LLM (activo en Phase 3).
├── overlay/
│   ├── OverlayBubble.kt         <-- Burbuja flotante (drag, tap, magnet a borde).
│   └── OverlayManager.kt        <-- Gestión de la ventana del sistema.
├── service/
│   ├── BetoForegroundService.kt    <-- Servicio principal de fondo y gestión de TTS.
│   └── BetoAccessibilityService.kt <-- Servicio de accesibilidad (lectura de pantalla).
├── voice/
│   ├── TtsManager.kt            <-- Motor TTS nativo en es-AR.
│   └── VoiceCaptureActivity.kt  <-- Activity transparente para captura STT en es-AR.
├── util/
│   ├── LogTags.kt               <-- Tags Timber centralizados (Beto-TTS, Beto-Action, etc.).
│   └── PreflightCheck.kt        <-- Validación de permisos críticos antes de arrancar.
├── BetoApplication.kt           <-- Inicialización de logs, canales de notificación y TTS.
└── MainActivity.kt              <-- Pantalla de configuración inicial y bienvenida.
```

---

## ✅ Hitos Alcanzados

### Phase 1 — Foundation & Sync de Hora 0
- [x] Infraestructura de comunicación (`AgentBus` + `AgentEvents`).
- [x] Interfaz flotante interactiva (`OverlayBubble` con drag + magnet + tap/long-press).
- [x] Foreground Service con notificación persistente.
- [x] Motor de voz local (`TtsManager`) funcionando en es-AR.
- [x] Flujo de permisos automatizado (`PreflightCheck`).
- [x] Logs estructurados (Timber) con tags consistentes.

### Phase 2 — Vertical Slice Mínimo (Plan C Offline)
- [x] `VoiceCaptureActivity` capturando voz en es-AR vía recognizer nativo.
- [x] `DeterministicMatcher` reconociendo la familia de comandos del guion sin LLM.
- [x] `DemoContacts` con contacto demo "Mi nieto" y aliases.
- [x] `IntentBranch` abriendo WhatsApp regular (`com.whatsapp`) con mensaje pre-llenado.
- [x] `PlanCController` orquestando confirmación, retry cálido y aclaración corta.
- [x] Marcadores de log estables: `PLAN_C_STT_START`, `PLAN_C_STT_RESULT elapsedMs=...`, `PLAN_C_MATCHED`, `PLAN_C_WHATSAPP_LAUNCHED`, `PLAN_C_WHATSAPP_FAILED`.
- [x] Verificación: `./gradlew testDebugUnitTest` y `./gradlew assembleDebug` pasan.
- [x] APK debug generado (~13 MB) listo para smoke test físico.
- [ ] **Pendiente:** smoke test físico en el teléfono de demo (ver `02-DEMO-CHECK.md`).

---

## 🚀 Próximos Pasos

### Phase 3 — Motor de Acciones Completo + Compañero (próxima)
1. **Conectar Gemini 2.5 Flash** vía Firebase AI Logic SDK con tool calling sobre `ToolDescriptors`.
2. **Sumar Intents** restantes: llamadas, SMS, Maps (los 4 caminos confiables completos).
3. **Sanitizer on-device** (regex DNI/teléfono/tarjeta) antes de cualquier payload al LLM.
4. **Modo Compañero**: long-press abre sheet de chat cálido (Compose).
5. **Fallback offline**: si la red cae, volver al matcher determinista de Phase 2 sin que el usuario lo note.

### Phase 4 — Loop Agéntico + UX Senior
- Loop agéntico silencioso usando `BetoAccessibilityService` cuando un Intent fijo falla.
- Estados visuales de la burbuja (idle/listening/thinking/speaking/error).
- Tipografía ≥22sp con contraste alto en toda la UI propia.

### Phase 5 — Demo Readiness
- Freeze APK 4+ horas antes, hot-spare phone, hotspot dedicado, ensayos 5x, video respaldo, submission.
