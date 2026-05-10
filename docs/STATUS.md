# Estado Actual del Proyecto - Beto 🇦🇷

**Fecha:** 9 de mayo de 2026
**Proyecto:** Beto (Platanus Hack 26 - Vertical AI)
**Estado Global:** ✅ Phase 2 Completa (Vertical Slice Offline) / 🚀 Ejecutando Phase 3 (Cerebro IA + Memoria)

---

## 🤖 ¿Qué es Beto? (Resumen para todos)

Beto es un acompañante digital diseñado especialmente para que las personas mayores puedan usar su celular sin complicaciones. En lugar de navegar por menús difíciles, Beto permite que el usuario dé órdenes con su voz (en español argentino), y él se encarga de hacer las cosas por ellos.

### 🌟 ¿Qué puede hacer hoy?
Ya no son solo cimientos: Beto entiende y actúa sobre un comando real **completamente offline**.

- **La Burbuja de Beto:** Aparece una burbuja flotante sobre cualquier app. Se arrastra con el dedo y queda magnetizada al borde. Al tocarla, dispara la captura de voz.
- **Te Escucha en Argentino:** Tras tocar la burbuja, Beto abre el reconocedor de voz nativo de Android en `es-AR` y devuelve el transcript final en menos de 3 segundos.
- **Te Manda el WhatsApp:** Si decís *"mandale a mi nieto que ya llegué"*, Beto reconoce la intención sin LLM, resuelve el contacto demo "Mi nieto" y abre WhatsApp con el mensaje pre-llenado.
- **Te Habla con Calidez:** Beto confirma antes de actuar (*"Abro WhatsApp con el mensaje para tu nieto."*), reporta éxito y falla con frases cálidas.
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

### 3. Memoria y Aprendizaje (En desarrollo - Phase 3)
- **`UserMemory`**: Persistencia en `EncryptedSharedPreferences` + JSON. Beto recuerda alias ("mi nieto"), preferencias de canal (WhatsApp vs SMS) y datos del usuario para no repreguntar.
- **Aprendizaje Multi-canal**: Si no sabe cómo contactar a alguien, pregunta una vez y lo guarda para siempre.

---

## 📁 Estructura del Proyecto

```text
android/app/src/main/java/com/beto/app/
├── action/           <-- Motor de acciones (Intents, Multi-canal, Matcher offline).
├── bus/              <-- Sistema nervioso (AgentBus, Eventos).
├── llm/              <-- Integración Gemini y Descriptores de Herramientas (Phase 3).
├── memory/           <-- Persistencia de perfil y preferencias (Phase 3).
├── overlay/          <-- Interfaz flotante (Burbuja, Drag & Drop).
├── service/          <-- Servicios core (Foreground, Accessibility).
├── voice/            <-- Audio (TTS nativo/neural, STT Activity).
└── util/             <-- Logs, Permisos, Sanitizer de privacidad.
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

---

## 🚀 Próximos Pasos (Roadmap Reframed)

### Phase 3 — Cerebro IA + Memoria + Multi-canal (Actual)
- **Conectar Gemini 2.5 Flash** con tool calling estructurado.
- **Memoria persistida**: Beto aprende quién es "tu nieto" y qué prefiere el usuario.
- **Sanitizer on-device**: Protege DNI/teléfonos antes de enviarlos a la nube.
- **Router Multi-canal**: Elige inteligentemente entre WhatsApp, SMS o Llamada.

### Phase 4 — Voz humana + UX Senior + Modo Guía
- **Voz Neural**: Beto suena humano, no robótico.
- **Modo Compañero**: Chat conversacional cálido (Compose sheet).
- **Modo Guía**: Beto dibuja flechas sobre la pantalla para enseñar a usar otras apps.

### Phase 5 — Anti-fraude Reactivo
- Tool de análisis de estafas: *"Beto, ¿esto es un engaño?"*. Beto analiza y explica simple.

### Phase 6 — Activación Rápida (Opcional)
- Activación por botón de volumen o Wake Word ("Hola Beto").

### Phase 7 — Demo Readiness
- Freeze APK, checklist físico de 16 ítems, video de respaldo y submission final.

