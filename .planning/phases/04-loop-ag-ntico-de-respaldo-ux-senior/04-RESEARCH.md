# Phase 4: Loop Agentico de Respaldo + UX Senior - Research

**Researched:** 2026-05-09
**Status:** Ready for planning

## Research Complete

Phase 4 should be planned as three tightly coupled vertical slices:

1. Accessibility-backed agentic fallback for one controlled WhatsApp scenario.
2. Bubble state system with 5 visible states and 200ms transitions.
3. Senior UX system: shared typography/color tokens and exact phrase bank.

The risky part is not "AI orchestration" in the abstract; it is local Android execution against a noisy Accessibility tree. The LLM should be constrained to a structured one-action decision, while Kotlin owns every guardrail and every `performAction()` call.

## Key Findings

### 1. Accessibility execution must stay local and defensive

Android's `AccessibilityNodeInfo.performAction()` can only be performed from an `AccessibilityService`, returns whether the action was performed, and can throw if called outside that service context. `AccessibilityNodeInfo.refresh()` returns false when the represented view is obsolete. This directly supports the Phase 4 rule: every action must resolve a live node ref and refresh it immediately before execution.

Planning implications:

- `BetoAccessibilityService` should expose small service-owned methods for root tree snapshot and action execution.
- `NodeRefRegistry` must be rebuilt per snapshot. Refs like `@e1` are not durable across iterations.
- `AgentLoop` should never hold a stale node and execute it later without lookup + `refresh()`.
- `TYPE` should use `ACTION_SET_TEXT` with a bundle, not keyboard simulation.
- `SCROLL` should use node scroll actions where available; `BACK` should use controlled global back only when in the fallback loop.

### 2. Firebase AI Logic structured output is enough; do not add a server agent framework

Firebase AI Logic supports Android/Kotlin client SDK usage and structured output through `responseMimeType = "application/json"` plus `responseSchema`. Gemini API docs also support function calling, but Phase 4 does not need automatic function execution. The safer path is a structured decision object that local Kotlin validates and executes manually.

Planning implications:

- Create `AgenticDecision` as a Kotlin serializable model:
  - `plan: String`
  - `action: CLICK | TYPE | SCROLL | BACK | ABORT`
  - `nodeRef: String?`
  - `text: String?`
  - `reason: String`
- Use strict `Json { ignoreUnknownKeys = false }`.
- On malformed JSON/schema violation: one repair retry, then abort warmly.
- Keep `temperature = 0f`; keep output small (`maxOutputTokens` around 512 unless implementation finds Firebase API constraints).
- Prefer a dedicated `GeminiAgenticDecider` or method on existing `GeminiLlmClient`, but keep execution outside the LLM client.

### 3. Function calling should remain conceptually separated from UI action execution

Gemini function calling is designed for models to suggest external function calls. It supports modes such as `AUTO`, `ANY`, `NONE`, and preview `VALIDATED`; docs recommend clear function/parameter descriptions, descriptive names, and strong typing.

For this phase, using Firebase structured output avoids mixing Gemini tool-calling semantics with Android `AccessibilityService` actions. The project-level `agentic_perform_action` tool can remain the dispatcher-level concept, while the per-iteration loop consumes `AgenticDecision` JSON.

Planning implications:

- Do not let the model directly call multiple tools or submit multiple actions.
- One iteration = one structured decision = one local action.
- `agentic_perform_action(goal)` can start the loop, but the loop's internal decisions are not externally exposed tools.
- Never include a `SEND` action in schema, prompt, code enum, or phrase bank.

### 4. Tree serialization is the main context-control mechanism

Accessibility trees can be large, noisy, and inconsistent across WhatsApp versions/devices. Requirements already cap to 50 nodes. The serializer should make the tree useful for contact/search fallback, not complete.

Planning implications:

- Filter to nodes where `isVisibleToUser` and at least one is true: clickable, longClickable, focusable, editable, scrollable, nonblank text, nonblank contentDescription.
- Preserve enough metadata for safe decisions: ref, className simplified, text/contentDescription/resourceId, booleans, maybe coarse bounds.
- Redact DNI/phone/card-like strings before sending tree text to Gemini or logs.
- Prioritize nodes with WhatsApp-relevant strings: search, contact rows, message field, compose field, "mi nieto", user-provided contact name.
- Hash the serialized tree string after normalization for stuck detection.

### 5. Fallback trigger should be explicit and testable

The context intentionally delegated exact trigger design to planning, but locked a narrow bias. In existing code, `IntentBranch.sendWhatsapp()` returns `ActionResult.Launched` or `ActionResult.Failed`; Phase 4 can add a test hook path for controlled fallback without broadening production behavior.

Planning implications:

- Primary trigger: `ActionResult.Failed` from WhatsApp launch.
- Optional controlled demo trigger: a debug/test-only branch or injected `sendWhatsapp` failure in tests to exercise fallback.
- Do not trigger fallback merely because WhatsApp launched and foreground validation is uncertain unless the planner adds a very cheap, explicit stuck-state check.
- The first fallback user-facing phrase is fixed: "Dame un segundo, lo intento de otra forma."

### 6. Senior UX can be enforced with tokens and grep-verifiable artifacts

The Phase 4 UX requirements are implementation-friendly: define shared typography/color/phrase constants, use them from Companion/shortcut UI, and verify via tests/grep/manual demo-phone checks.

Planning implications:

- Create a Beto UI module/file even if Companion Compose implementation is minimal or still in Phase 3.
- Define `textStyleHero` with at least `28.sp` and `textStyleBody` with at least `22.sp`.
- Define high-contrast semantic state colors for idle/listening/thinking/speaking/error.
- Put exact phrases in a `PhraseBank` object; use constants rather than inline strings in fallback.
- Manual demo verification should explicitly check readability and contrast on the physical phone.

## Recommended Architecture

```text
PlanCController / ActionDispatcher
  └── on clear WhatsApp failure
      └── speaks PhraseBank.fallbackTryingAnotherWay
      └── AgentLoop.run(goal, message, contactHint)
            ├── BetoAccessibilityService.snapshot()
            │     ├── TreeSerializer.serialize(root, limit=50)
            │     └── NodeRefRegistry
            ├── GeminiAgenticDecider.decide(tree, goal, attempt)
            ├── AgenticDecisionValidator
            ├── StuckDetector(treeHash, actionSignature)
            └── BetoAccessibilityService.performValidatedAction(...)
```

## Proposed File Surfaces

### Agentic fallback

- `android/app/src/main/java/com/beto/app/agent/AgentLoop.kt`
- `android/app/src/main/java/com/beto/app/agent/AgenticDecision.kt`
- `android/app/src/main/java/com/beto/app/agent/AgenticPrompts.kt`
- `android/app/src/main/java/com/beto/app/agent/TreeSerializer.kt`
- `android/app/src/main/java/com/beto/app/agent/NodeRefRegistry.kt`
- `android/app/src/main/java/com/beto/app/agent/TreeHash.kt`
- `android/app/src/main/java/com/beto/app/llm/GeminiAgenticDecider.kt` or existing `GeminiLlmClient.kt`
- `android/app/src/main/java/com/beto/app/service/BetoAccessibilityService.kt`
- `android/app/src/main/java/com/beto/app/bus/AgentEvents.kt`
- `android/app/src/main/java/com/beto/app/action/PlanCController.kt` or Phase 3 `ActionDispatcher`

### Bubble state / UX senior

- `android/app/src/main/java/com/beto/app/overlay/BubbleState.kt`
- `android/app/src/main/java/com/beto/app/overlay/OverlayBubble.kt`
- `android/app/src/main/java/com/beto/app/overlay/OverlayManager.kt`
- `android/app/src/main/res/layout/overlay_bubble.xml`
- `android/app/src/main/res/drawable/*` for simple icons/backgrounds if needed
- `android/app/src/main/java/com/beto/app/ui/BetoTheme.kt`
- `android/app/src/main/java/com/beto/app/ui/PhraseBank.kt`

### Tests

- `android/app/src/test/java/com/beto/app/agent/TreeSerializerTest.kt`
- `android/app/src/test/java/com/beto/app/agent/AgenticDecisionTest.kt`
- `android/app/src/test/java/com/beto/app/agent/StuckDetectorTest.kt`
- `android/app/src/test/java/com/beto/app/ui/PhraseBankTest.kt`
- `android/app/src/test/java/com/beto/app/overlay/BubbleStateTest.kt`

## Verification Strategy

Code-level verification:

- Unit-test decision JSON parsing rejects unknown fields/actions.
- Unit-test stuck detector catches unchanged tree hash and repeated action/ref.
- Unit-test TreeSerializer filters and caps nodes.
- Unit-test phrase bank has exact warm phrases and no technical strings.
- Unit-test bubble state model has exactly 5 states and expected semantic mapping.

Build verification:

```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Manual demo-phone verification:

- Force WhatsApp failure/fallback path in controlled scenario.
- Confirm Beto says "Dame un segundo, lo intento de otra forma."
- Confirm fallback runs max 5 iterations / 15 seconds.
- Confirm 3 failed contact attempts end with warm failure and bubble idle.
- Confirm message is filled, not sent.
- Confirm bubble transitions among idle/listening/thinking/speaking/error within 200ms perceived/logged.
- Confirm Companion/shortcut UI uses readable 22sp+ body text and high contrast on the demo phone.

## Planning Guidance

Plan this phase in parallel-friendly slices:

1. **Agent contracts and serializers**: decision model, tree serializer, registry, stuck detector, tests. This can happen without touching overlay UI.
2. **Agent loop integration**: AccessibilityService action execution, Gemini decider, fallback trigger wiring, phrase bank usage.
3. **Bubble state + senior UX**: overlay visual states, shared UI tokens, phrase bank, manual verification hooks.

The agent loop should not be planned as a broad "operate any app" feature. The plan must keep the scope constrained to WhatsApp fallback and the controlled contact/search scenario from CONTEXT.md.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| WhatsApp accessibility tree differs on demo phone | Keep scenario narrow, prefer visible text/search/contact fields, manually smoke-test early. |
| Gemini returns malformed JSON | Use response schema + strict parser + one repair retry + abort. |
| Loop visibly repeats | Tree hash + repeated action/ref abort, plus hard iteration/time limits. |
| Model clicks send | No send action in enum, block nodes that look like send, success is field filled only. |
| UI work gets treated as polish | Make bubble states and senior tokens first-class plan requirements, not cleanup. |

## Sources

- Android Developers — `AccessibilityNodeInfo`: `performAction()`, `refresh()`, `ACTION_SET_TEXT`, scroll actions: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo
- Firebase AI Logic overview: https://firebase.google.com/docs/ai-logic
- Firebase AI Logic Android get started: https://firebase.google.com/docs/ai-logic/get-started
- Firebase AI Logic structured output for Android: https://firebase.google.com/docs/ai-logic/generate-structured-output?platform=android
- Gemini API function calling: https://ai.google.dev/gemini-api/docs/function-calling
- `.planning/phases/04-loop-ag-ntico-de-respaldo-ux-senior/04-CONTEXT.md`
- `.planning/phases/04-loop-ag-ntico-de-respaldo-ux-senior/04-AI-SPEC.md`

## RESEARCH COMPLETE
