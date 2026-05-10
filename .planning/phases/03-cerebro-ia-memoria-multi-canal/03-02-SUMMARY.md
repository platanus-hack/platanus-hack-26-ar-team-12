# Plan 03-02 Summary — STT corrector + on-device recognizer

**Status:** Implemented
**Date:** 2026-05-09

## Delivered

- Added `RecognizerFactory`:
  - Uses `SpeechRecognizer.createOnDeviceSpeechRecognizer()` when on-device recognition is available.
  - Falls back to `SpeechRecognizer.createSpeechRecognizer()`.
  - Logs recognizer choice with `Beto-STT`.
- Updated `VoiceCaptureActivity`:
  - Uses direct `SpeechRecognizer` instead of external `RecognizerIntent` activity.
  - Keeps `es-AR`, `EXTRA_MAX_RESULTS = 1`, and sets `EXTRA_PREFER_OFFLINE` for on-device path.
  - Reads confidence scores from recognition results.
  - Applies correction only for low-confidence transcripts or short non-Plan-C matches.
  - Emits `AgentEvent.SttCorrectionStarted`.
  - Logs `STT_CORRECTED elapsedMs=...`.
- Added `SttCorrector`:
  - Builds the STT correction prompt.
  - Calls an injectable `TranscriptCorrectionClient`.
  - Enforces 1.5s timeout.
  - Parses strict `{"corrected":"..."}` JSON.
  - Falls back to raw text on timeout, malformed JSON, unsafe verb changes, or unsafe contact invention.
- Added `SttCorrectorTest` covering phonetic contact correction, no invented contacts, verb preservation, timeout, and malformed JSON.

## Conflict Avoidance

`03-01` owns `llm/LlmClient.kt`, `GeminiLlmClient.kt`, and `llm/PromptBuilder.kt`. To avoid merge conflicts with the teammate implementing `03-01`, this plan exposes `TranscriptCorrectionClient` in `voice` and leaves the Gemini adapter as the small integration step once `03-01` lands.

## Verification

- `./gradlew testDebugUnitTest --tests com.beto.app.voice.SttCorrectorTest` passes.
- `./gradlew assembleDebug` passes.
- Full `./gradlew testDebugUnitTest` still fails in pre-existing `IntentBranchTest.buildsWaMeUri`; untouched because it is outside 03-02.
