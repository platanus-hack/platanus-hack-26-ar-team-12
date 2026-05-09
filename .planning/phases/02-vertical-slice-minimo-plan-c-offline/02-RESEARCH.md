# Phase 2: Vertical Slice Minimo (Plan C Offline) - Research

**Researched:** 2026-05-09
**Status:** Ready for planning
**Question:** What do we need to know to plan tap -> STT -> deterministic matcher -> WhatsApp prefill -> TTS without LLM or network?

## Research Summary

Phase 2 should be planned as one offline-first user journey, not as separate technical layers. The core user story is:

**As an older adult, I want to tap Beto, say "mandale a mi nieto que ya llegue", and see WhatsApp open with the right message prepared, so that I do not need to navigate the phone myself.**

The safest implementation path is:

1. Reuse Phase 1's overlay tap and `AgentBus` contracts.
2. Launch a transparent `VoiceCaptureActivity` for `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`.
3. Emit one final `VoiceCaptured(text)` event.
4. Run `DeterministicMatcher` before any LLM path.
5. Resolve "nieto" through hardcoded `DemoContacts`.
6. Build a strict `com.whatsapp` intent with a URL-encoded `wa.me` message.
7. Speak fixed Android TTS phrases through the already-initialized `TtsManager`.

## Official Android Findings

### Speech Recognition

Android's `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` is an Activity-based flow. The docs state that it prompts the user for speech and returns results via the Activity result path, and that starting it without `startActivityForResult`/activity-result handling is unsupported. It also requires `EXTRA_LANGUAGE_MODEL`; `EXTRA_LANGUAGE`, `EXTRA_MAX_RESULTS`, and `EXTRA_PROMPT` are optional. For Phase 2, this supports a transparent `VoiceCaptureActivity` host rather than trying to start recognition directly from the service.

Important implication: Android's recognizer implementation may stream audio remotely. The Phase 2 product contract is "no LLM and no app-owned network dependency"; the demo phone must still be prepared so the native recognizer works in the expected mode. The plan should validate the exact demo device in airplane-mode conditions because native STT offline behavior depends on installed language packs / recognizer implementation.

### TextToSpeech

Android `TextToSpeech` is asynchronous. The docs are explicit that a TTS instance should only synthesize after initialization completes via `TextToSpeech.OnInitListener`. `speak()` queues work asynchronously and returns whether queuing succeeded, not whether audio finished. For Phase 2, all user-facing voice feedback should route through the Phase 1 `TtsManager` queue and use utterance IDs/logging where cheap.

### Intent Launching

Android `Intent.ACTION_VIEW` with a `Uri` is the correct platform primitive for opening a deep link in another app. The intent should be package-targeted with `setPackage("com.whatsapp")` to avoid chooser behavior and WhatsApp Business ambiguity. Phone numbers must be normalized to digits-only international format before building the `wa.me` URI; the message must be URL encoded.

## Phase-Specific Implementation Guidance

### VoiceCaptureActivity

Recommended concrete behavior:

- Theme: transparent/no title; finishes immediately after result/cancel/error.
- Intent extras:
  - `RecognizerIntent.EXTRA_LANGUAGE_MODEL = RecognizerIntent.LANGUAGE_MODEL_FREE_FORM`
  - `RecognizerIntent.EXTRA_LANGUAGE = "es-AR"`
  - `RecognizerIntent.EXTRA_MAX_RESULTS = 1`
  - optional prompt text should be short or omitted to reduce visual noise.
- Result handling:
  - extract `RecognizerIntent.EXTRA_RESULTS`, take first non-blank result.
  - normalize whitespace but preserve actual words for matcher.
  - emit `AgentEvent.VoiceCaptured(text, elapsedMs)` or equivalent.
  - emit a failure/cancel event for no match/cancel so the retry controller can decide.

### Flow Controller

Avoid spreading retry state across Activity, overlay, matcher, and action code. Add a small `PlanCController` / `OfflineActionController` owned by the foreground service or action module:

- On `BubbleTapped`: start voice capture and record tap timestamp.
- On `VoiceCaptured`: log tap-to-transcript latency, pass text to matcher.
- On empty/cancel/no usable text: speak retry phrase once and relaunch capture once.
- On match success: speak pre-launch phrase, then dispatch WhatsApp intent.
- On close ambiguous match: ask one clarification, capture one follow-up, merge into pending command, and retry matcher once.
- On exhausted failure: speak final failure phrase.

This keeps `VoiceCaptureActivity` dumb and prevents hidden loops.

### DeterministicMatcher

The matcher should be intentionally narrow:

- Normalize to lowercase, remove diacritics, collapse whitespace, strip punctuation.
- Recognize verbs: `mandale`, `manda`, `mandar`, `avisale`, `avisa`, `decile`, `dile`, `escribile`.
- Recognize recipient markers: `a mi nieto`, `al nieto`, `a {alias}`.
- Recognize message connectors: `que`, `diciendo`, `con el mensaje`, `para decirle`, `de que`.
- Return a typed result, not just a boolean:
  - `Matched(contactAlias, message)`
  - `NeedsContact(message?)`
  - `NeedsMessage(contactAlias?)`
  - `NoMatch`

Keep this class pure and unit-testable. It is the main risk reducer for `LLM-04`.

### DemoContacts

Use a hardcoded map, not Android Contacts:

```kotlin
data class DemoContact(val canonicalName: String, val e164: String, val aliases: Set<String>)
```

The E.164 value for a WhatsApp `wa.me` path should be stored or normalized as digits only when used in the URL. Avoid `+`, spaces, parentheses, and dashes in the final URI.

### WhatsApp IntentBranch

Recommended minimal API:

```kotlin
sealed class ActionResult {
  data object Launched : ActionResult()
  data class Failed(val reason: Reason, val throwable: Throwable? = null) : ActionResult()
}

fun sendWhatsapp(contact: DemoContact, message: String): ActionResult
```

Implementation notes:

- Build `Uri.parse("https://wa.me/${digitsOnlyPhone}?text=${Uri.encode(message)}")` or use `Uri.Builder`.
- Use `Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)`.
- Wrap `startActivity` in `try/catch`.
- Success for this phase is "no exception while launching".
- Failure speaks the locked phrase; do not trigger agentic fallback in Phase 2.

### TTS Phrases

The exact strings from `02-CONTEXT.md` are part of the plan contract:

- Before launch: "Abro WhatsApp con el mensaje para tu nieto."
- Success: "Listo, te deje el mensaje preparado."
- WhatsApp failure: "No pude abrir WhatsApp. Probemos de nuevo en un ratito, dale."
- STT/matcher final failure: "Perdon, no te entendi bien. Probemos de nuevo, dale."
- STT empty retry: "No te escuche bien. Probemos de nuevo, dale."

Phase 2 uses Android native TTS only. ElevenLabs is explicitly deferred.

## Validation Strategy

### Unit Tests

Add JVM unit tests for:

- phrase variants around `mandale/avisale/decile/dile`.
- message extraction from multiple connectors.
- alias resolution for `nieto`, `mi nieto`, and real demo contact name.
- ambiguous cases: missing contact, missing message.
- WhatsApp URI builder encodes text and strips non-digits from phone.

### Manual Device Tests

The real success criteria require a device:

- Tap bubble -> recognizer appears -> final transcript emitted under 3s.
- "mandale a mi nieto que ya llegue" opens regular WhatsApp with prefilled message.
- Airplane-mode rehearsal on the exact demo phone verifies the Plan C path. If native STT does not work offline on that phone, record it as a demo setup requirement: install/download Spanish recognizer language data or run with radio state that keeps Android STT available while no LLM/network code is used.
- Missing WhatsApp or bad phone path speaks the failure phrase and stops.

### Logging

Use existing tags:

- `Beto-STT`: capture start, result text, elapsed ms, retry count.
- `Beto-Action`: matched action and dispatch result.
- `Beto-Intent`: exact sanitized WhatsApp URI / package target.
- `Beto-TTS`: phrase keys and utterance IDs.

## Planning Risks

| Risk | Plan Mitigation |
|------|-----------------|
| Phase 1 files do not exist when Phase 2 starts | Plans must list Phase 1 files in `read_first` and treat missing contracts as blockers to resolve from Phase 1 summary/context. |
| Android recognizer offline behavior varies by device | Include manual device validation and setup note; do not introduce cloud STT or LLM. |
| Matcher becomes too broad and unreliable | Keep demo-family scope; unit tests should encode allowed variants and no-match behavior. |
| WhatsApp opens chooser or Business | Force `setPackage("com.whatsapp")` and fail warmly if unavailable. |
| TTS wording implies auto-send | Use the exact locked phrases from context. |

## Sources

- Android Developers: `RecognizerIntent` API reference — `ACTION_RECOGNIZE_SPEECH`, required `EXTRA_LANGUAGE_MODEL`, optional `EXTRA_LANGUAGE`, result delivery constraints. https://developer.android.com/reference/kotlin/android/speech/RecognizerIntent
- Android Developers: `TextToSpeech` API reference — initialization requirement and asynchronous `speak()` behavior. https://developer.android.com/reference/android/speech/tts/TextToSpeech
- Android Developers: `TextToSpeech.OnInitListener` API reference — `onInit(status)` reports `SUCCESS` or `ERROR`. https://developer.android.com/reference/android/speech/tts/TextToSpeech.OnInitListener
- Android Developers: `Intent` API reference — `ACTION_VIEW` with URI and package/component targeting behavior. https://developer.android.com/reference/android/content/Intent
- Project context: `.planning/phases/02-vertical-slice-minimo-plan-c-offline/02-CONTEXT.md`
- Prior context: `.planning/phases/01-foundation-sync-de-hora-0/01-CONTEXT.md`
