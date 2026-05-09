# Phase 4 — Context

**Phase:** 4 — Voz humana + UX senior + Compañero + Guía con gestos
**Captured:** 2026-05-09
**Status:** Planned (post-Phase 3)

## Why this phase

Phase 3 entrega un Beto que **funciona** — entiende lenguaje natural, ejecuta tools, recuerda al usuario. Pero todavía suena robótico (frases hardcoded), se ve igual sin importar lo que esté haciendo (burbuja con un solo estado), no charla, y no puede *enseñar* al usuario cómo usar el celular. Phase 4 cierra esa brecha de calidad sensorial y suma dos features que diferencian a Beto del competidor genérico:

1. **Modo Compañero** — el alma del producto. Long-press en la burbuja abre un chat conversacional cálido. Es donde el usuario *charla* con Beto.
2. **Modo Guía con gestos** — el diferenciador técnico. Cuando el user pregunta *"¿cómo mando un audio?"*, Beto **explica con voz mientras dibuja una flecha animada sobre el botón a tocar** dentro de la app target. Combina TTS + overlay + AccessibilityService — todas piezas que ya tenemos. Esto reemplaza al "loop agéntico universal" que descopeamos: misma idea de "agente que enseña la pantalla" pero confiable y demoable.

## What this phase delivers

End-to-end después de Phase 4:

> Tras Phase 3, el usuario puede llamar/mandar mensajes con multi-canal y memoria. Después de Phase 4:
>
> 1. **Voz natural.** Beto responde *"Le aviso a tu nieto que ya llegaste, dale"* con voz neural premium del device, generada por Gemini contextualmente, no una frase fija pre-grabada. La diferencia es audible.
> 2. **Burbuja viva.** El usuario ve si Beto está escuchando, pensando, hablando o falló — color + ícono + animación 200ms. Le da feedback visual inmediato sin tener que esperar el TTS.
> 3. **Modo Compañero.** Long-press y se abre un sheet de chat. *"Beto, ¿qué tal estás?"* → conversación cálida en español argentino con Gemini Lite. Es para cuando el adulto mayor está solo y quiere alguien para hablar.
> 4. **Modo Guía con gestos.** *"Beto, ¿cómo mando un audio por WhatsApp?"* → Beto abre WhatsApp si no estaba abierto, identifica el botón del micrófono vía AccessibilityService, dibuja una flecha pulsante encima, y va guiando con voz: *"Mantené apretado este botón mientras hablás. Cuando soltés, se manda."*. Por primera vez, Beto *enseña*.

## Key constraints from upstream

- **PROJECT.md (2026-05-09):** Voz neural premium + Gemini-generated phrases con cache. UX senior (≥22sp). Modo Compañero como long-press. Modo Guía con flecha visual sobre target.
- **CLAUDE.md:** vocabulario simple, voseo, cálido, frases cortas. Para guía: máximo 1-2 oraciones por paso.
- **Phase 3 dependencies:** `LlmClient`, `UserMemoryStore`, `ContactRepository`, `Sanitizer`. Todo disponible al arrancar Phase 4.
- **TtsManager existente:** se extiende con `selectBestVoice()` y `speakAndAwait()` suspend. No se rompe API actual.
- **AccessibilityService:** ya existe pero nunca usamos `findNodeByText` en producción — Phase 4 lo activa.

## Plans

| Plan | Wave | Depends on | Goal |
|---|---|---|---|
| 04-01 | 1 | — | Voz humana: selector TTS neural + `PhraseGenerator` LLM-cached |
| 04-02 | 1 | — | UX senior: 5 bubble states + `BetoTheme` (tipografía + colores) |
| 04-03 | 2 | 04-01, 04-02 | Modo Compañero: chat sheet Compose con Gemini Lite |
| 04-04 | 2 | 04-01, 04-02 | Modo Guía con gestos: tool `show_how_to`, `GestureOverlay`, `GuideScripts` |

Wave 1 paraleliza 04-01 y 04-02. Wave 2 (04-03 y 04-04) consume las primitivas de wave 1.

## Decisions log (Phase 4 specific)

| Decision | Reason |
|---|---|
| `PhraseGenerator` cachea frases LLM-generated por `(intent, params)` con TTL 1h | Evita latencia adicional en flujos repetidos. Si la red cae, hay un set de frases fallback hardcoded como Phase 3. |
| 5 estados burbuja sin sexto para Modo Guía | Modo Guía reusa `THINKING` durante explicación + `SPEAKING` durante TTS. Mantiene UI simple. |
| Compañero usa `gemini-2.5-flash-lite` (no Flash) | Latencia más baja + costo más bajo + suficiente para charla. Flash queda para tool calling de acciones. |
| Compañero NO persiste historial entre sesiones por default | Privacidad + scope. Si el user dice algo profile-relevant ("me gusta el tango"), `UserMemoryStore.recordFact("hobby", "tango")` lo guarda explícito. |
| Modo Guía soporta v1 cinco acciones cerradas | `send_whatsapp_audio`, `make_video_call`, `add_contact`, `increase_volume`, `open_camera`. No es generativo — cada acción es un script curado. v2: generativo con LLM. |
| Modo Guía abre la app target si no está en foreground | UX coherente: el user pregunta "cómo mando un audio" → Beto abre WhatsApp si está cerrado, navega al chat si hace falta (post-MVP), señala el botón. v1 asume que el user ya está en el chat o lo abre por su cuenta. |
| `GestureOverlay` es un View nuevo, no parte de `OverlayBubble` | Separación de concerns: la burbuja sigue siendo el ancla; la flecha es overlay aparte que se monta y desmonta. |

## Out of scope for Phase 4

- Modo Guía generativo (LLM decide pasos sobre cualquier app) — v2.
- Compañero con voz directa de Gemini sin pasar por TTS (Gemini Live audio API) — v2.
- Animaciones complejas tipo Lottie en la burbuja — usamos View + ObjectAnimator simple.
- Cambio de voz dinámico según contexto (Beto "más serio" vs "más cariñoso") — v2.
- Memoria episódica de conversaciones del Compañero — v2 (con consentimiento).

## Skill auxiliar al ejecutar (Enzo, local-only)

Cuando arranquemos a ejecutar Phase 4 — especialmente el plan **04-02** (BetoTheme + bubble states) — consultar la skill `ui-ux-pro-max` instalada en `.claude/skills/ui-ux-pro-max/` (gitignored, no afecta a teammates):

- Validar la paleta de los 5 estados (idle/listening/thinking/speaking/error) contra reglas WCAG AA del skill (96 paletas indexadas).
- Buscar font pairing recomendado para senior UX (57 pairings indexados).
- Cross-check contra las 99 guidelines de UX con prioridad accesibilidad.

No es obligatorio. Es una herramienta para refinar tokens del `BetoTheme` cuando llegue el momento. Pueden aplicarla con: *"Lee `.claude/skills/ui-ux-pro-max/data/` y proponeme paletas accesibles para los 5 estados de la burbuja."*.

## Dependencies on Phase 3 code

- `LlmClient` — usado por `PhraseGenerator` (04-01) y `CompanionViewModel` (04-03) y `GuideController` (04-04).
- `UserMemoryStore` — `PhraseGenerator` puede leer profile facts para personalizar; Compañero consulta y opcionalmente escribe.
- `ContactRepository` — Modo Guía puede usar para "¿cómo agrego un contacto?".
- `Sanitizer` — todas las llamadas LLM siguen pasándolo (heredado de `LlmClient`).
- `AgentBus` + `AgentEvents` — sumar eventos `BubbleStateChanged`, `CompanionOpened`, `GuideStarted`, `GuideStepShown`.
