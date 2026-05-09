# Requirements: Beto

**Defined:** 2026-05-09
**Core Value:** Beto entiende un comando de voz complejo en español argentino y ejecuta la acción correcta en el celular sin que el adulto mayor tenga que tocar nada más.

## v1 Requirements

Requirements para la demo en vivo de la hackathon (24-36 horas). Cada uno mapea a una fase del roadmap.

### Setup (proyecto Android instalable)

- [ ] **SETUP-01**: Proyecto Kotlin compilable con AGP 8.7.x, Gradle 8.10, Kotlin 2.1.10, minSdk 31, targetSdk 34, Java 11 + desugaring habilitado
- [ ] **SETUP-02**: `AndroidManifest.xml` declara `BetoForegroundService` con `foregroundServiceType="microphone"`, `BetoAccessibilityService` con `BIND_ACCESSIBILITY_SERVICE` y filter, y todos los permisos críticos (`RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_MICROPHONE`, `READ_CONTACTS`, `CALL_PHONE`, `INTERNET`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
- [ ] **SETUP-03**: `accessibility_service_config.xml` con `canRetrieveWindowContent=true` y eventos `typeWindowStateChanged|typeWindowContentChanged|typeViewClicked`
- [ ] **SETUP-04**: APK instalable en el teléfono dedicado con Google Services configurado (Firebase project + `google-services.json`)
- [ ] **SETUP-05**: Logging con tags `Beto-Accessibility`, `Beto-LLM`, `Beto-Action`, `Beto-STT`, `Beto-Intent` (Timber)

### Contratos Compartidos (Phase 0 — sync de hora 0)

- [ ] **BUS-01**: `AgentBus` singleton con `SharedFlow<AgentEvent>` y `SharedFlow<AgentCommand>` accesible desde service y activity
- [ ] **BUS-02**: `AgentEvents.kt` con sealed classes para todos los eventos del flujo (VoiceCaptured, IntentClassified, ActionExecuted, ToolFailed, etc.)
- [ ] **BUS-03**: `ToolDescriptors.kt` con definiciones de las tools del LLM (send_whatsapp, make_call, send_sms, open_maps, agentic_perform_action) con schemas estrictos en español

### Burbuja Flotante (single entry point)

- [ ] **OVERLAY-01**: Burbuja flotante visible permanentemente sobre cualquier app, dragable, con magnet a borde
- [ ] **OVERLAY-02**: Burbuja usa `TYPE_ACCESSIBILITY_OVERLAY` cuando el AccessibilityService está conectado, fallback a `TYPE_APPLICATION_OVERLAY`
- [ ] **OVERLAY-03**: Tap corto en burbuja dispara captura de voz (Motor de Acciones)
- [ ] **OVERLAY-04**: Tap largo (long-press) en burbuja abre el Modo Compañero
- [ ] **OVERLAY-05**: Burbuja muestra 5 estados visuales distinguibles (idle / listening / thinking / speaking / error) con color + ícono + animación 200ms

### Voz (STT + TTS)

- [ ] **VOICE-01**: `TtsManager` singleton inicializado en `Application.onCreate()` con cascada de Locale `es-AR` → `es-419` → `es-ES` → `es` → `en-US` y cola interna pre-init
- [ ] **VOICE-02**: TTS pronuncia frase de boot al iniciar el servicio para verificar que la voz funciona ("Hola, soy Beto. Estoy acá para ayudarte.")
- [ ] **VOICE-03**: `VoiceCaptureActivity` transparente que hostea `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` con `EXTRA_LANGUAGE="es-AR"` y devuelve texto al `AgentBus`
- [ ] **VOICE-04**: TTS confirma al usuario qué acción se va a ejecutar antes de hacerla (en tono cálido y corto)
- [ ] **VOICE-05**: TTS reporta éxito o fallo de la acción con frase amable de máximo 1 oración

### Motor de Acciones — Intents Fijos (camino confiable)

- [ ] **ACT-01**: Tabla hardcoded `DemoContacts.kt` mapea nombres ("nieto", "hijo", "Ana") a números E.164 (+54...) para los contactos del guion
- [ ] **ACT-02**: Tool `send_whatsapp(contact, message)` arma Intent `wa.me/PHONE?text=...` con `setPackage("com.whatsapp")` y abre WhatsApp con texto pre-llenado
- [ ] **ACT-03**: Tool `make_call(contact)` lanza Intent `ACTION_CALL` con número resuelto desde la tabla
- [ ] **ACT-04**: Tool `send_sms(contact, message)` lanza Intent `ACTION_SENDTO` con `smsto:` y texto pre-llenado
- [ ] **ACT-05**: Tool `open_maps(query)` lanza Intent geo con búsqueda
- [ ] **ACT-06**: Si un Intent falla (app no instalada, permiso, etc.), TTS reporta el problema y opcionalmente el loop agéntico intenta como fallback silencioso

### Loop Agéntico (respaldo silencioso, NO demostrado en vivo)

- [ ] **AGENTIC-01**: `BetoAccessibilityService` lee `rootInActiveWindow` filtrado a `isVisibleToUser && (isClickable || isLongClickable || isFocusable || hasText)` con límite de 50 nodos
- [ ] **AGENTIC-02**: `TreeSerializer` produce representación compacta con refs `@e1`, `@e2` y `NodeRefRegistry` traduce refs de vuelta a `AccessibilityNodeInfo` reales
- [ ] **AGENTIC-03**: `AgentLoop` con hard limits sagrados: `MAX_ITERATIONS=5`, `TIMEOUT=15s` wallclock, `MAX_TOKENS=4000` por turno
- [ ] **AGENTIC-04**: Una acción por turno + `nodeInfo.refresh()` antes de cada `performAction`; abort si el hash del árbol no cambia entre 2 iteraciones
- [ ] **AGENTIC-05**: Loop solo se invoca como fallback silencioso de un Intent que falla (NO es entrada principal en demo)

### LLM Cliente

- [ ] **LLM-01**: Interface `LlmClient` con dos implementaciones: `GeminiLlmClient` (default, vía Firebase AI Logic SDK con `gemini-2.5-flash`) y `AnthropicLlmClient` (fallback comentado, `claude-haiku-4-5`)
- [ ] **LLM-02**: `PromptBuilder` con system prompt estricto, few-shots argentinos, tool descriptions en español
- [ ] **LLM-03**: Tool calling configurado con `temperature: 0`, allow-list de tool names, validación con `ignoreUnknownKeys=false` y retry 1x si JSON malformado
- [ ] **LLM-04**: `DeterministicMatcher` con regex de los 4 comandos top tiene precedencia sobre el LLM (cubre el guion sin internet)
- [ ] **LLM-05**: Cache de respuestas LLM por hash de input para resiliencia ante red caída en demo
- [ ] **LLM-06**: `ActionDispatcher` recibe ToolCall y rutea a `IntentBranch` (top-N) o `AgenticBranch` (loop fallback)

### Privacidad — Sanitización On-Device

- [ ] **PRIV-01**: `Sanitizer` con regex que tachan DNI argentino (8 dígitos), teléfonos (formato AR), tarjetas de crédito (Luhn opcional, formato 16 dígitos), antes de enviar texto al LLM
- [ ] **PRIV-02**: Sanitizer se aplica como interceptor OkHttp para garantizar que NINGÚN payload sale sin pasar por el filtro

### Modo Compañero (alma del producto)

- [ ] **COMP-01**: `CompanionActivity` con BottomSheet/card Compose mostrando chat conversacional
- [ ] **COMP-02**: System prompt cálido distinto al Motor de Acciones, vocabulario simple y argentino, respuestas cortas, paciencia explícita
- [ ] **COMP-03**: Modelo `gemini-2.5-flash-lite` con `temperature: 0.4` para tono natural
- [ ] **COMP-04**: Historial de mensajes en `ViewModel` (stateless entre sesiones — se descarta al cerrar sheet)

### UX Senior

- [ ] **UX-01**: Estilos de tipografía `textStyleHero` ≥28sp y `textStyleBody` ≥22sp con alto contraste en toda UI propia (Compañero + atajos)
- [ ] **UX-02**: Mensajes de error siempre en tono cálido ("Uy, algo no salió bien. Probá de nuevo, dale.") — nunca exponer códigos técnicos al usuario
- [ ] **UX-03**: TTS limita feedback a 1 frase máxima para evitar verbosidad

### Demo Readiness

- [ ] **DEMO-01**: Teléfono dedicado seedeado: WhatsApp instalado (NO Business), contactos del guion creados, voz TTS es-AR pre-descargada manualmente, Accessibility/Overlay/Battery configurados manualmente
- [ ] **DEMO-02**: Atajo en homescreen visible que lanza directamente el flujo principal (entry secundario si la burbuja falla)
- [ ] **DEMO-03**: Atajo desde el código a `Settings → Accessibility` para re-toggle rápido (<8s) si el sistema desactiva el AS
- [ ] **DEMO-04**: Pre-flight check al boot que valida `canDrawOverlays` + `isAccessibilityEnabled` + TTS init OK; si falla, TTS lo informa
- [ ] **DEMO-05**: APK freezeado ≥4 horas antes de la demo (no más builds), una sola keystore, hot-spare phone idénticamente configurado
- [ ] **DEMO-06**: Plan C offline-first verificado en modo avión: matcher determinista + Intents + TTS hardcoded permite ejecutar el guion principal sin LLM
- [ ] **DEMO-07**: Hotspot personal del dev como red dedicada para la demo (NO Wi-Fi del venue)
- [ ] **DEMO-08**: Guion ensayado mínimo 5 veces extremo a extremo (incluyendo recuperación de errores)
- [ ] **DEMO-09**: Video pre-grabado del guion completo (3 min) como respaldo absoluto si todo falla en vivo
- [ ] **DEMO-10**: Checklist físico en papel con 16 items (toggles, voz TTS, contactos seedeados, modo avión OFF, hotspot ON, pre-flight verde, ensayo de cada comando, APK no actualizado en 4hs)

### Submission Hackathon

- [ ] **SUB-01**: `platanus-hack-project.json` completado con `project-name`, `project-oneliner-spanish`, `project-description-spanish`
- [ ] **SUB-02**: README actualizado con descripción real de Beto (sin placeholder LLM, sin emojis bananas)

## v2 Requirements

Reconocidos pero diferidos. Tracked pero no en el roadmap actual.

### Wake Word

- **WAKE-01**: Activación por voz "Beto" sin tocar pantalla (post-hackathon, requiere licencia comercial de Porcupine o alternativa OSS)

### Escudo Antiestafas

- **SHIELD-01**: Detección de mensajes de WhatsApp con sentido de urgencia o links sospechosos, overlay rojo + alerta por voz
- **SHIELD-02**: Mockup visual del Escudo en el pitch deck (1 hora de trabajo) — narrativa de roadmap
- **SHIELD-03**: Heurísticas de detección y prevención específicas para adultos mayores

### Onboarding Real

- **ONB-01**: Flujo guiado paso-a-paso para activar Accessibility + SYSTEM_ALERT_WINDOW + Battery unrestricted en cualquier teléfono Android del usuario final

### Cloud STT

- **STT-CLOUD-01**: STT con Whisper / Realtime API como default, fallback a nativo cuando no hay internet
- **STT-CLOUD-02**: Mejor calidad de reconocimiento para acentos y voces de adultos mayores

### Privacidad Avanzada

- **PRIV-NER-01**: NER on-device (ML Kit / TF Lite) para detectar nombres, ubicaciones y otros PII estructurado
- **PRIV-NER-02**: Pipeline completo: NER + clasificador de sensibilidad + redacción estructurada

### Vision

- **VIS-01**: Captura de pantalla vía MediaProjection cuando el árbol de vistas no alcanza
- **VIS-02**: LLM multimodal procesa screenshots para acciones agénticas avanzadas

### Multi-Plataforma / Multi-Usuario

- **MULTI-01**: Persistencia de historial entre sesiones (cuenta de usuario)
- **MULTI-02**: Modo "cuenta familiar" — el hijo/nieto puede configurar Beto desde su teléfono
- **MULTI-03**: iOS (postergado por dependencia de AccessibilityService de Android)

## Out of Scope

Explícitamente excluido en v1. Documentado para prevenir scope creep.

| Feature | Reason |
|---------|--------|
| Wake word "Beto" en MVP | Porcupine non-commercial license + complejidad on-device. Botón flotante cubre activación. v2. |
| Escudo Antiestafas en código MVP | Detección visual robusta + overlay rojo + heurísticas requiere días. Solo mockup en pitch. v2. |
| Onboarding visual de permisos | Permisos Settings no se piden con dialog estándar. Demo asume teléfono pre-configurado. v2. |
| Cloud STT (Whisper / Realtime) | Suma latencia + dependencia de red. STT nativo alcanza para guion ensayado. v2. |
| NER on-device profundo | ML Kit / TF Lite suma 2-3 días. Regex simple cuenta la historia. v2. |
| Vision (MediaProjection) en MVP | Permiso adicional + duplica latencia + 4-10k tokens vs 1-3k. Árbol filtrado alcanza para guion. v2. |
| Multi-usuario / persistencia entre sesiones | Demo es single-device, single-user, stateless. v2. |
| Notificaciones push proactivas | Asustan a adultos mayores sin opt-in serio. v2 con consideración cuidadosa. |
| Apps bancarias / pagos | Apps bancarias bloquean AccessibilityService por seguridad. Excluir refuerza confianza del usuario. Anti-feature permanente. |
| iOS / Web | La tesis del producto depende de AccessibilityService Android. Solo Android. |
| Compose dentro de Service (burbuja) | Requiere `ViewTreeLifecycleOwner` manual = 2-3hs sin valor demo. Views clásicas para overlay. |
| Hilt / KSP | Overhead de KSP roba 1-2hs en sprint sin retorno. Singletons manuales alcanzan. |
| Configuración / Settings de usuario | Target real (adultos mayores) no entra a Settings. Anti-feature. |
| Suscripciones / monetización | No aplica para hackathon ni MVP. v3+. |
| Multi-idioma | Solo es-AR. Otros idiomas v2+. |

## Traceability

Mapping completo de los 56 v1 requirements a las 5 fases del roadmap.

| Requirement | Phase | Status |
|-------------|-------|--------|
| SETUP-01 | Phase 1 | Pending |
| SETUP-02 | Phase 1 | Pending |
| SETUP-03 | Phase 1 | Pending |
| SETUP-04 | Phase 1 | Pending |
| SETUP-05 | Phase 1 | Pending |
| BUS-01 | Phase 1 | Pending |
| BUS-02 | Phase 1 | Pending |
| BUS-03 | Phase 1 | Pending |
| OVERLAY-01 | Phase 1 | Pending |
| OVERLAY-02 | Phase 1 | Pending |
| OVERLAY-03 | Phase 2 | Pending |
| OVERLAY-04 | Phase 3 | Pending |
| OVERLAY-05 | Phase 4 | Pending |
| VOICE-01 | Phase 1 | Pending |
| VOICE-02 | Phase 1 | Pending |
| VOICE-03 | Phase 2 | Pending |
| VOICE-04 | Phase 2 | Pending |
| VOICE-05 | Phase 2 | Pending |
| ACT-01 | Phase 2 | Pending |
| ACT-02 | Phase 2 | Pending |
| ACT-03 | Phase 3 | Pending |
| ACT-04 | Phase 3 | Pending |
| ACT-05 | Phase 3 | Pending |
| ACT-06 | Phase 2 | Pending |
| AGENTIC-01 | Phase 4 | Pending |
| AGENTIC-02 | Phase 4 | Pending |
| AGENTIC-03 | Phase 4 | Pending |
| AGENTIC-04 | Phase 4 | Pending |
| AGENTIC-05 | Phase 4 | Pending |
| LLM-01 | Phase 3 | Pending |
| LLM-02 | Phase 3 | Pending |
| LLM-03 | Phase 3 | Pending |
| LLM-04 | Phase 2 | Pending |
| LLM-05 | Phase 3 | Pending |
| LLM-06 | Phase 3 | Pending |
| PRIV-01 | Phase 3 | Pending |
| PRIV-02 | Phase 3 | Pending |
| COMP-01 | Phase 3 | Pending |
| COMP-02 | Phase 3 | Pending |
| COMP-03 | Phase 3 | Pending |
| COMP-04 | Phase 3 | Pending |
| UX-01 | Phase 4 | Pending |
| UX-02 | Phase 4 | Pending |
| UX-03 | Phase 4 | Pending |
| DEMO-01 | Phase 5 | Pending |
| DEMO-02 | Phase 5 | Pending |
| DEMO-03 | Phase 5 | Pending |
| DEMO-04 | Phase 1 | Pending |
| DEMO-05 | Phase 5 | Pending |
| DEMO-06 | Phase 5 | Pending |
| DEMO-07 | Phase 5 | Pending |
| DEMO-08 | Phase 5 | Pending |
| DEMO-09 | Phase 5 | Pending |
| DEMO-10 | Phase 5 | Pending |
| SUB-01 | Phase 5 | Pending |
| SUB-02 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 56 total
- Mapped to phases: 56 ✓
- Unmapped: 0 ✓

**Distribución por fase:**
- Phase 1 (Foundation & Sync): 13 requirements
- Phase 2 (Vertical Slice Mínimo): 8 requirements
- Phase 3 (Motor Completo + Compañero): 15 requirements
- Phase 4 (Loop Agéntico + UX Senior): 9 requirements
- Phase 5 (Demo Readiness): 11 requirements

---
*Requirements defined: 2026-05-09*
*Last updated: 2026-05-09 — traceability completada por gsd-roadmapper*
