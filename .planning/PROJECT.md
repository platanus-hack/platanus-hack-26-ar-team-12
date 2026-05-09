# Beto

## What This Is

Beto es un agente multimodal autónomo de IA en Android — un "copiloto" para adultos mayores que ve la pantalla, escucha comandos por voz y opera el teléfono por ellos. Usa AccessibilityService para entender qué hay en la pantalla en tiempo real, ejecuta acciones a su nombre (mandar mensajes, llamar, navegar apps) y los acompaña con un tono cálido y paciente. Proyecto de hackathon (Platanus Hack 26 — track Vertical AI) construido por team-12 en 24-36 horas.

## Core Value

**Beto entiende un comando de voz complejo en español argentino ("avisale a mi nieto que ya llegué") y ejecuta la acción correcta en el celular sin que el adulto mayor tenga que tocar nada más.**

Si todo lo demás falla, esa demo en vivo tiene que funcionar. Es lo que prueba la tesis: agente que opera el teléfono, no chatbot.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

(None yet — ship to validate)

### Active

<!-- Current scope being built toward in 24-36hs hackathon sprint. -->

**Hero — Motor de Acciones (híbrido)**

- [ ] App Android instalable en el teléfono dedicado de demo, con AccessibilityService y SYSTEM_ALERT_WINDOW pre-activados (asumimos permisos otorgados manualmente, sin onboarding en código)
- [ ] Burbuja flotante persistente (SYSTEM_ALERT_WINDOW) que es el único punto de entrada al agente — sin wake word
- [ ] Captura de voz por tap en burbuja usando STT nativo de Android (RecognizerIntent / SpeechRecognizer) en es-AR
- [ ] Clasificador de intención + extractor de entidades vía LLM (destinatario, mensaje, app objetivo)
- [ ] Top-3-4 comandos guionados vía Android Intents directos (WhatsApp send, llamada, SMS, Maps) — el camino confiable que se demuestra en vivo
- [ ] Loop agéntico de respaldo: AccessibilityNodeInfo tree → LLM con captura/jerarquía → AccessibilityService.performAction() (click/scroll/type) — para acciones fuera del top-N, demuestra la visión universal
- [ ] Feedback por voz al usuario (TTS nativo Android) confirmando qué se va a hacer y reportando éxito/fallo en tono cálido

**Modo Compañero (MVP mínimo)**

- [ ] Hold/long-press en la burbuja flotante abre sheet de chat conversacional con LLM
- [ ] System prompt estricto: tono cálido, paciente, vocabulario simple, respuestas cortas, empatía argentina

**Privacidad — filtrado on-device**

- [ ] Sanitizador local que tacha datos sensibles (DNI, teléfono, número de tarjeta) con regex antes de enviar texto/captura al LLM cloud — la versión más simple que cuente la historia en el pitch

**Demo readiness**

- [ ] Teléfono dedicado seedeado con apps (WhatsApp instalado), contactos demo ("Mi nieto", "Hijo", etc.) y un chat de WhatsApp con mensaje "estafa" mockeado para potencial demo del Escudo
- [ ] Guion de demo de 3-5 min ensayado: comando voz hero + 1-2 comandos extra + breve muestra del Compañero
- [ ] `platanus-hack-project.json` completado (project-name, oneliner, descripción)

### Out of Scope

<!-- Explícitamente no construimos esto en el MVP. -->

- **Wake word "Beto"** — Porcupine es non-commercial en free tier y agrega trabajo on-device. Botón flotante cubre el rol de activación con cero riesgo de licencia. Roadmap post-hackathon.
- **Escudo Antiestafas** — Flujo emocional fuerte pero requiere detección visual robusta + overlay rojo + lógica de heurísticas que arriesga el sprint. Solo si sobra tiempo después del MVP. Mencionar en pitch como roadmap.
- **Onboarding visual de permisos** — AccessibilityService y SYSTEM_ALERT_WINDOW requieren navegación manual a Settings que no podemos automatizar. En la demo el teléfono ya está configurado; el código asume permisos otorgados.
- **Cloud STT (Whisper / Realtime API)** — Mejor calidad para acentos pero suma latencia + dependencia de red en el peor momento (en vivo). Roadmap: cloud por default + nativo como fallback offline.
- **NER on-device / pipeline de privacidad completo** — ML Kit / TF Lite NER agregaría 2-3 días. Regex simple alcanza para la narrativa.
- **Multi-usuario / cuentas / persistencia de historial** — Beto en demo es single-device, single-user, stateless entre sesiones.
- **iOS / Web** — Solo Android. La tesis depende de AccessibilityService.

## Context

**Equipo:** 5 personas full-stack Android (Francisco Iturain, Mateo Buela, Nahuel Prado, Matías Sánchez Novelli, Enzo Canelo). Cualquiera puede tomar cualquier tarea — plan flexible, paralelización libre.

**Hackathon — Platanus Hack 26 (Buenos Aires) — track Vertical AI:** la propuesta debe ser un agente vertical, no un chatbot genérico. La verticalidad de Beto es el dominio "operación del celular para adultos mayores", no un sector industrial.

**Audiencia / problema:** adultos mayores que enfrentan brecha digital y soledad. El producto debe transmitir empatía y simplicidad, no sofisticación técnica. CLAUDE.md ya define el tono que el agente usa con el usuario final: vocabulario extremadamente simple, cálido, paciente, respuestas muy cortas y directas, transmitir seguridad y empatía.

**Setup técnico de demo:** teléfono Android dedicado (versión moderna, asumimos 13+) con todos los permisos pre-otorgados manualmente. Apps relevantes instaladas y seedeadas con datos demo realistas antes del pitch.

**Naturaleza de hackathon:** no buscamos producción ni escala. Cualquier decisión que sume robustez "para producción" pero quite tiempo a la demo es la decisión incorrecta. Si algo es frágil pero funciona en el guion ensayado, alcanza.

**Restricciones conocidas de AccessibilityService que impactan el plan:**
- Requiere activación manual en Settings → Accesibilidad (no hay dialog estándar)
- `performGlobalAction` y `performAction` sobre nodos son confiables, pero el árbol de vistas es ruidoso e inconsistente entre apps — el loop agéntico es frágil por naturaleza
- WhatsApp y muchas apps populares cumplen razonablemente con accesibilidad, pero apps bancarias frecuentemente bloquean lectura por seguridad
- Capturas de pantalla vía MediaProjection es un permiso aparte (dialog estándar) — preferimos texto del árbol de vistas cuando alcanza, captura solo si el LLM la necesita

## Constraints

- **Timeline:** 24-36 horas de sprint hasta la demo en vivo — no hay margen para iteraciones largas, refactors o validación con usuarios reales
- **Tech stack:** Android nativo (Kotlin) + AccessibilityService + SYSTEM_ALERT_WINDOW. No React Native, no Flutter — la verticalidad sobre Accessibility lo hace inviable
- **STT:** Android nativo (RecognizerIntent / SpeechRecognizer) en es-AR. Cloud STT es post-MVP
- **TTS:** Android `TextToSpeech` nativo con voz en es-AR
- **LLM:** sin decidir aún — la fase de research evalúa Claude vs GPT vs Gemini con criterios: latencia para voz, calidad multimodal (visión sobre capturas), capacidad de tool/function calling robusto para el motor de acciones, cuota gratis o créditos para hackathon, calidad en es-AR
- **Privacidad:** sanitización mínima on-device antes de enviar a LLM cloud — regex de DNI/teléfono/tarjeta. No filtrado profundo
- **Equipo:** 5 personas trabajando en paralelo — preferir tareas con poca interdependencia, agradar merges seguidos, evitar bottlenecks de un solo archivo
- **Demo:** un teléfono dedicado, sin internet inestable como excusa — si algo crashea en vivo, perdimos. Preferir caminos confiables (Intents) sobre caminos impresionantes pero frágiles (loop agéntico) para los comandos del guion principal

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Motor de Acciones híbrido (Intents fijos + loop agéntico de respaldo) | Visión "agente universal" en 24-36hs es inviable como demo confiable. Híbrido garantiza que el guion principal funcione (Intents) y muestra ambición (loop) como bonus | — Pending |
| Sin wake word en MVP — solo botón flotante | Porcupine es non-commercial en free tier. Botón flotante cubre activación sin riesgo legal ni técnico. Wake word queda en roadmap | — Pending |
| Modo Compañero adentro del MVP | Es "casi gratis" una vez que tenemos LLM + UI básica. Es el alma del producto y suma narrativa al pitch | — Pending |
| Escudo Antiestafas afuera del MVP | Detección visual robusta + overlay + heurísticas es scope grande para 24-36hs. Mejor mencionarlo en pitch que mostrarlo frágil | — Pending |
| STT nativo Android (RecognizerIntent) | Funciona en es-AR, gratis, on-device, suficiente para demo con guion ensayado. Cloud STT = post-MVP | — Pending |
| Filtrado de privacidad: regex simple on-device | "Lo más simple que cuente la historia". NER on-device sumaría días | — Pending |
| Asumir permisos pre-otorgados (sin onboarding) | Demo es controlada — pre-configuramos el teléfono. Onboarding no aporta a la demo y consume horas | — Pending |
| Solo Android (Kotlin nativo) | La tesis del producto depende de AccessibilityService. iOS/Web no aplican | ✓ Good |
| LLM provider: a decidir en research | Decisión crítica que afecta arquitectura, latencia y costo. Merece evaluación basada en criterios concretos antes de comprometernos | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-09 after initialization*
