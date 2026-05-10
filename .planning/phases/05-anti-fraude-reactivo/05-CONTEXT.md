# Phase 5 — Context (REVISED 2026-05-10)

**Phase:** 5 — Escudo Antiestafas (proactivo + reactivo)
**Captured:** 2026-05-09 (reactive-only)
**Revised:** 2026-05-10 — alineado al Pitch final
**Status:** In progress

## Why this phase changed

La versión original (reactiva: *"Beto, ¿esto es estafa?"*) quedó corta vs. lo que **el pitch promete**:

> *"Beto está leyendo el contexto en pantalla y detecta tres señales al mismo tiempo: urgencia, dinero, y un familiar que aparece de la nada. Antes de que la abuela responda, aparece la alerta de Beto encima del chat."*

> *"AccessibilityService → ScamRiskEngine (motor local) → Overlay → Acción. El motor local decide. El LLM solo explica mejor, nunca decide solo."*

El pitch elevó la apuesta a **anti-fraude proactivo** con un motor on-device que **decide sin LLM** — el LLM solo enriquece la frase warm.

## Architecture (per pitch slide 6)

```
┌──────────────────────┐    ┌──────────────────┐    ┌────────────────┐    ┌────────────────────┐
│ AccessibilityService │ →  │ ScamRiskEngine   │ →  │ Overlay + Voz  │ →  │ Contacto confianza │
│ (entrada pasiva)     │    │ (cerebro local)  │    │ (interfaz)     │    │ (acción intent)    │
└──────────────────────┘    └──────────────────┘    └────────────────┘    └────────────────────┘
        ↓ texto visible            ↓ HIGH/MEDIUM           ↓ user tap
        - WhatsApp                 - 3+ signals →          - Llamar nieto real
        - SMS / Messages             HIGH                  - Cancelar
        - throttle + dedupe        - 2 signals →           - Entendido
                                     MEDIUM
                                                   ┌──────────────────────────────┐
                                                   │ LLM Explainer (opcional)    │
                                                   │ Haiku 4.5 — frase warm      │
                                                   │ contextual. NUNCA decide.   │
                                                   └──────────────────────────────┘
```

**Regla de oro (inviolable):** el motor local es la única fuente de verdad sobre *si hay riesgo*. El LLM se invoca solo después de que el engine decidió, y solo para mejorar la redacción de la explicación. Si el LLM falla / timeout / sin red, hay frases canned por combinación de signals — **el escudo funciona offline**.

## What this phase delivers

End-to-end después de Phase 5:

> Llega a WhatsApp un mensaje desde un número desconocido: *"Hola abu, soy yo. Cambié de número, guardalo. Estoy en un quilombo, ¿me podés transferir 80 mil ahora? Es urgente. No le digas a papá todavía 🙏"*.
>
> Beto, sin que el user toque nada:
> 1. **Accessibility** captura el texto visible (≤500 chars del último burbuja).
> 2. **ScamRiskEngine** detecta 4 signals: `URGENCY` + `MONEY_REQUEST` + `NEW_NUMBER` + `SECRECY` → **HIGH**.
> 3. **AlertOrchestrator** chequea cooldown (no alertó hace <60s sobre el mismo hash) → ok.
> 4. **Overlay** aparece sobre el chat: badge "Beto · Escudo Antiestafas" + título "Ojo, esto podría ser una estafa." + chips de signals + 3 botones grandes.
> 5. **TTS** habla en es-AR cálido: *"Mejor frenemos un segundo. Te están pidiendo plata con urgencia desde un número nuevo. Antes de seguir, llamemos a alguien de confianza."* (frase generada por LLM en background; si no llega en 1.5s → canned).
> 6. User toca "Llamar a mi nieto" → intent `ACTION_CALL` al contacto de confianza guardado.

## Key constraints

- **PROJECT.md:** anti-fraude. Pivot proactivo se justifica porque el pitch lo promete explícitamente.
- **CLAUDE.md:** vocabulario simple, voseo, sin alarmismo. Beto **no asusta**.
- **Confiable primero:** el motor local debe funcionar offline al 100% sobre el caso del pitch. Si la red cae, la demo igual funciona.
- **Phase 3 dependencies:** `LlmClient`, `Sanitizer`, `ToolDescriptors`. El sanitizer protege antes de mandar al LLM Explainer.
- **Phase 4 dependencies:** `PhraseGenerator`, `TtsManager.speakAndAwait`, `BubbleState` (agregar `WARNING`).
- **Privacy:** texto capturado por Accessibility nunca sale del device sin pasar por `Sanitizer`. Logs nunca contienen el texto raw — solo `len=N pkg=X signals=[...] hash=abcd1234`.

## Block plan (6 blocks, iterative)

| Block | Wave | Depends on | Goal |
|---|---|---|---|
| **05-01** | 1 | — | **ScamRiskEngine core** (pure Kotlin): `Signal` enum, `SignalDetector`, `ScamRiskEngine`, `RiskAssessment`. Tests JUnit con caso del pitch. |
| 05-02 | 1 | — (paralelo a 05-01) | Trusted contact: setting + persistencia + `TrustedContactsRepository` |
| 05-03 | 2 | 05-01 | Accessibility pipeline: filter por package whitelist, BFS extract, throttle, dedupe por hash, alimentar engine |
| 05-04 | 2 | 05-01 | Scam Alert Overlay: nuevo overlay variant tipo bottom-sheet sobre chat |
| 05-05 | 3 | 05-01, 05-04 | LLM Explainer + canned phrases por signal combination; nunca decide |
| 05-06 | 3 | 05-01..05-05 | AlertOrchestrator: cooldown por package, dedupe, BubbleState.WARNING, TTS-overlay sync, fix mic-during-TTS, reactivo (`"¿esto es estafa?"`) reusando engine |

## Decisions log

| Decision | Reason |
|---|---|
| Pivot a proactivo | El pitch lo promete textualmente. Reactivo solo no cierra la promesa. |
| Motor local decide, LLM solo explica | Pitch slide 6: *"El LLM solo explica mejor, nunca decide solo."* También garantiza demo offline. |
| Threshold: 1=LOW, 2=MEDIUM, 3+=HIGH | Slide 7: *"una señal sola es ruido · tres señales son patrón"*. |
| Solo alertamos en `HIGH` (v1) | Evitar falsos positivos en demo. `MEDIUM` se reserva para el flujo reactivo invocado por user. |
| Whitelist de packages para Accessibility | Evita ruido + reduce CPU. Lista: WhatsApp, SMS Android, Google Messages. Configurable. |
| Cooldown 60s por package + dedupe por hash | Evita spam si el user scrollea o el chat se actualiza. |
| Frases canned por combo de signals como fallback | Pitch promete que funciona offline. LLM Explainer es enrichment, no requirement. |
| Sanitizer aplicado antes del LLM Explainer | Aún en este flujo, no mandamos DNI/CBU/teléfono raw a la nube. |
| Mantener path reactivo `"¿esto es estafa?"` | Cubre casos fuera de la whitelist (ej. SMS reenviado, screenshot share). Reusa el mismo engine. |

## Out of scope for Phase 5 (v2)

- Bloqueo automático de mensajes (ético complejo).
- Lista negra de números/dominios.
- Auto-reporte a AFIP/Anti-Estafas.
- Detección de deepfake voice / llamada en vivo.
- Alertas por notificación cuando la app no está en foreground (requiere `NotificationListenerService` separado).

## Demo guarantee (caso del pitch — slide 4)

**Input texto** (concatenado, lo que ve Accessibility en el chat):

> Hola abu, soy yo. Cambié de número, guardalo. Estoy en un quilombo, ¿me podés transferir 80 mil ahora? Después te explico. Es urgente. No le digas a papá todavía

**Engine MUST detect:**
- `NEW_NUMBER` ("cambié de número")
- `MONEY_REQUEST` ("transferir 80 mil")
- `URGENCY` ("ahora", "urgente")
- `SECRECY` ("no le digas")
- `IMPERSONATION_FAMILY` ("soy yo", "abu")

→ 5 signals → **HIGH** → overlay + voz + botón llamar.

Esta tupla es **non-negotiable** para Phase 5 done. Está cubierta en `ScamRiskEngineTest.demoCaseFromPitchReturnsHigh()`.
