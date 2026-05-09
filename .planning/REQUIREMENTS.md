# Requirements: Beto

**Defined:** 2026-05-09
**Last reframe:** 2026-05-09 (post Phase 2: visión expandida — memoria, multi-canal, guía con gestos, anti-fraude reactivo, voz humana)
**Core Value:** Beto es un asistente conversacional que opera el celular por voz, aprende y recuerda al usuario, lo guía con gestos en pantalla, lo acompaña conversando, y detecta intentos de estafa cuando se lo consultan — todo en español argentino con tono cálido.

## v1 Requirements

Requirements para la demo en vivo de la hackathon. Cada uno mapea a una fase del roadmap.

### Setup (proyecto Android instalable) — Phase 1

- [x] **SETUP-01**: Proyecto Kotlin compilable con AGP 8.7.x, Gradle 8.10, Kotlin 2.1.10, minSdk 31, targetSdk 34, Java 11 + desugaring habilitado
- [x] **SETUP-02**: `AndroidManifest.xml` declara `BetoForegroundService` con `foregroundServiceType="microphone"`, `BetoAccessibilityService` con `BIND_ACCESSIBILITY_SERVICE` y filter, y todos los permisos críticos (`RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_MICROPHONE`, `READ_CONTACTS`, `CALL_PHONE`, `SEND_SMS`, `INTERNET`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
- [x] **SETUP-03**: `accessibility_service_config.xml` con `canRetrieveWindowContent=true`, `canPerformGestures=true`, eventos `typeWindowStateChanged|typeWindowContentChanged|typeViewClicked`
- [x] **SETUP-04**: APK instalable en el teléfono dedicado con Google Services configurado (Firebase project + `google-services.json`)
- [x] **SETUP-05**: Logging con tags `Beto-Accessibility`, `Beto-LLM`, `Beto-Action`, `Beto-STT`, `Beto-Intent`, `Beto-Memory`, `Beto-Guide`, `Beto-Fraud` (Timber)

### Contratos Compartidos — Phase 1

- [x] **BUS-01**: `AgentBus` singleton con `SharedFlow<AgentEvent>` y `SharedFlow<AgentCommand>` accesible desde service y activity
- [x] **BUS-02**: `AgentEvents.kt` con sealed classes para todos los eventos del flujo (VoiceCaptured, IntentClassified, ActionExecuted, ToolFailed, etc.)
- [x] **BUS-03**: `ToolDescriptors.kt` con definiciones de las tools del LLM con schemas estrictos en español

### Burbuja Flotante (single entry point)

- [x] **OVERLAY-01**: Burbuja flotante visible permanentemente sobre cualquier app, dragable, con magnet a borde — Phase 1
- [x] **OVERLAY-02**: Burbuja usa `TYPE_ACCESSIBILITY_OVERLAY` cuando el AccessibilityService está conectado, fallback a `TYPE_APPLICATION_OVERLAY` — Phase 1
- [x] **OVERLAY-03**: Tap corto en burbuja dispara captura de voz — Phase 2
- [ ] **OVERLAY-04**: Tap largo (long-press) en burbuja abre el Modo Compañero — Phase 4
- [ ] **OVERLAY-05**: Burbuja muestra 5 estados visuales distinguibles (idle / listening / thinking / speaking / error) con color + ícono + animación 200ms — Phase 4

### Voz (STT + TTS) base

- [x] **VOICE-01**: `TtsManager` singleton inicializado en `Application.onCreate()` con cascada de Locale `es-AR` → `es-419` → `es-ES` → `es` → `en-US` y cola interna pre-init — Phase 1
- [x] **VOICE-02**: TTS pronuncia frase de boot al iniciar el servicio para verificar que la voz funciona — Phase 1
- [x] **VOICE-03**: `VoiceCaptureActivity` transparente que hostea `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` con `EXTRA_LANGUAGE="es-AR"` y devuelve texto al `AgentBus` — Phase 2
- [x] **VOICE-04**: TTS confirma al usuario qué acción se va a ejecutar antes de hacerla (en tono cálido y corto) — Phase 2
- [x] **VOICE-05**: TTS reporta éxito o fallo de la acción con frase amable de máximo 1 oración — Phase 2

### Voz humana — Phase 4 (NUEVO)

- [ ] **VOICE-HUM-01**: `TtsManager.selectBestVoice()` itera sobre `tts.voices` y elige la **mejor voz neural masculina argentina** disponible. Prioridad: (1) voz explícitamente masculina y `es-AR` neural; (2) voz masculina `es-419`/`es-MX` neural; (3) voz neural cualquier `es` filtrada como masculina; (4) cualquier voz masculina `es`; (5) cualquier `es` (último recurso). Detección de género heurística por nombre de voz (Google neural usa convenciones tipo `es-AR-Tomas-Neural` masculino vs `es-AR-Elena-Neural` femenino) + lista mantenida de IDs conocidos. Si no hay voz masculina disponible, log warning para revisar al setup.
- [ ] **VOICE-HUM-02**: Las respuestas del flujo de acción (confirmación, éxito, fallo) son **generadas por Gemini** en lugar de PhraseBank fijo — con cache por hash de input para mitigar latencia.
- [ ] **VOICE-HUM-03**: System prompt de Beto refuerza tono de **amigo** (no asistente formal): voseo argentino siempre, "che" / "dale" naturales, **prohibido tratar de "usted"**. Quality checks en `PhraseGenerator` rechazan outputs con `usted/ustedes/su/sus` (en sentido formal) y forzan regeneración.

### Motor de Acciones — Intents Fijos

- [x] **ACT-01**: Tabla hardcoded `DemoContacts.kt` mapea aliases a números E.164 — Phase 2 (será reemplazada por memoria + libreta del sistema en Phase 3)
- [x] **ACT-02**: Tool `send_whatsapp(contact, message)` arma Intent `wa.me/PHONE?text=...` con `setPackage("com.whatsapp")` y abre WhatsApp con texto pre-llenado — Phase 2
- [ ] **ACT-03**: Tool `make_call(contact)` lanza Intent `ACTION_CALL` con número resuelto desde la memoria/libreta — Phase 3
- [ ] **ACT-04**: Tool `send_sms(contact, message)` lanza Intent `ACTION_SENDTO` con `smsto:` y texto pre-llenado — Phase 3
- [ ] **ACT-05**: Tool `open_maps(query)` lanza Intent geo con búsqueda — Phase 3
- [x] **ACT-06**: Si un Intent falla, TTS reporta el problema en tono cálido — Phase 2

### LLM Cliente — Phase 3

- [ ] **LLM-01**: Interface `LlmClient` con `GeminiLlmClient` (default, vía Firebase AI Logic SDK con `gemini-2.5-flash`) y `AnthropicLlmClient` (fallback comentado, `claude-haiku-4-5`)
- [ ] **LLM-02**: `PromptBuilder` con system prompt estricto, few-shots argentinos, tool descriptions en español
- [ ] **LLM-03**: Tool calling configurado con `temperature: 0`, allow-list de tool names, validación con `ignoreUnknownKeys=false` y retry 1x si JSON malformado
- [x] **LLM-04**: `DeterministicMatcher` con regex de los comandos top tiene precedencia sobre el LLM (cubre el guion sin internet) — Phase 2
- [ ] **LLM-05**: Cache de respuestas LLM por hash de input para resiliencia ante red caída en demo
- [ ] **LLM-06**: `ActionDispatcher` recibe ToolCall y rutea a `IntentBranch` (top-N) o pregunta al user si falta info

### STT — corrector y on-device — Phase 3 (NUEVO)

- [ ] **STT-FIX-01**: `SttCorrector` recibe `(rawTranscript, contexto)` donde contexto incluye lista de contactos del usuario y el último comando ejecutado, y devuelve transcript corregido. Implementado vía Gemini con prompt corto.
- [ ] **STT-FIX-02**: Cuando el dispositivo soporta `SpeechRecognizer.createOnDeviceSpeechRecognizer()` (API 31+ con paquete on-device disponible), `VoiceCaptureActivity` lo usa preferentemente — mejor para acentos / voces senior. Fallback al cloud-backed si on-device no está disponible.

### Privacidad — Sanitización On-Device — Phase 3

- [ ] **PRIV-01**: `Sanitizer` con regex que tachan DNI argentino (7-8 dígitos), teléfonos (formato AR `+54...` o `1\d{9,10}`), tarjetas de crédito (16 dígitos con/sin separadores), antes de enviar texto al LLM
- [ ] **PRIV-02**: Sanitizer se aplica en el `LlmClient` antes del `generateContent` para garantizar que NINGÚN payload sale sin pasar por el filtro

### Acceso a contactos del sistema — Phase 3 (NUEVO)

- [ ] **CONTACTS-01**: Permiso runtime `READ_CONTACTS` solicitado en el onboarding (`MainActivity` o sheet dedicado). Si el usuario lo rechaza, Beto opera contra `DemoContacts.kt` como fallback.
- [ ] **CONTACTS-02**: `ContactRepository` lee `ContactsContract.CommonDataKinds.Phone` y devuelve por nombre (búsqueda fuzzy con `ContactsContract.Contacts.CONTENT_FILTER_URI`).
- [ ] **CONTACTS-03**: Resolución multi-canal: `ContactRepository.resolve(name)` devuelve `ContactInfo(displayName, phoneNumbers, hasWhatsApp, hasEmail)`. `hasWhatsApp` se infiere chequeando si existe contacto con `mimetype` de WhatsApp.

### Memoria del usuario — Phase 3 (NUEVO)

- [ ] **MEM-01**: `UserMemory` data class serializable con campos: `aliases: Map<String, ContactRef>`, `channelPreferences: Map<ContactId, Channel>`, `personalProfile: ProfileFacts` (familia, hobbies, fecha de cumpleaños, ciudad, etc.), `version: Int`.
- [ ] **MEM-02**: `UserMemoryStore` persiste `UserMemory` serializado JSON dentro de `EncryptedSharedPreferences` (clave `user_memory_v1`). Operaciones thread-safe con `Mutex`.
- [ ] **MEM-03**: `UserMemory.knowsAlias("nieto") -> Boolean` y `UserMemory.resolveAlias("nieto") -> ContactRef?` consultables sin red.
- [ ] **MEM-04**: API para acumular profile facts (`UserMemoryStore.recordFact("hobby", "leer novelas")`). Sin overwrite — append-style.

### Aprendizaje multi-canal — Phase 3 (NUEVO)

- [ ] **LEARN-01**: Si el comando referencia un alias no en `UserMemory.aliases`, `ActionRouter` invoca `ContactClarifier` que pregunta por TTS *"¿Quién es tu nieto?"*, captura voz, resuelve contra `ContactRepository`, y guarda en memoria.
- [ ] **LEARN-02**: Si el comando es de mensajería sin canal especificado, y `UserMemory.channelPreferences` no tiene preferencia para ese contacto, `ChannelClarifier` pregunta por TTS *"¿Por WhatsApp, SMS o llamada?"*, captura voz, persiste preferencia, ejecuta. La segunda vez no pregunta.

### Modo Compañero — Phase 4

- [ ] **COMP-01**: `CompanionActivity` con BottomSheet/card Compose mostrando chat conversacional
- [ ] **COMP-02**: System prompt cálido distinto al Motor de Acciones, vocabulario simple y argentino, respuestas cortas, paciencia explícita
- [ ] **COMP-03**: Modelo `gemini-2.5-flash-lite` con `temperature: 0.4` para tono natural
- [ ] **COMP-04**: Historial de mensajes en `ViewModel` (stateless entre sesiones — se descarta al cerrar sheet, opcionalmente snapshot a memoria si el user dice algo profile-relevant)

### Modo Guía con gestos en pantalla — Phase 4 (NUEVO, diferenciador clave)

- [ ] **GUIDE-01**: Tool `show_how_to(action: enum)` invocable por LLM cuando el user pregunta *"¿cómo hago X?"*. Acciones soportadas v1: `send_whatsapp_audio`, `make_video_call`, `add_contact`, `increase_volume`, `open_camera`.
- [ ] **GUIDE-02**: `GestureOverlay` (View dentro del overlay manager) dibuja una flecha animada (`ObjectAnimator` de translación + alpha) apuntando a las coordenadas del View target. Las coordenadas las resuelve `BetoAccessibilityService.findNodeByText()` o `findNodeByContentDescription()` sobre `rootInActiveWindow`.
- [ ] **GUIDE-03**: `GuideScripts.kt` define para cada acción: (a) `targetSelector` (texto/contentDescription a buscar), (b) `voiceSteps` (frases TTS paso-a-paso), (c) `appPackage` (Beto puede abrir la app si no está en foreground vía Intent).

### UX Senior — Phase 4

- [ ] **UX-01**: Estilos de tipografía `textStyleHero` ≥28sp y `textStyleBody` ≥22sp con alto contraste en toda UI propia (Compañero + atajos + sheet de guía)
- [ ] **UX-02**: Mensajes de error siempre en tono cálido — nunca exponer códigos técnicos al usuario
- [ ] **UX-03**: TTS limita feedback a 1-2 frases máximas para evitar verbosidad

### Anti-fraude reactivo — Phase 5 (NUEVO)

- [ ] **FRAUD-01**: Tool `analyze_for_fraud(text: String, screenshot: Image?)` invocable por LLM. Devuelve `FraudVerdict(is_fraud: Boolean, risk_level: enum {low, medium, high}, explanation_warm: String, suggested_action: String)`.
- [ ] **FRAUD-02**: System prompt anti-fraude argentino especializado: lista de patrones (AFIP/ANSES, premios falsos, urgencia artificial, pedidos de DNI/CBU/clave), ejemplos few-shot reales, instrucción de ser cálido sin alarmismo y vocabulario simple. Invocación natural: *"Beto, ¿esto es estafa?"* o *"Beto, mirá esto, ¿es real?"* — si hay screenshot disponible, lo pasa multimodal a Gemini Vision.

### Activación rápida — Phase 6 (opcional)

- [ ] **ACTIV-01**: Mantener pulsado volumen-down 2s lanza la captura de voz como si se tocara la burbuja. Implementación vía `AccessibilityService.onKeyEvent` o servicio de fondo con `MediaSessionCompat`.
- [ ] **ACTIV-02**: (Stretch) Wake word "Hola Beto" implementado vía spike previo de 30 min. Candidatos: `SpeechRecognizer` continuo con detección de keyword, integración con Google Assistant intent, o lib OSS sin license issue.

### Demo Readiness — Phase 7

- [ ] **DEMO-01**: Teléfono dedicado seedeado: WhatsApp instalado (NO Business), contactos del guion creados (mínimo 3 contactos: nieto/Pedro, hijo/Carlos, médica/Dra López), voz TTS es-AR pre-descargada manualmente, Accessibility/Overlay/Battery configurados manualmente, READ_CONTACTS otorgado
- [ ] **DEMO-02**: Atajo en homescreen visible que lanza directamente el flujo principal (entry secundario si la burbuja falla)
- [ ] **DEMO-03**: Atajo desde el código a `Settings → Accessibility` para re-toggle rápido (<8s) si el sistema desactiva el AS
- [x] **DEMO-04**: Pre-flight check al boot que valida `canDrawOverlays` + `isAccessibilityEnabled` + TTS init OK; si falla, TTS lo informa — Phase 1
- [ ] **DEMO-05**: APK freezeado ≥4 horas antes de la demo (no más builds), una sola keystore, hot-spare phone idénticamente configurado
- [ ] **DEMO-06**: Plan C offline-first verificado en modo avión: matcher determinista + Intents + TTS hardcoded permite ejecutar el guion principal sin LLM
- [ ] **DEMO-07**: Hotspot personal del dev como red dedicada para la demo (NO Wi-Fi del venue)
- [ ] **DEMO-08**: Guion ensayado mínimo 5 veces extremo a extremo (incluyendo recuperación de errores)
- [ ] **DEMO-09**: Video pre-grabado del guion completo (3 min) como respaldo absoluto si todo falla en vivo
- [ ] **DEMO-10**: Checklist físico en papel con 16 items (toggles, voz TTS, contactos seedeados, READ_CONTACTS otorgado, modo avión OFF, hotspot ON, pre-flight verde, ensayo de cada comando, APK no actualizado en 4hs)

### Submission Hackathon — Phase 7

- [ ] **SUB-01**: `platanus-hack-project.json` completado con `project-name`, `project-oneliner-spanish`, `project-description-spanish`
- [x] **SUB-02**: README actualizado con descripción real de Beto (sin placeholder LLM, sin emojis bananas) — Hecho 2026-05-09

## v2 Requirements

Reconocidos pero diferidos. Tracked pero no en el roadmap actual.

### Wake Word

- **WAKE-01**: Activación por voz "Beto" sin tocar pantalla, on-device (post-hackathon, requiere licencia comercial de Porcupine o alternativa OSS)

### Anti-fraude proactivo

- **SHIELD-01**: Detección automática de mensajes de WhatsApp/SMS con sentido de urgencia o links sospechosos, overlay rojo + alerta por voz
- **SHIELD-02**: Heurísticas específicas para adultos mayores (AFIP, ANSES, premios)
- **SHIELD-03**: Monitor de notificaciones (`NotificationListenerService`) integrado al sistema anti-fraude

### Onboarding Real

- **ONB-01**: Flujo guiado paso-a-paso para activar Accessibility + SYSTEM_ALERT_WINDOW + Battery unrestricted en cualquier teléfono Android del usuario final, asistido por voz

### Cloud STT

- **STT-CLOUD-01**: STT con Whisper / Realtime API como default cuando hay buena red, fallback a nativo cuando no hay internet
- **STT-CLOUD-02**: Mejor calidad de reconocimiento para voces senior con ruido de fondo

### Privacidad Avanzada

- **PRIV-NER-01**: NER on-device (ML Kit / TF Lite) para detectar nombres, ubicaciones y otros PII estructurado
- **PRIV-NER-02**: Pipeline completo: NER + clasificador de sensibilidad + redacción estructurada

### Memoria avanzada

- **MEM-RAG-01**: Embeddings + retrieval para perfil de usuario grande
- **MEM-RAG-02**: Memoria episódica de conversaciones del Modo Compañero (con consentimiento explícito)

### Multi-Plataforma / Multi-Usuario

- **MULTI-01**: Persistencia de historial entre sesiones con cuenta de usuario
- **MULTI-02**: Modo "cuenta familiar" — el hijo/nieto puede configurar Beto desde su teléfono
- **MULTI-03**: iOS (postergado por dependencia de AccessibilityService)
- **MULTI-04**: Soporte de computadora (postergado, plataforma entera nueva)

## Out of Scope (permanente o v2)

| Feature | Reason |
|---------|--------|
| Loop agéntico universal (LLM iterativo operando UI) | Visión vieja del roadmap. Reemplazada por Modo Guía con gestos. **Permanente fuera v1.** |
| Wake word "Beto" en MVP | Porcupine non-commercial license + complejidad on-device. Botón flotante + vol-down 2s cubren activación. v2 (o Phase 6 si spike sale viable). |
| Anti-fraude proactivo | Requiere monitor continuo + overlay rojo + heurísticas. La versión reactiva (Phase 5) cubre la tesis. v2. |
| Onboarding visual completo de permisos | Permisos especiales no se piden con dialog estándar. Demo asume teléfono pre-configurado. v2. |
| Cloud STT (Whisper / Realtime) | Suma latencia + dependencia de red. STT nativo + corrector LLM alcanzan. v2. |
| NER on-device profundo | ML Kit / TF Lite suma 2-3 días. Regex simple cuenta la historia. v2. |
| Memoria con embeddings/RAG | JSON simple alcanza para hackathon. v2. |
| Vision (MediaProjection) full | Permiso adicional + duplica latencia + 4-10k tokens vs 1-3k. Solo se usa puntual en FRAUD-02. v2 ampliable. |
| Multi-usuario / persistencia entre sesiones | Demo es single-device, single-user. v2. |
| Notificaciones push proactivas | Asustan a adultos mayores sin opt-in serio. v2 con consideración cuidadosa. |
| Apps bancarias / pagos | Apps bancarias bloquean AccessibilityService por seguridad. **Anti-feature permanente.** |
| iOS / Web | La tesis depende de AccessibilityService Android. **Solo Android. Permanente.** |
| Soporte de computadora | Plataforma entera nueva, scope explosion. **v2+.** |
| Compose dentro de Service (burbuja) | Requiere `ViewTreeLifecycleOwner` manual = 2-3hs sin valor demo. Views clásicas para overlay. **Locked.** |
| Hilt / KSP | Overhead sin retorno en sprint. Singletons manuales alcanzan. **Locked.** |
| Configuración / Settings de usuario | Target real (adultos mayores) no entra a Settings. Anti-feature. |
| Suscripciones / monetización | No aplica para hackathon ni MVP. v3+. |
| Multi-idioma (no es-AR) | Solo es-AR. Otros idiomas v2+. |

## Traceability

Mapping completo de los v1 requirements a las 7 fases del roadmap.

| Requirement | Phase | Status |
|---|---|---|
| SETUP-01..05 | Phase 1 | ✓ Validated |
| BUS-01..03 | Phase 1 | ✓ Validated |
| OVERLAY-01, OVERLAY-02 | Phase 1 | ✓ Validated |
| OVERLAY-03 | Phase 2 | ✓ Validated |
| OVERLAY-04 | Phase 4 | Pending |
| OVERLAY-05 | Phase 4 | Pending |
| VOICE-01, VOICE-02 | Phase 1 | ✓ Validated |
| VOICE-03..05 | Phase 2 | ✓ Validated |
| VOICE-HUM-01, VOICE-HUM-02 | Phase 4 | Pending |
| ACT-01, ACT-02, ACT-06 | Phase 2 | ✓ Validated |
| ACT-03..05 | Phase 3 | Pending |
| LLM-01..03 | Phase 3 | Pending |
| LLM-04 | Phase 2 | ✓ Validated |
| LLM-05, LLM-06 | Phase 3 | Pending |
| STT-FIX-01, STT-FIX-02 | Phase 3 | Pending |
| PRIV-01, PRIV-02 | Phase 3 | Pending |
| CONTACTS-01..03 | Phase 3 | Pending |
| MEM-01..04 | Phase 3 | Pending |
| LEARN-01, LEARN-02 | Phase 3 | Pending |
| COMP-01..04 | Phase 4 | Pending |
| GUIDE-01..03 | Phase 4 | Pending |
| UX-01..03 | Phase 4 | Pending |
| FRAUD-01, FRAUD-02 | Phase 5 | Pending |
| ACTIV-01, ACTIV-02 | Phase 6 (opcional) | Pending |
| DEMO-01..03, DEMO-05..10 | Phase 7 | Pending |
| DEMO-04 | Phase 1 | ✓ Validated |
| SUB-01 | Phase 7 | Pending |
| SUB-02 | Phase 7 | ✓ Validated (README rewritten) |

**Coverage:**

- v1 requirements totales: 70
- Validated: 22 (Phase 1: 13, Phase 2: 8, SUB-02 hecho fuera de fase)
- Pending: 48 distribuidos en Phases 3-7
- Mapped: 70 ✓
- Unmapped: 0 ✓

**Distribución por fase:**

| Phase | Count |
|---|---|
| Phase 1 | 13 (✓ validated) |
| Phase 2 | 9 (✓ validated, incluye SUB-02 anticipado) |
| Phase 3 | 19 (LLM + STT-FIX + PRIV + CONTACTS + MEM + LEARN + ACT-03..05) |
| Phase 4 | 14 (VOICE-HUM + OVERLAY-04..05 + COMP + GUIDE + UX) |
| Phase 5 | 2 (FRAUD-01..02) |
| Phase 6 | 2 (ACTIV-01..02) — opcional |
| Phase 7 | 11 (DEMO + SUB-01) |

---
*Requirements defined: 2026-05-09*
*Last updated: 2026-05-09 — full reframe post Phase 2. AGENTIC-01..05 movidos permanentemente a Out of Scope. Sumados: STT-FIX, CONTACTS, MEM, LEARN, VOICE-HUM, GUIDE, FRAUD, ACTIV (24 reqs nuevos).*
