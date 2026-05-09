# Phase 4: Loop Agentico de Respaldo + UX Senior - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-09
**Phase:** 4-Loop Agentico de Respaldo + UX Senior
**Areas discussed:** Fallback trigger, Agentic safety limits, Bubble states, Senior UX system

---

## Fallback trigger

| Option | Description | Selected |
|--------|-------------|----------|
| Only explicit launch failures | Fallback only if Android throws or refuses to launch the fixed Intent. Keeps the fallback narrow and predictable. | |
| Launch failure + known stuck states | Fallback if the Intent fails, or if a controlled app opens but Beto can detect it did not reach the expected screen. More useful, but needs lightweight screen checks. | |
| Any uncertain result | Fallback whenever Beto cannot confidently prove the action succeeded. Most ambitious, highest risk for noisy/incorrect automation. | |
| You decide | Planner chooses the safest trigger based on existing code and demo constraints. | ✓ |

**User's choice:** You decide.  
**Notes:** Locked bias: trigger should be narrow and run only on clear failure.

| Option | Description | Selected |
|--------|-------------|----------|
| Say nothing | Truly silent fallback; only speak final success/failure. | |
| One soft status phrase | "Dame un segundo, lo intento de otra forma." | ✓ |
| Visual only | Bubble changes to thinking/working state, no TTS until outcome. | |
| You decide | Planner chooses based on flow timing and UX polish. | |

**User's choice:** One soft status phrase.  
**Notes:** Phrase should stay warm and non-technical.

| Option | Description | Selected |
|--------|-------------|----------|
| WhatsApp only | Safest controlled scenario, directly tied to the hero demo path. | ✓ |
| WhatsApp + SMS + Maps | Covers most action surfaces without trying calls. | |
| All fixed tools except calls | Fallback can assist send/open flows, but never tries phone-call UI. | |
| You decide | Planner picks the smallest useful surface for Phase 4. | |

**User's choice:** WhatsApp only.  
**Notes:** Avoid spreading fallback across SMS, Maps, or calls in this phase.

| Option | Description | Selected |
|--------|-------------|----------|
| Screen reached | Verify WhatsApp/message screen appears, even if it does not send. | |
| Text field filled | Verify the intended message is visible in the compose field. | ✓ |
| Message sent | Click send and verify outgoing bubble. | |
| You decide | Planner chooses strongest safe success check. | |

**User's choice:** Text field filled.  
**Notes:** Do not auto-send; preserve earlier prefill-only behavior.

---

## Agentic safety limits

| Option | Description | Selected |
|--------|-------------|----------|
| Click + type only | Enough for WhatsApp compose fallback, safest surface. | |
| Click + type + back | Adds recovery if it lands in the wrong screen. | |
| Click + type + scroll + back | More general, useful if field/button is off-screen. | ✓ |
| You decide | Planner chooses smallest needed action set. | |

**User's choice:** Click + type + scroll + back.  
**Notes:** Still limited to WhatsApp fallback and hard limits.

| Option | Description | Selected |
|--------|-------------|----------|
| Strict tree-hash abort | Abort if the tree does not change across 2 iterations. | |
| Tree-hash + repeated action abort | Abort if tree is unchanged or the loop repeats same action/ref twice. | ✓ |
| Planner decides | Implement reliable stuck detector from available Accessibility data. | |
| Manual timeout only | Rely on 5 iterations / 15 seconds. | |

**User's choice:** Tree-hash + repeated action abort.  
**Notes:** Prevent visible looping.

| Option | Description | Selected |
|--------|-------------|----------|
| One action per turn | LLM returns exactly one action from current tree. | |
| Short plan + one action | LLM may include internal plan, but execution is one action. | ✓ |
| Full multi-step plan | LLM returns several actions at once. | |
| You decide | Planner chooses as long as one-action execution is enforced. | |

**User's choice:** Short plan + one action.  
**Notes:** More debuggable, but execution remains one action per turn.

| Option | Description | Selected |
|--------|-------------|----------|
| WhatsApp compose fallback | Intent fails or lands incomplete, loop opens/fills compose. | |
| WhatsApp search/contact selection | Handles selecting the contact inside WhatsApp if direct URI does not work. | ✓ |
| Settings-style generic task | Proves broader phone control, but less tied to hero flow. | |
| You decide | Planner picks safest demonstrable WhatsApp scenario. | |
| User freeform | Ask how the grandson is named/saved; after 3 failed attempts, close. | ✓ |

**User's choice:** Freeform refinement.  
**Notes:** If Beto cannot find "mi nieto", ask how he is named or saved. Retry max 3 times; no persistent learning.

| Option | Description | Selected |
|--------|-------------|----------|
| Cerrar y volver a idle | Leave WhatsApp as-is, speak warm failure, bubble returns to idle. | ✓ |
| Volver atras antes de cerrar | Try back until exiting WhatsApp/search, then return idle. | |
| Visual error breve | Leave screen, show error 1-2s, speak warm phrase, return idle. | |
| You decide | Planner picks least risky close path. | |

**User's choice:** Cerrar y volver a idle.  
**Notes:** Do not try to clean up WhatsApp after 3 failed attempts.

---

## Bubble states

| Option | Description | Selected |
|--------|-------------|----------|
| Color ring + simple icon | Stable logo, ring color, small state icon. | ✓ |
| Color ring + animation only | State is color plus pulse/wave/shake. | |
| Full state badges | Logo, ring, icon, and small label/badge. | |
| You decide | Planner picks least cluttered option. | |

**User's choice:** Color ring + simple icon.  
**Notes:** No full labels/badges inside 64dp bubble.

| Option | Description | Selected |
|--------|-------------|----------|
| Calm semantic palette | Idle gray, listening blue, thinking amber, speaking green, error red. | ✓ |
| Beto brand palette | Idle blue, listening cyan, thinking purple, speaking green, error red. | |
| High-contrast palette | Idle dark gray, listening bright blue, thinking yellow, speaking white/green, error red. | |
| You decide | Planner chooses exact accessible colors. | |

**User's choice:** Calm semantic palette.  
**Notes:** Exact hex values can be selected for contrast.

| Option | Description | Selected |
|--------|-------------|----------|
| Subtle per-state animation | Listening pulse, thinking rotate/dots, speaking wave, error shake. | |
| One shared pulse | Same gentle pulse for non-idle states, error shake. | ✓ |
| No animation except error | Fastest, less clear in motion. | |
| You decide | Planner balances polish with time. | |

**User's choice:** One shared pulse.  
**Notes:** Keep implementation simple and consistent.

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse thinking | Fallback is internally thinking/working; no separate state. | ✓ |
| Thinking + small tool icon | Amber thinking plus tool/hand/cursor icon. | |
| Error-to-thinking sequence | Brief error red, then thinking while fallback runs. | |
| You decide | Planner maps clearly without sixth state. | |

**User's choice:** Reuse thinking.  
**Notes:** No sixth state.

| Option | Description | Selected |
|--------|-------------|----------|
| Strict 200ms max | All state transitions complete within 200ms. | ✓ |
| Immediate color, 200ms animation | Color/icon immediate; pulse/shake can run longer. | |
| Planner decides | User sees each real transition within 200ms. | |
| No strict timing | Prioritize polish over timing. | |

**User's choice:** Strict 200ms max.  
**Notes:** Match roadmap success criterion literally.

---

## Senior UX system

| Option | Description | Selected |
|--------|-------------|----------|
| Hard style tokens | Shared styles like `textStyleHero >=28sp`, `textStyleBody >=22sp`; all Beto-owned UI must use them. | ✓ |
| Screen-level guidelines | Each screen can choose sizes but must pass >=22sp body/high contrast. | |
| Only audit existing UI | Patch sizes where too small, no shared system yet. | |
| You decide | Planner picks fastest enforceable approach. | |

**User's choice:** Hard style tokens.  
**Notes:** Apply to Companion and shortcut UI.

| Option | Description | Selected |
|--------|-------------|----------|
| Exact phrase bank | Approved phrases for common failure/status cases. | ✓ |
| Rules + examples | Tone rules and examples; implementers write similar copy. | |
| TTS-only strictness | Visible text can vary; spoken feedback exact. | |
| You decide | Planner chooses enough consistency for demo. | |

**User's choice:** Exact phrase bank.  
**Notes:** Avoid technical error copy.

| Option | Description | Selected |
|--------|-------------|----------|
| Warm and brief | "Dame un segundo..." / "No pude encontrarlo..." | ✓ |
| More reassuring | "Tranqui, estoy intentando ayudarte..." | |
| Very direct | "No lo encontre. Proba de nuevo." | |
| You decide | Planner writes exact phrases from Beto tone rules. | |

**User's choice:** Warm and brief.  
**Notes:** Phrase bank should stay short and natural.

| Option | Description | Selected |
|--------|-------------|----------|
| Explicit token contrast | Define color tokens and require accessible contrast. | |
| Manual visual check | Verify simple high-contrast colors on demo phone. | |
| Both | Define tokens and manually verify on demo phone. | ✓ |
| You decide | Planner picks fastest credible enforcement. | |

**User's choice:** Both.  
**Notes:** Tokenized and manually checked.

| Option | Description | Selected |
|--------|-------------|----------|
| Always one sentence | Every spoken feedback phrase max one sentence. | |
| One sentence except clarification | Fallback/failure one sentence; contact clarification can ask one concise question. | ✓ |
| Planner decides | Preserve UX-03 while allowing natural speech. | |
| Only final feedback | Intermediate prompts may be longer. | |

**User's choice:** One sentence except clarification.  
**Notes:** Contact clarification can be a concise question.

## the agent's Discretion

- Exact fallback trigger, as long as it remains narrow and clear-failure-biased.
- Technical implementation of tree hashing, repeated-action detection, node refs, and prompts.
- Exact color hex values and icons, as long as they preserve the semantic mapping and pass contrast.
- Additional phrase-bank entries, as long as they match the warm/brief style.

## Deferred Ideas

- Persistent alias/contact learning for relationships such as "mi nieto" — future phase, not Phase 4.
