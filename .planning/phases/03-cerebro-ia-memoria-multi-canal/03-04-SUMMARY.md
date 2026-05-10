# Plan 03-04 Summary — User memory

**Status:** Implemented
**Date:** 2026-05-09

## Delivered

- Added `androidx.security:security-crypto` dependency.
- Added serializable memory model:
  - `UserMemory`
  - `ContactRef`
  - `Channel`
- Added `UserMemoryStore`:
  - Real Android path uses `EncryptedSharedPreferences`.
  - Persists a single `user_memory_v1` JSON.
  - Keeps cached state in memory.
  - Exposes synchronous reads: `current`, `knowsAlias`, `resolveAlias`, `preferredChannel`.
  - Exposes suspend writes guarded by `Mutex`: `recordAlias`, `recordChannel`, `recordFact`, `clear`.
  - Falls back to `UserMemory.empty()` on corrupt JSON.
- Initialized `BetoApplication.userMemoryStore`.
- Added `Beto-Memory` log tag.
- Added JVM tests with fake `SharedPreferences` for model and store behavior.

## Verification

- `./gradlew testDebugUnitTest --tests com.beto.app.memory.UserMemoryTest --tests com.beto.app.memory.UserMemoryStoreTest` passes.
- `./gradlew assembleDebug` passes.
- Full `./gradlew testDebugUnitTest` still fails in pre-existing `IntentBranchTest.buildsWaMeUri`; untouched because it is outside 03-04.

## Notes

- `EncryptedSharedPreferences` / `MasterKey` compile with deprecation warnings in the current AndroidX Security artifact, but the plan explicitly requires this storage mechanism.
- Store tests use injected fake `SharedPreferences`; encrypted persistence should still be smoke-tested on a device with `adb shell run-as com.beto.app ls shared_prefs`.
