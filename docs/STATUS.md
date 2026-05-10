# Estado Actual del Proyecto - Beto 🇦🇷

**Fecha:** 9 de mayo de 2026
**Proyecto:** Beto (Platanus Hack 26 - Vertical AI)
**Estado Global:** ✅ Phase 3 Completa (Cerebro IA + Memoria + Multi-canal) / 🚀 Lista para Phase 4 (Voz Neural + UX Senior)

---

## 🤖 ¿Qué es Beto? (Resumen para todos)

Beto es un acompañante digital diseñado especialmente para que las personas mayores puedan usar su celular sin complicaciones. En lugar de navegar por menús difíciles, Beto permite que el usuario dé órdenes con su voz (en español argentino), y él se encarga de hacer las cosas por ellos.

### 🌟 ¿Qué puede hacer hoy?
Beto tiene el cerebro completo: entiende comandos complejos en español argentino, aprende de cada interacción y elige el canal correcto para comunicarse.

- **La Burbuja de Beto:** Aparece una burbuja flotante sobre cualquier app. Se arrastra con el dedo y queda magnetizada al borde. Al tocarla, dispara la captura de voz.
- **Te Escucha en Argentino:** Tras tocar la burbuja, Beto abre el reconocedor de voz nativo de Android en `es-AR` y devuelve el transcript final en menos de 3 segundos.
- **Entiende Comandos Complejos con IA:** Para todo lo que no sea el guion garantizado, Beto le pregunta a Gemini 2.5 Flash con tool calling estructurado. Entiende intenciones como *"llamá a mi médica"* o *"mandá un mensaje a Juan"*.
- **Te Manda el WhatsApp (Garantizado):** Si decís *"mandale a mi nieto que ya llegué"*, Beto reconoce la intención **sin LLM** (Plan C offline), resuelve el contacto y abre WhatsApp con el mensaje pre-llenado. Funciona en avión.
- **Aprende Quién es Quién:** Si Beto no sabe a quién te referís con "mi médica", te pregunta una sola vez (`¿Quién es tu médica?`), resuelve el contacto de la agenda real y lo recuerda para siempre en memoria cifrada.
- **Elige el Canal por Vos:** Si no sabe si mandarte por WhatsApp, SMS o llamada, te pregunta una sola vez y guarda tu preferencia por contacto.
- **Maneja Homónimos:** Si hay varios "Carlos" en tu agenda, Beto pregunta cuál en vez de adivinar.
- **Te Habla con Calidez:** Confirma antes de actuar, reporta éxito y falla con frases cálidas. Si no entiende, dice *"No te entendí del todo, repetímelo más despacito."* en vez de crashear.
- **Sabe Pedir Permiso:** Al iniciar te lleva de la mano a configurar overlay y accesibilidad si faltan.
- **Siempre Atento:** Corre como Foreground Service con notificación persistente.

---

## 🏗️ Arquitectura Técnica

Beto utiliza una arquitectura desacoplada basada en eventos, optimizada para reactividad y robustez en servicios de fondo de Android.

### 1. Sistema Nervioso: `AgentBus`
El corazón de la comunicación es el `AgentBus`, un bus de eventos reactivo basado en **Kotlin SharedFlow**.
- **`AgentEvent`**: Hechos que ya ocurrieron (ej: `BubbleTapped`, `VoiceCaptured(text)`, `ActionExecuted`).
- **`AgentCommand`**: Instrucciones (ej: `Speak`, `StartListening`).

### 2. Componentes Core (Servicios)
- **`BetoForegroundService`**: Gestiona el ciclo de vida, la notificación persistente y coordina la salida de audio (TTS).
- **`BetoAccessibilityService`**: Los "ojos" de Beto. Registrado para leer pantalla y ejecutar gestos de forma autónoma.
- **`OverlayManager`**: Controla la `OverlayBubble` (`SYSTEM_ALERT_WINDOW`).

### 3. Cerebro IA (Phase 3 — Completo)
- **`GeminiLlmClient`**: Integración con Gemini 2.5 Flash vía Firebase AI Logic. Tool calling estructurado, caché de decisiones, sanitizador on-device (DNI/teléfono/tarjeta) y retry automático ante JSON malformado.
- **`ActionRouter`**: Enruta cada transcript hacia Plan C (offline) o hacia el LLM según el `DeterministicMatcher`. Separa routing de ejecución.
- **`ActionDispatcher`**: Orquesta el flujo completo: Plan C → LLM → clarificación de contacto/canal → ejecución del Intent. Manejo de errores con fallback cálido.
- **`ChannelClarifier`**: Pregunta por voz `¿Por WhatsApp, SMS o llamada?` y persiste la elección. Hasta 3 intentos con mensajes de reintento amables.
- **`ContactClarifier`**: Pregunta `¿Quién es tu X?`, resuelve contra la agenda real, maneja homónimos con un menú de selección por voz y persiste el alias.

### 4. Memoria y Aprendizaje (Phase 3 — Completo)
- **`UserMemory`**: Modelo de datos serializable con alias de contactos, preferencias de canal por contacto y perfil de hechos del usuario.
- **`UserMemoryStore`**: Persistencia en `EncryptedSharedPreferences` + JSON (`kotlinx.serialization`). API suspendida con mutex para acceso concurrente seguro.
- **Aprendizaje Multi-canal**: Beto aprende alias y canal preferido de cada contacto, nunca repregunta lo mismo dos veces.

### 5. Acceso a Contactos (Phase 3 — Completo)
- **`ContactRepository`**: Búsqueda por nombre en la agenda real del dispositivo (`ContactsContract`). Detecta si el contacto tiene WhatsApp. Fallback demo cuando falta permiso `READ_CONTACTS`.
- **Normalización de teléfonos**: Conversión a E.164 para comparación robusta.

---

## 📁 Estructura del Proyecto

```text
android/app/src/main/java/com/beto/app/
├── action/           <-- Motor de acciones (Router, Dispatcher, Clarifiers, Intents).
├── bus/              <-- Sistema nervioso (AgentBus, Eventos).
├── contacts/         <-- Acceso a agenda real del dispositivo (ContactRepository).
├── llm/              <-- Cerebro IA (GeminiLlmClient, PromptBuilder, Sanitizer, Cache).
├── memory/           <-- Persistencia cifrada de perfil y preferencias (UserMemory).
├── overlay/          <-- Interfaz flotante (Burbuja, Drag & Drop).
├── service/          <-- Servicios core (Foreground, Accessibility).
├── voice/            <-- Audio (TTS nativo/neural, STT Activity, Corrección).
└── util/             <-- Logs, Permisos, LogTags.
```

---

## ✅ Hitos Alcanzados

### Phase 1 — Foundation & Sync de Hora 0
- [x] Infraestructura de comunicación (`AgentBus`).
- [x] Interfaz flotante interactiva (`OverlayBubble`).
- [x] Motor de voz local (`TtsManager`) en es-AR.
- [x] Flujo de permisos automatizado (`PreflightCheck`).

### Phase 2 — Vertical Slice Mínimo (Plan C Offline)
- [x] `VoiceCaptureActivity` capturando voz en es-AR.
- [x] `DeterministicMatcher` reconociendo comandos del guion sin internet.
- [x] `IntentBranch` abriendo WhatsApp con mensaje pre-llenado.
- [x] Marcadores de log estables para diagnóstico de performance.
- [x] Tests unitarios pasando.

### Phase 3 — Cerebro IA + Memoria + Multi-canal
- [x] **`GeminiLlmClient`** (03-01): Integración con Gemini 2.5 Flash, tool calling, caché, sanitizador on-device, retry ante JSON inválido.
- [x] **Corrección STT** (03-02): `TranscriptCorrectionClient` que limpia el transcript vía LLM antes de interpretar.
- [x] **`ContactRepository`** (03-03): Acceso a agenda real del dispositivo, búsqueda por nombre, detección de WhatsApp, fallback demo.
- [x] **`UserMemory` + `UserMemoryStore`** (03-04): Persistencia cifrada de alias, preferencias de canal y perfil. Tests unitarios completos.
- [x] **`ActionRouter` + `ActionDispatcher`** (03-05): Router Plan C → LLM, orquestador del flujo completo con `ChannelClarifier`, `ContactClarifier` y manejo de homónimos.
- [x] **Hardening de routing** (audit-fix): Robustecimiento de llamadas telefónicas y canal multi en `ActionDispatcher`.
- [x] **Checklist de demo** (`docs/03-DEMO-CHECK.md`): 7 escenarios verificables en el teléfono de demo.

---

## 🚀 Próximos Pasos (Roadmap Reframed)

### Phase 4 — Voz humana + UX Senior + Modo Guía (Actual)
- **Voz Neural**: Beto suena humano, no robótico.
- **Modo Compañero**: Chat conversacional cálido (Compose sheet).
- **Modo Guía**: Beto dibuja flechas sobre la pantalla para enseñar a usar otras apps.
- Plan elaborado y UI-SPEC disponibles en `.planning/phases/04-*/`.

### Phase 5 — Anti-fraude Reactivo
- Tool de análisis de estafas: *"Beto, ¿esto es un engaño?"*. Beto analiza y explica simple.

### Phase 6 — Activación Rápida (Opcional)
- Activación por botón de volumen o Wake Word ("Hola Beto").

### Phase 7 — Demo Readiness
- Freeze APK, checklist físico de 16 ítems, video de respaldo y submission final.

