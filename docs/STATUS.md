# Estado Actual del Proyecto - Beto 🇦🇷

**Fecha:** 9 de mayo de 2026  
**Proyecto:** Beto (Platanus Hack 26 - Vertical AI)  
**Estado Global:** 🛠️ Infraestructura Base Completa / Iniciando Integración de IA

---

## 🤖 ¿Qué es Beto? (Resumen para todos)

Beto es un acompañante digital diseñado especialmente para que las personas mayores puedan usar su celular sin complicaciones. En lugar de navegar por menús difíciles, Beto permite que el usuario dé órdenes con su voz (en español argentino), y él se encarga de hacer las cosas por ellos.

### 🌟 ¿Qué puede hacer hoy?
Por ahora, hemos construido los "cimientos" de su casa:
- **La Burbuja de Beto:** Ya aparece una burbuja flotante en la pantalla que podés mover con el dedo. Si la tocás o la mantenés apretada, Beto sabe que querés hablar con él.
- **Te Habla al Oído:** Beto ya sabe hablar. Al iniciar, te saluda con un cálido *"Hola, soy Beto"* y te guía si falta configurar algún permiso.
- **Sabe Pedir Permiso:** Como Beto necesita ver la pantalla y usar el micrófono para ayudarte, él mismo te lleva de la mano a las pantallas de configuración necesarias.
- **Siempre Atento:** Beto corre de fondo, lo que significa que no necesitás tener una aplicación abierta para que esté disponible.

---

## 🏗️ Arquitectura Técnica

Beto utiliza una arquitectura desacoplada basada en eventos, optimizada para la reactividad y la robustez en servicios de fondo de Android.

### 1. Sistema Nervioso: `AgentBus`
El corazón de la comunicación es el `AgentBus`, un bus de eventos reactivo basado en **Kotlin SharedFlow**. Permite que los servicios, la interfaz y el motor de IA se comuniquen sin conocerse entre sí.
- **`AgentEvent`**: Representa hechos que ya ocurrieron (ej: "Burbuja tocada", "Permisos faltantes").
- **`AgentCommand`**: Representa instrucciones (ej: "Beto, hablá", "Empezá a escuchar").

### 2. Componentes Core (Servicios)
- **`BetoForegroundService`**: Gestiona el ciclo de vida del agente. Mantiene al proceso vivo mediante una notificación persistente y coordina la salida de audio (TTS).
- **`BetoAccessibilityService`**: Los "ojos" de Beto. Registrado para leer el contenido de la pantalla y ejecutar gestos de forma autónoma.
- **`OverlayManager`**: Controla la `OverlayBubble`, una interfaz de tipo `SYSTEM_ALERT_WINDOW` que permite la interacción desde cualquier aplicación.

### 3. Motores de Voz
- **`TtsManager`**: Envoltorio sobre el motor nativo de Android, configurado específicamente para el locale `es-AR` para asegurar una identidad local y empática.

---

## 📁 Estructura del Proyecto y Archivos

```text
android/app/src/main/java/com/beto/app/
├── bus/
│   ├── AgentBus.kt         <-- El bus de eventos (SharedFlow).
│   └── AgentEvents.kt      <-- Definición de eventos y comandos.
├── llm/
│   └── ToolDescriptors.kt  <-- Definición de las "herramientas" que la IA podrá usar (WhatsApp, Llamadas, etc.).
├── overlay/
│   ├── OverlayBubble.kt    <-- Lógica de la burbuja flotante (drag, tap, magnetismo).
│   └── OverlayManager.kt   <-- Gestión de la ventana del sistema (WindowManager).
├── service/
│   ├── BetoForegroundService.kt    <-- Servicio principal de fondo y gestión de TTS.
│   └── BetoAccessibilityService.kt <-- Servicio de accesibilidad para lectura de pantalla.
├── voice/
│   └── TtsManager.kt       <-- Motor de voz (Texto a Voz).
├── util/
│   ├── LogTags.kt          <-- Tags centralizados para Timber (Beto-TTS, Beto-Action, etc.).
│   └── PreflightCheck.kt   <-- Validación de permisos críticos antes de arrancar.
├── BetoApplication.kt      <-- Inicialización de logs, canales de notificación y TTS.
└── MainActivity.kt         <-- Pantalla de configuración inicial y bienvenida.
```

---

## ✅ Hitos Alcanzados
- [x] Infraestructura de comunicación (`AgentBus`).
- [x] Interfaz flotante interactiva (`OverlayBubble`).
- [x] Motor de voz local (`TtsManager`) funcionando en es-AR.
- [x] Flujo de permisos automatizado (`PreflightCheck`).
- [x] Logs estructurados para debugging en tiempo real.

## 🚀 Próximos Pasos (Phase 2 & 3)
1. **Integración de STT (Speech-to-Text):** Implementar el reconocimiento de voz para que Beto "escuche".
2. **Cerebro de IA:** Conectar con Gemini 2.5 Flash usando el Firebase AI Logic SDK.
3. **Acciones Híbridas:** Implementar el disparador de Intents (WhatsApp/Llamadas) y el loop agéntico de accesibilidad.
