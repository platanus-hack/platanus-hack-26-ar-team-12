# Phase 3 Audit Fix Summary

**Date:** 2026-05-09
**Status:** Fixed after Phase 3 reanalysis

## Fixed

- Connected `VoiceCaptureActivity` STT correction to `GeminiLlmClient` instead of the no-op correction client.
- Added contact-name context to STT correction through `ContactRepository.knownContactNames()`.
- Made `GeminiLlmClient` implement `TranscriptCorrectionClient`.
- Implemented real `NeedsClarification` handling in `ActionDispatcher`:
  - contact clarification captures a response, persists alias memory, and continues execution.
  - channel clarification captures a response, persists channel memory, and continues execution.
- Fixed clarification voice-capture routing so the global dispatcher does not treat clarification answers as new top-level commands.
- Added `CALL_PHONE` permission check/request path before `ACTION_CALL`.
- Added `ACTION_DIAL` fallback when `CALL_PHONE` is missing from service context.
- Enforced channel clarification when the original transcript does not explicitly mention WhatsApp, SMS, or phone call.
- Added coverage for contact-name listing used by STT context.

## Verification

- `./gradlew testDebugUnitTest assembleDebug` passes.

## Remaining Manual Verification

- Run `docs/03-DEMO-CHECK.md` on the demo device, especially contact/channel clarification loops.
