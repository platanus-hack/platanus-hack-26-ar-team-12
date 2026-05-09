# Phase 2: Vertical Slice Minimo (Plan C Offline) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-09
**Phase:** 2-Vertical Slice Minimo (Plan C Offline)
**Areas discussed:** STT handoff, Matcher + contacts, WhatsApp execution, TTS + failure tone

---

## STT Handoff

| Question | Selected | Alternatives Considered |
|----------|----------|-------------------------|
| Capture approach | Transparent `VoiceCaptureActivity` with Android recognizer UI | Custom invisible `SpeechRecognizer`; planner discretion |
| Empty/unusable result | One warm retry, then stop cleanly | Fail immediately; planner discretion |
| Transcript events | Best final transcript only | Partial + final transcripts; planner discretion |
| 3s measurement | Tap to final transcript under 3s | Tap to WhatsApp open under 3s; no instrumentation yet |

**Notes:** Reliability and speed were prioritized over a custom invisible listening surface.

---

## Matcher + Contacts

| Question | Selected | Alternatives Considered |
|----------|----------|-------------------------|
| Matcher breadth | Demo phrase family only | All WhatsApp-like phrases; exact phrase only |
| Contact representation | Hardcoded alias map | Android Contacts lookup; single literal contact key |
| Message extraction | Regex/rules for multiple Spanish/Argentine verbs and connectors | Everything after `que`; hardcoded demo message |
| Ambiguous close match | One short voice clarification | Fail warmly; infer demo defaults |

**Notes:** User proposed relationship learning through contacts. It was marked deferred because it is a new capability outside Phase 2.

---

## WhatsApp Execution

| Question | Selected | Alternatives Considered |
|----------|----------|-------------------------|
| Execution behavior | Open WhatsApp prefilled only | Auto-send; app chooser |
| Package targeting | Strict `com.whatsapp` | WhatsApp Business fallback; generic `ACTION_VIEW` |
| Success definition | Intent launched without exception | WhatsApp foreground detected; message field detected |
| Failure behavior | TTS failure only, then stop | Open Play Store/settings; trigger agentic fallback |

**Notes:** Phase 2 intentionally avoids Accessibility automation and WhatsApp Business branches.

---

## TTS + Failure Tone

| Question | Selected | Alternatives Considered |
|----------|----------|-------------------------|
| Pre-launch confirmation | "Abro WhatsApp con el mensaje para tu nieto." | "Te dejo el mensaje listo para tu nieto, dale."; dynamic contact/message phrase |
| Success phrase | "Listo, te deje el mensaje preparado." | "Listo, ya esta."; no success phrase |
| WhatsApp failure phrase | "No pude abrir WhatsApp. Probemos de nuevo en un ratito, dale." | "No encontre WhatsApp instalado."; dynamic error phrase |
| STT/matcher final failure | "Perdon, no te entendi bien. Probemos de nuevo, dale." | "No entendi el comando."; separate phrases |
| ElevenLabs credits | Keep Android native TTS only | Pregenerated ElevenLabs clips; live ElevenLabs TTS |

**Notes:** User flagged that "Le aviso..." felt weird because Phase 2 does not auto-send. Wording was revised to avoid implying the message was sent.

---

## Claude's Discretion

- Exact regex implementation and normalization details.
- Exact event class names, as long as they follow Phase 1 `AgentBus` contracts.
- Lightweight latency instrumentation shape.

## Deferred Ideas

- Relationship/contact learning: search contacts, ask who "nieto" refers to, suggest matches, and save the alias for future commands.
- ElevenLabs voice generation for later polish, outside this offline Plan C phase.
- Auto-send and Accessibility-based proof/fallback for later phases.
