# Beto: Multimodal AI Agent for Seniors

Beto is an autonomous multimodal AI agent for Android designed as a "copilot" for adults seniors. It "sees" the screen using `AccessibilityService`, "hears" voice commands, and performs actions on behalf of the user (sending messages, making calls, etc.) with a warm and patient tone.

## Project Overview

- **Core Mission:** Bridge the digital gap for seniors by allowing them to operate their phones using natural voice commands in Argentine Spanish (es-AR).
- **Architecture:** 
    - **Hybrid Action Engine:** Combines reliable Android Intents (for top-N actions like WhatsApp, Calls, Maps) with a fallback agentic loop (Accessibility Tree analysis + LLM tool calling).
    - **Event-Driven:** Uses `AgentBus` for communication between the floating bubble UI, the Foreground Service (microphone), and the Accessibility Service (context/actions).
    - **Cloud-Powered Agent:** Uses Gemini 2.5 Flash via the Firebase AI Logic SDK for intention classification and tool calling.
- **Key UI:** A persistent floating bubble (`SYSTEM_ALERT_WINDOW`) as the primary entry point.

## Technical Stack

- **Platform:** Android Native (Kotlin 2.1.10, minSdk 31, targetSdk 34)
- **Build System:** Gradle 8.10 with AGP 8.7.3
- **LLM:** Gemini 2.5 Flash (via `com.google.firebase:firebase-ai`)
- **Voice:** Native Android `SpeechRecognizer` (STT) and `TextToSpeech` (TTS)
- **UI:** Android Views (for the floating bubble) and Jetpack Compose (for the Companion chat sheet)
- **Concurrency:** Kotlin Coroutines & Flow
- **Logging:** Timber with specific tags (`Beto-XXX`)

## Building and Running

### Prerequisites
- Android Studio Ladybug or newer.
- A physical Android device (API 31+) is highly recommended for `AccessibilityService` and `SYSTEM_ALERT_WINDOW` testing.

### Commands
- **Build Debug APK:** `./gradlew assembleDebug`
- **Install on Device:** `./gradlew installDebug`
- **View Logs:** `adb logcat -s "Beto-Accessibility:D" "Beto-LLM:D" "Beto-Action:D" "Beto-TTS:D" "Beto-Voice:D"`

### Manual Setup
After installation, you MUST manually grant the following permissions on the device:
1. **Accessibility Service:** Settings -> Accessibility -> Beto (Turn ON).
2. **Display over other apps:** Settings -> Apps -> Beto -> Display over other apps (Allow).
3. **Microphone/Contacts/Phone:** Grant standard runtime permissions when prompted (or via App Info).

## Development Conventions

### 1. TONE & EMPATHY (Critical)
Beto's persona is extremely warm, patient, and simple.
- Use simple vocabulary (no technical jargon).
- Keep responses short and direct.
- Use Argentine Spanish (es-AR) "voseo" (e.g., "Decime qué necesitás" instead of "Dime qué necesitas").

### 2. Modular Services
- `BetoAccessibilityService`: Handles screen context extraction and UI actions.
- `BetoForegroundService`: Manages the persistent notification and microphone lifecycle.
- `TtsManager`: Centralized management of voice feedback.
- `AgentBus`: All cross-component communication should happen via events defined in `AgentEvents.kt`.

### 3. Logging
Always use `Timber` with appropriate tags from `LogTags.kt`.
- `Beto-Accessibility`: For screen parsing and action execution.
- `Beto-LLM`: For prompts, tool calls, and LLM responses.
- `Beto-Voice`: For STT/TTS lifecycle.

### 4. Privacy
Apply regex-based sanitization in `PrivacyUtil` (if implemented) or manually before sending text/captures to the LLM cloud. Protect DNI, phone numbers, and credit cards.

## Key Files & Directories

- `android/app/src/main/java/com/beto/app/`:
    - `BetoApplication.kt`: Boot-time initialization (TTS, Notification Channels).
    - `bus/`: `AgentBus` and `AgentEvents` (The nervous system).
    - `llm/`: Tool definitions for Gemini.
    - `service/`: (Planned) `BetoAccessibilityService` and `BetoForegroundService`.
    - `voice/`: `TtsManager` and voice-related logic.
- `.planning/`: ROADMAP, REQUIREMENTS, and PROJECT goals.
- `CLAUDE.md`: High-level hackathon rules and personas.

## Hackathon Constraints
- **Reliability > Sophistication:** If a direct Intent can perform the task (e.g., `ACTION_SEND` for WhatsApp), use it. Reserve the agentic loop for complex/unsupported tasks.
- **Single Device Demo:** The code assumes permissions are pre-granted. No need for complex onboarding UI.
- **Stateless:** The agent is stateless between voice commands for the MVP.
