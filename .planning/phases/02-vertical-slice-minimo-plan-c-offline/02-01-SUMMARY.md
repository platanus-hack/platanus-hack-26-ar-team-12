---
phase: 02-vertical-slice-minimo-plan-c-offline
plan: 02-01
status: completed
completed_at: 2026-05-09
---

# Plan 02-01 Summary

## Completed

- Added deterministic Plan C matcher for `mandale/avisale/decile/dile/escribile` demo phrases.
- Added hardcoded demo contact `Mi nieto` with aliases `nieto` and `mi nieto`.
- Added strict regular WhatsApp prefill intent spec and runtime launch path targeting `com.whatsapp`.
- Added transparent `VoiceCaptureActivity` using `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, `es-AR`, and one final transcript.
- Wired bubble tap through AgentBus into BetoForegroundService, voice capture, matcher, WhatsApp prefill, and locked TTS phrases.

## Verification

- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew testDebugUnitTest` passes.
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug` passes.

## Notes

- WhatsApp success is defined as accepted intent launch. There is no auto-send and no Accessibility click automation.
- Unit tests cover the demo phrase family and WhatsApp URI/package construction.
