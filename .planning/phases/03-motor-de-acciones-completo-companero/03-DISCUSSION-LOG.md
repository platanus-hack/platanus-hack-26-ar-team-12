# Phase 3: Motor de Acciones Completo + Companero - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-05-09
**Phase:** 3-Motor de Acciones Completo + Companero
**Areas discussed:** LLM routing contract, Modo Companero personality + UI

---

## Gray Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| LLM routing contract | Which commands stay deterministic, when Gemini gets called, and what happens if tool JSON is malformed. | yes |
| Privacy sanitizer behavior | What gets redacted, what logs must prove it, and whether Beto mentions redaction. | |
| Call / SMS / Maps demo behavior | Whether actions execute immediately, require confirmation, or only prepare. | |
| Modo Companero personality + UI | Long-press chat behavior, sheet contents, tone, and interaction style. | yes |
| All of them | Discuss every area. | |

**User's choice:** `1,4`
**Notes:** User chose to discuss routing and Companion, not sanitizer or action execution policy in this pass.

---

## LLM Routing Contract

| Option | Description | Selected |
|--------|-------------|----------|
| Deterministic first for demo commands | Regex/matcher handles top demo phrases before Gemini; Gemini handles arbitrary phrasing and non-exact variants. | yes |
| Gemini first for all Phase 3 commands | Every command goes through tool calling, with deterministic fallback only if Gemini/network fails. | |
| Hybrid by confidence | Deterministic matcher wins only on high-confidence exact/near-exact matches; otherwise Gemini routes. | |

**User's choice:** Deterministic first for demo commands.
**Notes:** Locked for hackathon reliability.

| Option | Description | Selected |
|--------|-------------|----------|
| Retry once, then fallback deterministic | Use `temperature: 0`, retry tool call once with a repair prompt, then try deterministic matcher/cache before failing warmly. | yes |
| No retry, fail warmly | Treat malformed tool output as an LLM failure and tell the user to try again. | |
| Retry once, then ask clarification | If repair fails, ask one short clarifying question instead of attempting fallback. | |

**User's choice:** Retry once, then safe fallback.
**Notes:** User clarified that fallback must never substitute contacts. Example: "llama a Pedro" must not become "llama a hijo" if Pedro fails. Locked: fallback only when the original text has the same explicit action/contact/destination/message semantics.

| Option | Description | Selected |
|--------|-------------|----------|
| DemoContacts only | Resolve only seeded aliases; unknown contacts ask/fail warmly. | |
| DemoContacts first, Android Contacts second | Seeded demo aliases win; unknown names query Android Contacts and ask on ambiguity. | yes |
| Android Contacts first | Treat the phone contacts app as source of truth, with DemoContacts only as fallback. | |

**User's choice:** DemoContacts first, Android Contacts second.
**Notes:** Arbitrary names like Pedro may resolve through Android Contacts; zero or multiple matches must not be guessed.

| Option | Description | Selected |
|--------|-------------|----------|
| No agentic tool in Phase 3 | Gemini may only call fixed tools: WhatsApp, call, SMS, Maps. Agentic fallback waits for Phase 4. | |
| Descriptor exists but disabled | Tool schema can define `agentic_perform_action`, but Phase 3 dispatcher rejects it with warm failure/log. | yes |
| Allowed only after fixed tool failure | Gemini can request agentic fallback, but dispatcher only runs it if a fixed Intent failed. | |

**User's choice:** Descriptor exists but disabled.
**Notes:** Shared contract can include `agentic_perform_action`, but Phase 3 allow-list remains fixed tools only.

---

## Modo Companero Personality + UI

| Option | Description | Selected |
|--------|-------------|----------|
| Simple warm chat | Bottom sheet with short back-and-forth messages, no settings or extra actions. | |
| Help + chat | Large suggested prompts plus chat, so the user knows what to ask. | |
| Voice-first companion | Long-press starts a spoken conversation mode, with the sheet mainly showing transcript. | yes |

**User's choice:** Voice-first companion.
**Notes:** User also wants a minimal text bar and a vertical-bar recording animation inspired by "Hey Google". They do not want a massive button. Physical side-button activation/configuration was mentioned as desirable but deferred.

| Option | Description | Selected |
|--------|-------------|----------|
| Start listening immediately | Long-press opens the sheet and begins voice capture right away. | yes |
| Open idle, then tap/text | Sheet opens ready, but user must tap a small mic/text control. | |
| Remember last mode | If user last used voice, auto-listen; otherwise idle. | |

**User's choice:** Start listening immediately.
**Notes:** This supports the desire to keep the experience simple for elders.

| Option | Description | Selected |
|--------|-------------|----------|
| Very short and warm | 1-2 sentences max, Argentine tone, simple vocabulary, patient and calm. | |
| More conversational | Can ask follow-ups and give fuller answers when useful. | yes |
| Action-oriented | Keeps answers short but always tries to suggest a next action Beto can do. | |

**User's choice:** More conversational.
**Notes:** Tone must remain Argentine, simple but correct, patient, calm, and not boring.

| Option | Description | Selected |
|--------|-------------|----------|
| No, chat only | It can explain or suggest, but actions stay in Motor de Acciones voice commands. | |
| Yes, with confirmation | If user says an action inside Companero, it can route to tools after confirming. | yes |
| Suggest switching modes | Tell the user to say it from the bubble instead of executing. | |

**User's choice:** Yes, with confirmation.
**Notes:** User emphasized elders should not need to know Beto is switching between Companion and Actions. Internally there may be routes; externally it should feel like one Beto.

| Option | Description | Selected |
|--------|-------------|----------|
| Session-only | Keep messages in `ViewModel` while the sheet is open; discard when closed. | yes |
| Short local memory | Keep the last few messages locally during the app session, but no permanent storage. | |
| Persistent history | Save conversations across app restarts. | |

**User's choice:** Session-only.
**Notes:** User wants future personal memory for user name, family, frequent contacts and hobbies, but this is deferred because Phase 3 locks `COMP-04` session-only history.

---

## the agent's Discretion

- Exact matcher implementation and thresholds, as long as fallbacks never substitute entities.
- Exact warm failure/clarification phrases for unknown or ambiguous contacts.
- Exact visual implementation of the vertical-bar listening animation.
- Whether action confirmation from Companero is voice, text, or both.

## Deferred Ideas

- Physical side-button activation/configuration for Beto.
- Personal memory for user name, family members, frequent contacts, hobbies, and preferences with consent and privacy-safe storage.
- Persistent chat history.
- Operational agentic fallback via `agentic_perform_action` in Phase 4.
