# Plan 03-03 Summary — Acceso a contactos del sistema

**Status:** Implemented
**Date:** 2026-05-09

## Delivered

- Added `ContactInfo`, `PhoneNumber`, `PhoneType`, and `String.toE164()`.
- Added `ContactRepository` backed by `ContactsContract`:
  - `resolve(name)` uses `ContactsContract.Contacts.CONTENT_FILTER_URI`.
  - `findByPhone(phone)` uses `PhoneLookup` and verifies an exact number match.
  - `hasWhatsApp` checks the WhatsApp profile mimetype.
  - `hasEmail` checks email rows in `ContactsContract.Data`.
- Added graceful no-permission behavior:
  - If `READ_CONTACTS` is missing, lookup falls back to `DemoContacts`.
  - `MainActivity` shows a simple contacts permission onboarding after critical preflight passes.
  - Denied/skip persists limited mode and starts Beto without crashing.
- Added unit coverage for single/multiple/no matches, denied fallback, WhatsApp flag, and reverse phone lookup.

## Verification

- `./gradlew assembleDebug` passes.
- `./gradlew testDebugUnitTest --tests com.beto.app.contacts.ContactRepositoryTest` passes.
- Full `testDebugUnitTest` still fails in pre-existing `IntentBranchTest.buildsWaMeUri`; untouched because it is outside 03-03.
