# Phase 5 — Context

**Phase:** 5 — Anti-fraude reactivo
**Captured:** 2026-05-09
**Status:** Planned (post-Phase 4)

## Why this phase

Los adultos mayores son target principal de las estafas en Argentina: SMS de "AFIP", llamadas de falsa "ANSES", premios falsos por WhatsApp, pedidos de DNI/CBU/clave. La versión proactiva (Beto detecta solo y avisa) requiere monitor continuo + clasificador en tiempo real → alto riesgo, fuera de scope v1. La **versión reactiva** que cubrimos en Phase 5 es mucho más simple y suficientemente potente para el pitch:

> El usuario le pasa un texto sospechoso a Beto y le pregunta *"¿esto es estafa?"*. Beto invoca un tool especializado, Gemini lo analiza con prompt anti-fraude argentino, y devuelve veredicto cálido sin alarmismo: confirma riesgo si lo hay, explica simple por qué, sugiere qué hacer.

Es 1 tool + prompt curado + UX de invocación. Implementable en 2 plans cortos.

## What this phase delivers

End-to-end después de Phase 5:

> Llega un SMS al teléfono del usuario: *"AFIP: Tiene una multa pendiente. Pague antes del viernes para evitar embargo. CBU 0123-4567..."*.
>
> El usuario ve el SMS y le dice a Beto: *"Beto, mirá esto, ¿es real?"*. Beto:
> 1. Captura el contenido del último SMS (o pide screenshot si no puede leerlo).
> 2. Invoca `analyze_for_fraud(text, screenshot?)`.
> 3. Gemini analiza con system prompt curado + ejemplos few-shot de estafas reales en AR.
> 4. Beto responde con voz cálida: *"Mirá, esto se ve sospechoso. AFIP nunca pide CBU por SMS y nunca te apura así. Mejor no hagas nada, llamá a tu banco si querés confirmar."*.

## Key constraints from upstream

- **PROJECT.md:** Anti-fraude **reactivo** (no proactivo). Solo cuando el user pregunta.
- **CLAUDE.md:** vocabulario simple, voseo, sin alarmismo. Beto no asusta.
- **Phase 3 dependencies:** `LlmClient`, `Sanitizer`, `ToolDescriptors`. El sanitizer protege especialmente acá: si el SMS legítimo tiene un DNI real, debe llegar tachado al LLM (irónicamente, ese mismo dato es la razón por la que se detecta como sospechoso).
- **Phase 4 dependencies:** `PhraseGenerator` para frases finales, voz neural masculina argentina, voseo.
- **Multimodal:** Gemini Vision recibe screenshot si la captura de texto no es accesible. Esto requiere `MediaProjection` permission (que el user otorgue al primer uso) o manualmente desde la app de mensajería compartiendo el screenshot.

## Plans

| Plan | Wave | Depends on | Goal |
|---|---|---|---|
| 05-01 | 1 | — (Phase 3+4) | Tool `analyze_for_fraud` + prompt curado anti-fraude argentino |
| 05-02 | 2 | 05-01 | UX de invocación: voz natural + captura de contenido (último SMS / screenshot share-target) |

## Decisions log (Phase 5 specific)

| Decision | Reason |
|---|---|
| Solo reactivo, no proactivo | Proactivo necesita monitor continuo + clasificador 24/7 + overlay. Riesgo alto, no aporta más al pitch. v2. |
| Ejemplos few-shot de estafas argentinas reales | Genericidad mata performance. Casos específicos (AFIP, ANSES, premios, urgencia) calibran mejor. |
| Sin alarmismo en respuestas | "Esto se ve sospechoso" en vez de "¡PELIGRO!". Adultos mayores responden mal al alarmismo. |
| Captura del último SMS como path principal | Más cómodo que screenshot. Requiere `READ_SMS` permission. Si rechazado, fallback a screenshot share-target. |
| Share target: app aparece como destino al compartir screenshot/texto | Estándar Android, familiar. *"Compartir → Beto → ¿Es estafa?"*. |
| No bloquear UX si la red cae durante análisis | Frase warm: *"No puedo analizarlo ahora, mejor no hagas nada y preguntá a alguien de confianza."* |

## Out of scope for Phase 5

- Anti-fraude proactivo (monitor de notificaciones / SMS) — v2.
- Bloqueo automático de mensajes — v2 ético complejo.
- Lista negra de números/dominios — v2.
- Auto-reporte a AFIP/Anti-Estafas — v2.
- Detección de deepfake voice — v2 técnico.

## Dependencies on Phase 3+4 code

- `LlmClient` (Phase 3) — extender con `analyzeForFraud(text: String, screenshot: Image?)`.
- `Sanitizer` — sigue tachando DNI/tel/tarjeta antes del payload (irónico pero correcto: el contenido del SMS de fraude tiene esos datos; el LLM puede analizar sin verlos exactos).
- `PhraseGenerator` (Phase 4) — para la frase final cálida.
- `TtsManager.speakAndAwait` (Phase 4) — para hablar el veredicto.
- `BubbleState` (Phase 4) — `THINKING` durante el análisis, `SPEAKING` durante el veredicto.
