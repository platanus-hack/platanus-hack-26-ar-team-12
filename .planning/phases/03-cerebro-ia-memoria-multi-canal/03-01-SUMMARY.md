# Plan 03-01 Summary — Gemini client + structured tool calling

**Status:** Implemented
**Date:** 2026-05-09

## Delivered

- Added `LlmClient` and `GeminiLlmClient`.
- Added structured `Decision` models:
  - `tool_call`
  - `needs_clarification`
  - `unknown`
- Added strict `DecisionJson` parsing with allow-list validation for:
  - `send_whatsapp`
  - `make_call`
  - `send_sms`
  - `open_maps`
- Added `PromptBuilder` with Argentine Spanish instructions, voseo guidance, tool descriptions, schema examples, and few-shots.
- Added `Sanitizer` at the LLM boundary for DNI, Argentine phone numbers, and cards.
- Added `LlmCache` with SHA-256 keys, LRU eviction, TTL, and coroutine-safe access.
- Extended `ToolDescriptors` with allowed tools and Spanish descriptors.
- Added tests for sanitizer, prompt/model serialization, and cache behavior.

## Verification

- `./gradlew testDebugUnitTest --tests com.beto.app.llm.SanitizerTest --tests com.beto.app.llm.PromptBuilderTest --tests com.beto.app.llm.LlmCacheTest` passes.
- `./gradlew assembleDebug` passes.
- `rg "generateContent\\(" android/app/src/main/java` shows generation only through `GeminiLlmClient`.
- Full `./gradlew testDebugUnitTest` still fails in pre-existing `IntentBranchTest.buildsWaMeUri`; untouched because it is outside 03-01.

## Notes

- `GeminiLlmClient` applies `Sanitizer.sanitize(rawTranscript)` before cache lookup and prompt construction.
- Malformed or disallowed LLM output retries once, then falls back to `Decision.Unknown`.
- API-key hardening remains out of scope for hackathon demo; production should add App Check or backend mediation.
