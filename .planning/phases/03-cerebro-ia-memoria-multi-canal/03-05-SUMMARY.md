# Plan 03-05 Summary — Multi-channel action router

**Status:** Implemented
**Date:** 2026-05-09

## Delivered

- Added multi-channel `IntentBranch` actions:
  - `makeCall`
  - `sendSms`
  - `openMaps`
  - `sendWhatsapp` overload for learned `ContactRef`
- Added `ActionRouter` for Plan C precedence and LLM decision routing.
- Added `ActionDispatcher` integrating:
  - deterministic matcher first
  - `LlmClient`
  - `ContactRepository`
  - `UserMemoryStore`
  - `ContactClarifier`
  - `ChannelClarifier`
  - `IntentBranch`
- Rewired `BetoForegroundService` from `PlanCController` to `ActionDispatcher`.
- Added `ContactClarifier` with max 3 attempts and memory persistence.
- Added `ChannelClarifier` with max 3 attempts and memory persistence.
- Added `SuspendableVoiceCapture` and runtime `AgentBusVoiceCapture`.
- Added JVM tests for router, contact clarification, and channel clarification.
- Fixed the stale `IntentBranchTest` expected WhatsApp URI to match its fixture.
- Added `docs/03-DEMO-CHECK.md` for the physical smoke-test scenarios.

## Verification

- `./gradlew testDebugUnitTest --tests com.beto.app.action.ActionRouterTest --tests com.beto.app.action.ContactClarifierTest --tests com.beto.app.action.ChannelClarifierTest` passes.
- `./gradlew assembleDebug` passes.
- Full `./gradlew testDebugUnitTest` passes.

## Notes

- Plan C still has precedence: matching demo commands do not invoke the LLM.
- SMS opens a composed SMS intent and does not auto-send.
- Runtime clarification uses `AgentBusVoiceCapture`; physical-device smoke testing is still required for the interactive voice loops.
