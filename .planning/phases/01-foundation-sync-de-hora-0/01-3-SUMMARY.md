---
phase: 01-foundation-sync-de-hora-0
plan: 03
status: completed
completed_at: 2026-05-09
---

# Plan 01-3 Summary

## Completed

- Added BetoForegroundService with persistent notification, 3-arg `startForeground(..., FOREGROUND_SERVICE_TYPE_MICROPHONE)`, `START_STICKY`, boot greeting, BootCompleted emission, and AgentCommand.Speak collector.
- Added OverlayManager with WindowManager attach/remove, initial right-edge midpoint positioning, `TYPE_ACCESSIBILITY_OVERLAY` when accessibility is enabled, and `TYPE_APPLICATION_OVERLAY` fallback.
- Added OverlayBubble touch handling for drag, edge magnet, tap emission, long-press emission, and haptic feedback.
- Added `overlay_bubble.xml` and `bubble_background.xml` using the existing `project_logo` inside a 64dp circular bubble.
- Fixed two setup blockers discovered during execution:
  - Firebase BoM updated to `34.7.0` so `com.google.firebase:firebase-ai` resolves through the BoM.
  - Notification vector tint changed from unresolved `?attr/colorControlNormal` to `@android:color/white`.

## Verification

- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:compileDebugKotlin` passes.
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug` passes.
- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 13M.

## Physical Smoke Test Pending

- Install APK on the demo phone.
- Grant overlay, accessibility, and microphone permissions.
- Confirm Beto says: `Hola, soy Beto. Estoy acá para ayudarte.`
- Confirm notification says `Beto está acá` and stays active.
- Confirm bubble appears over WhatsApp, Maps, and home screen.
- Confirm drag magnet, tap `BubbleTapped`, and long-press `BubbleLongPressed` in Logcat.

## Phase 2 Handoff

- VoiceCaptureActivity can subscribe to `AgentEvent.BubbleTapped` from BetoForegroundService and launch a transparent capture intent.
- Phase 3 companion mode can subscribe to `AgentEvent.BubbleLongPressed`.
