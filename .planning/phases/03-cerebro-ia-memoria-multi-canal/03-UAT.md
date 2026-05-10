---
status: testing
phase: 03-cerebro-ia-memoria-multi-canal
source:
  - .planning/phases/03-cerebro-ia-memoria-multi-canal/03-01-SUMMARY.md
  - .planning/phases/03-cerebro-ia-memoria-multi-canal/03-02-SUMMARY.md
  - .planning/phases/03-cerebro-ia-memoria-multi-canal/03-03-SUMMARY.md
  - .planning/phases/03-cerebro-ia-memoria-multi-canal/03-04-SUMMARY.md
  - .planning/phases/03-cerebro-ia-memoria-multi-canal/03-05-SUMMARY.md
  - .planning/phases/03-cerebro-ia-memoria-multi-canal/03-AUDIT-FIX-SUMMARY.md
started: 2026-05-09T22:22:31-03:00
updated: 2026-05-09T22:25:58-03:00
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

number: 1
name: Plan C Still Works Before AI
expected: |
  Say a known Plan C demo command. Beto should execute the deterministic demo action as before, without asking extra AI clarification.
awaiting: user response

## Tests

### 1. Plan C Still Works Before AI
expected: Say a known Plan C demo command. Beto should execute the deterministic demo action as before, without asking extra AI clarification.
result: issue
reported: "hay un error, la app no me deja pedirle nada a beto, no llega a leer"
severity: blocker

### 2. Gemini Handles Natural Spanish Commands
expected: Say a natural Spanish command outside the exact Plan C phrases, such as asking Beto to call or message a contact. Beto should understand the intent and proceed toward the correct call, SMS, WhatsApp, or maps action.
result: [pending]

### 3. Low Confidence STT Correction
expected: Say a short or slightly misrecognized command that resembles a known contact/action. Beto should correct the transcript when confidence is low, without changing the requested action or inventing a contact.
result: [pending]

### 4. Contact Permission And Lookup
expected: With contacts permission granted, asking for a saved contact should resolve that contact. If contacts permission is denied, Beto should continue in limited/demo mode instead of crashing.
result: [pending]

### 5. Contact Clarification Loop
expected: Ask for an ambiguous family/contact alias, such as "llama a mi nieto". Beto should ask how the contact is named or saved, retry up to 3 times, and stop cleanly if it cannot find the contact.
result: [pending]

### 6. Channel Clarification And Memory
expected: Ask to send a message without saying WhatsApp or SMS. Beto should ask which channel to use, remember the answer for that contact, and use the learned channel on a later similar request.
result: [pending]

### 7. Android Intent Launches
expected: For resolved requests, Beto should open the correct Android surface: dialer/call flow for phone calls, SMS composer for SMS, WhatsApp chat/link for WhatsApp, and Google Maps for map requests.
result: [pending]

## Summary

total: 7
passed: 0
issues: 1
pending: 6
skipped: 0
blocked: 0

## Gaps

- truth: "Say a known Plan C demo command. Beto should execute the deterministic demo action as before, without asking extra AI clarification."
  status: failed
  reason: "User reported: hay un error, la app no me deja pedirle nada a beto, no llega a leer"
  severity: blocker
  test: 1
  root_cause: "VoiceCaptureActivity always preferred Android's on-device SpeechRecognizer when reported available. On some devices/languages, especially es-AR without an offline recognizer pack, that path can fail before speech begins and the app emits VoiceCaptureFailed instead of falling back to the standard recognizer."
  artifacts:
    - path: "android/app/src/main/java/com/beto/app/voice/VoiceCaptureActivity.kt"
      issue: "No fallback from failing on-device recognizer to cloud-backed recognizer."
    - path: "android/app/src/main/java/com/beto/app/voice/RecognizerFactory.kt"
      issue: "Recognizer creation did not allow callers to opt out of on-device recognizer after a failure."
  missing:
    - "Retry with SpeechRecognizer.createSpeechRecognizer when on-device launch fails or emits an error before speech starts."
  debug_session: "inline-uat-03-voice-capture"
