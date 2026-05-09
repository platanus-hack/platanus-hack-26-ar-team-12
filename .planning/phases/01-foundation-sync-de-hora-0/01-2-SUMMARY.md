---
phase: 01-foundation-sync-de-hora-0
plan: 02
status: completed
completed_at: 2026-05-09
---

# Plan 01-2 Summary

## Completed

- AgentBus, AgentEvents, ToolDescriptors, LogTags were already implemented in commit `2f50986`.
- BetoApplication and TtsManager were already implemented in commit `f688481`.
- Added MainActivity launcher preflight flow.
- Added PreflightCheck for overlay, accessibility, TTS readiness, and microphone runtime permission.
- Added BetoAccessibilityService skeleton that emits ServiceStarted/ServiceStopped and logs accessibility events.

## Verification

- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin` passes.
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug` passes.

## Notes

- Added microphone runtime permission to preflight even though the original plan listed only overlay/accessibility/TTS. This is required because the foreground service declares `foregroundServiceType="microphone"` and modern Android can reject startup without `RECORD_AUDIO`.
- `android/local.properties` was created locally with `sdk.dir=/home/naprado/Android/Sdk`; it is ignored by git.
