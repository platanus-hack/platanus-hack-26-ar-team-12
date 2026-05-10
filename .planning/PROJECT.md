# Beto

## What This Is

Beto es un agente multimodal de IA en Android — un copiloto cálido y paciente para adultos mayores que **ejecuta acciones por voz, recuerda al usuario, lo guía visualmente cuando hace falta, lo acompaña conversando, y lo protege de estafas cuando se lo pregunta**. Usa AccessibilityService para "ver" la pantalla, `SpeechRecognizer` y `TextToSpeech` nativos para escuchar y hablar en español argentino, y Gemini 2.5 Flash (vía Firebase AI Logic) como cerebro para entender lenguaje natural, llamar tools y mantener una conversación natural. Proyecto de hackathon (Platanus Hack 26 — track Vertical AI) construido por team-12.

## Core Value

**Beto es un asistente conversacional que (1) opera el celular por voz, (2) aprende y recuerda al usuario, (3) guía con gestos en pantalla cómo usar las apps, (4) acompaña conversando, y (5) detecta intentos de estafa cuando se lo consultan — todo en español argentino con tono cálido.**

La tesis del producto: el adulto mayor no debería tener que aprender el celular. Beto es el celular adaptándose a él.

## Capabilities (v1 hackathon scope)

1. **Ejecutor multi-canal con aprendizaje** — *"Mandale a mi nieto que ya llegué"* → Beto interpreta, pregunta el medio si es la primera vez (WhatsApp / SMS / llamada), resuelve el contacto desde la libreta del sistema, ejecuta. Aprende y guarda preferencias.
2. **Memoria del usuario** — Persiste alias de contactos ("nieto" = Juan Pérez), medio preferido por contacto, hobbies, datos familiares. No vuelve a preguntar lo que ya sabe.
3. **Modo Compañero** — Long-press en la burbuja abre un chat conversacional con Gemini en tono cálido. Para hablar, escuchar, hacer compañía.
4. **Modo Guía con gestos en pantalla** — *"Beto, ¿cómo mando un audio?"* → Beto explica con voz **y** dibuja una flecha animada sobre el botón a tocar. Combina TTS + overlay + AccessibilityService para localizar el View.
5. **Anti-fraude reactivo** — *"Beto, ¿esto es estafa?"* sobre un mensaje o screenshot. Gemini Vision analiza y devuelve veredicto cálido.
6. **Voz humana** — Selección de la mejor **voz neural masculina argentina** disponible en el device + respuestas generadas por Gemini (no frases fijas) en tono de amigo, **siempre voseo, nunca "usted"**.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

**Phase 1 — Foundation (completa):**
- [x] App Android instalable (Kotlin 2.1.10, AGP 8.7.3, minSdk 31)
- [x] AgentBus reactivo (SharedFlow) con eventos y comandos del sistema
- [x] Burbuja flotante (drag, magnet a borde) sobre cualquier app
- [x] Foreground Service con notificación persistente
- [x] TTS nativo en es-AR con fallback es-ES → es → en-US
- [x] Pre-flight check de permisos críticos al boot

**Phase 2 — Plan C Offline (completa):**
- [x] STT nativo en es-AR con `VoiceCaptureActivity`
- [x] Matcher determinista para la familia *"mandale / avisale / decile"*
- [x] Apertura de WhatsApp con Intent estricto y mensaje pre-llenado
- [x] Confirmación TTS antes de actuar + reporte de éxito/fallo

### Active

<!-- Current scope being built toward in remaining hackathon time. -->

**Phase 3 — Cerebro IA + Memoria + Multi-canal con aprendizaje**

- [ ] Cliente Gemini 2.5 Flash vía Firebase AI Logic con structured tool calling
- [ ] Sanitizer regex on-device (DNI / teléfono / tarjeta) antes de cualquier payload al LLM
- [ ] STT corrector via Gemini: corrige transcripts dudosos usando contexto (contactos, comandos previos)
- [ ] Acceso a la libreta del sistema (`ContactsContract`) con permiso runtime
- [ ] Memoria persistida (`EncryptedSharedPreferences`) con: aliases de contactos, medio preferido por contacto, datos personales declarados
- [ ] Action router LLM-driven multi-canal: si falta info, pregunta cálidamente; cuando recibe respuesta, guarda y nunca repregunta

**Phase 4 — Voz humana + UX senior + Compañero + Guía con gestos**

- [ ] Selector TTS: usa la voz neural premium del device si existe (Google Neural / Samsung)
- [ ] Todas las respuestas son generadas por Gemini (no PhraseBank fijo) con caché por hash para latencia
- [ ] 5 estados visuales de la burbuja (idle / listening / thinking / speaking / error) con transición ≤200ms
- [ ] Tipografía senior (≥22sp) y colores high-contrast en toda UI propia
- [ ] Modo Compañero: long-press abre sheet de chat conversacional Compose
- [ ] Modo Guía con gestos: Beto explica con voz **y** dibuja flecha/highlight sobre el botón target

**Phase 5 — Anti-fraude reactivo**

- [ ] Tool `analyze_for_fraud(text | screenshot)` con prompt Gemini especializado en estafas a adultos mayores en Argentina
- [ ] Invocación natural: *"Beto, ¿esto es estafa?"* → captura el contenido relevante y devuelve veredicto cálido sin alarmismo

**Phase 6 — Activación rápida (opcional)**

- [ ] Activación por mantener pulsado volumen-down 2s (alternativa al tap en burbuja)
- [ ] Spike de wake word real ("Hola Beto") — implementación si el spike sale viable

**Phase 7 — Demo Readiness**

- [ ] APK frozen ≥4 h antes
- [ ] Hot-spare phone configurado idéntico
- [ ] Hotspot dedicado del dev (no Wi-Fi del venue)
- [ ] Guion ensayado 5x extremo a extremo
- [ ] Video pre-grabado de respaldo (3 min)
- [ ] Submission completa (`platanus-hack-project.json`, README)

### Out of Scope

<!-- Explícitamente fuera del v1 hackathon. Documentado para evitar scope creep. -->

- **Soporte de computadora** — Plataforma entera nueva, no entra ni con 72 h. Roadmap v2+.
- **Anti-fraude proactivo** (Beto detecta solo en tiempo real) — Requiere monitor de notificaciones + clasificador continuo. La versión reactiva (Phase 5) cubre la tesis sin el riesgo. v2.
- **Loop agéntico universal** (LLM operando UI iterativamente) — El roadmap viejo lo planteaba "no se demuestra, pero existe". Con la nueva visión de Modo Guía + Multi-canal, no aporta valor. Permanente fuera. La tesis se prueba con Intents + memoria + guía visual.
- **Memoria con embeddings / RAG** — JSON simple con perfil del usuario alcanza para hackathon. v2.
- **Onboarding visual completo de permisos** — Permisos especiales (overlay, accesibilidad) requieren navegación manual a Settings que no podemos automatizar. Onboarding asistido por voz en v2.
- **Cloud STT (Whisper / Realtime API)** — Suma latencia + dependencia de red. Nativo on-device + corrección por Gemini alcanzan. v2.
- **NER on-device profundo** — Regex simple cuenta la historia para hackathon. v2.
- **Multi-usuario / cuentas / sync** — Single-device, single-user, stateless entre milestones. v2.
- **iOS / Web** — Tesis depende de AccessibilityService Android. Permanente fuera.
- **Apps bancarias / pagos** — Bloquean AccessibilityService por seguridad. Excluir refuerza confianza. Anti-feature permanente.

## Context

**Equipo:** 5 personas Android (Francisco Iturain, Mateo Buela, Nahuel Prado, Matías Sánchez Novelli, Enzo Canelo). Cualquiera puede tomar cualquier tarea — paralelización por superficies disjuntas.

**Hackathon — Platanus Hack 26 (Buenos Aires) — track Vertical AI:** la verticalidad es el dominio "operación del celular para adultos mayores", no un sector industrial.

**Audiencia / problema:** adultos mayores con brecha digital y soledad. El producto debe transmitir empatía y simplicidad. Vocabulario simple, cálido, paciente, corto, voseo argentino.

**Setup técnico de demo:** teléfono Android dedicado (API 31+) con permisos pre-otorgados manualmente. Apps relevantes (WhatsApp, Maps) instaladas y seedeadas.

**Naturaleza de hackathon:** robustez para producción es la decisión incorrecta si quita tiempo a la demo. Si algo es frágil pero funciona en el guion ensayado, alcanza. Caminos confiables (Intents) preferidos sobre caminos impresionantes pero frágiles para todo lo que el guion principal toca.

**Restricciones conocidas que impactan el plan:**
- AccessibilityService requiere activación manual en Settings (no hay dialog estándar)
- `performAction` sobre nodos es confiable; el árbol de vistas es ruidoso entre apps
- WhatsApp y apps populares cumplen razonablemente con accesibilidad; bancos suelen bloquear
- Permiso `READ_CONTACTS` se pide runtime (sí hay dialog estándar) — necesario para memoria
- Voz TTS argentina puede no estar instalada — fallback a es-ES + advertencia al setup

## Constraints

- **Timeline:** 24-36 horas de sprint hasta la demo
- **Tech stack:** Android nativo Kotlin + AccessibilityService + SYSTEM_ALERT_WINDOW
- **STT:** Android `SpeechRecognizer` (cloud-backed default, on-device API 31+ donde aplique)
- **TTS:** Android `TextToSpeech` nativo, mejor voz neural disponible en device
- **LLM:** Gemini 2.5 Flash vía `com.google.firebase:firebase-ai` (Firebase AI Logic). Modelo Lite para Compañero por costo/latencia. Fallback Anthropic comentado si Gemini falla en es-AR.
- **Persistencia:** `EncryptedSharedPreferences` (memoria del usuario). Sin DB.
- **Privacidad:** sanitización mínima on-device (regex DNI / teléfono / tarjeta) antes de cualquier llamada al LLM.
- **Equipo:** 5 personas en paralelo — superficies de archivos disjuntas, evitar bottlenecks.
- **Demo:** un teléfono dedicado + hot-spare. Confiabilidad sobre sofisticación en el guion principal.

## Key Decisions

| Decisión | Rationale | Estado |
|---|---|---|
| Reformular roadmap completo después de Phase 2 | La visión del producto creció: memoria, multi-canal, guía con gestos, anti-fraude reactivo. Phases 3-5 viejas quedaron desalineadas. | ✓ Hecho 2026-05-09 |
| Multi-canal con aprendizaje en lugar de WhatsApp-only | El usuario dijo *"no nos cerremos por simples"*. Pregunta el medio la primera vez y guarda preferencia. | — Active |
| Memoria persistida en `EncryptedSharedPreferences` (no Room/SQLite) | Hackathon scope. JSON serializado con `kotlinx.serialization` alcanza para perfil + aliases + preferencias. | — Active |
| Modo Guía con gestos como feature core | Diferenciador fuerte vs. asistentes existentes. Combina TTS + overlay + AccessibilityService que ya tenemos. | — Active |
| Voz Beto = Gemini-generated, no PhraseBank fijo | Para sonar más humano. Cache por hash mitiga latencia. | — Active |
| Anti-fraude reactivo (no proactivo) | Reactivo es 1 tool + prompt. Proactivo requiere monitor continuo, alto riesgo. | — Active |
| Wake word a Phase 6 (opcional) | Botón físico (volumen-down 2s) cubre el caso sin licencias problemáticas. Wake word real solo si el spike sale viable. | — Active |
| Loop agéntico universal descopeado por completo | El roadmap viejo lo tenía como "no se demuestra". La nueva visión no lo necesita. | ✓ Out of scope |
| Soporte de computadora descopeado | Plataforma entera nueva. Imposible en 24-36 h. | ✓ Out of scope |
| Solo Android Kotlin nativo | La tesis depende de AccessibilityService. iOS / web no aplican. | ✓ Locked |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?

---
*Last updated: 2026-05-09 — full reframe post Phase 2: nueva visión (memoria + multi-canal + guía con gestos + anti-fraude reactivo + voz humana). Roadmap pasa de 5 a 7 phases.*
