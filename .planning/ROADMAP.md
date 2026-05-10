# Roadmap: Beto

## Overview

Beto se construye en 7 fases que entregan capacidades end-to-end de usuario, no capas técnicas. **Phases 1 y 2 ya están completas** (Foundation + Plan C offline). El roadmap fue reformulado el 2026-05-09 después de revisar Phase 2 y expandir la visión del producto: memoria del usuario, multi-canal con aprendizaje, voz humana, guía con gestos en pantalla y anti-fraude reactivo. La Phase 3 conecta Gemini al flujo y suma memoria + multi-canal — es el salto de "script duro" a "agente IA real". La Phase 4 hace que Beto se vea y suene cálido, suma Modo Compañero y la feature diferencial: guía con gestos visuales sobre la pantalla. La Phase 5 cubre el ángulo emocional fuerte (anti-fraude) en su forma reactiva. La Phase 6 es opcional — activación rápida sin tap (botón de volumen como mínimo viable, wake word como spike). La Phase 7 — Demo Readiness — es donde la mayoría de hackathons fallan: APK frozen, hot-spare, ensayos, video respaldo, submission.

**Granularity:** coarse — 7 fases.
**Mode:** mvp (vertical slicing, cada fase entrega capacidad observable de usuario).
**Parallelization:** habilitada — superficies de archivos disjuntas dentro de cada fase para 5 devs.

## Phases

**Phase Numbering:**
- Integer phases (1–7): Planned milestone work
- Decimal phases (3.1, 3.2): Urgent insertions (marked with INSERTED)

- [x] **Phase 1: Foundation & Sync de Hora 0** — Contrato compartido, manifest+permisos, esqueleto de burbuja, TTS pre-warmed, pre-flight check
- [x] **Phase 2: Vertical Slice Mínimo (Plan C Offline)** — Tap → STT → matcher determinista → Intent WhatsApp → TTS, sin LLM
- [x] **Phase 3: Cerebro IA + Memoria + Multi-canal con aprendizaje** — Gemini client + sanitizer + STT corrector + acceso a contactos del sistema + memoria persistida + router multi-canal que aprende
- [x] **Phase 4: Voz humana + UX senior + Compañero + Guía con gestos** — TTS neural + respuestas generadas por LLM + 5 estados burbuja + tipografía senior + chat Compañero + flecha visual sobre target
- [ ] **Phase 5: Escudo Antiestafas (proactivo + reactivo)** — AccessibilityService → ScamRiskEngine local → Overlay → Acción. LLM solo explica.
- [ ] **Phase 6: Activación rápida (opcional)** — Botón físico (vol-down 2s) y/o wake word
- [ ] **Phase 7: Demo Readiness** — Freeze APK, hot-spare, hotspot, checklist físico, ensayos, video respaldo, submission

## Phase Details

### Phase 1: Foundation & Sync de Hora 0
**Status:** ✓ Completa
**Goal**: El proyecto compila e instala en el teléfono de demo, la burbuja flotante aparece sobre cualquier app, el sistema valida al boot que tiene los permisos necesarios y la voz de Beto saluda al usuario al iniciar.
**Requirements**: SETUP-01..05, BUS-01..03, OVERLAY-01, OVERLAY-02, VOICE-01, VOICE-02, DEMO-04
**Plans**: 3/3 ejecutados

### Phase 2: Vertical Slice Mínimo (Plan C Offline)
**Status:** ✓ Completa
**Goal**: El usuario toca la burbuja, dice "mandale a mi nieto que ya llegué", y WhatsApp se abre con el mensaje pre-llenado al contacto correcto, todo sin internet y sin LLM, con confirmación por voz.
**Depends on**: Phase 1
**Requirements**: OVERLAY-03, VOICE-03..05, LLM-04, ACT-01, ACT-02, ACT-06
**Plans**: 3/3 ejecutados

### Phase 3: Cerebro IA + Memoria + Multi-canal con aprendizaje
**Status:** Planning → Execute
**Goal**: Beto entiende lenguaje natural en es-AR vía Gemini con tool calling, lee la libreta del sistema, persiste un perfil del usuario (aliases, preferencias, datos), y cuando le falta info pregunta amablemente y guarda la respuesta para no repreguntar. La primera vez que el usuario dice "mandale a Juan", Beto pregunta "¿por WhatsApp, SMS o llamada?" y lo guarda. La primera vez que dice "llamá a mi nieto", Beto pregunta "¿quién es tu nieto?" y lo guarda.
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: LLM-01..03, LLM-05, LLM-06, PRIV-01, PRIV-02, ACT-03, ACT-04, ACT-05, STT-FIX-01, STT-FIX-02, CONTACTS-01..03, MEM-01..04, LEARN-01, LEARN-02
**Success Criteria** (what must be TRUE):
  1. Diciendo "llamá a Pedro", Beto resuelve a un contacto real de la libreta y comienza la llamada en menos de 5 segundos.
  2. Diciendo un comando con DNI/teléfono/tarjeta embebido, ese dato sale tachado en el payload que va al LLM (verificable en logs `Beto-LLM`).
  3. La primera vez que se invoca un contacto sin alias en memoria, Beto pregunta "¿quién es tu nieto?" y al recibir la respuesta guarda el alias para usos futuros.
  4. La primera vez que se manda mensaje a un contacto sin canal preferido, Beto pregunta "¿por WhatsApp, SMS o llamada?" y guarda la preferencia. La segunda vez no pregunta.
  5. Si la red cae durante un comando del guion principal, el matcher determinista de Phase 2 toma la posta sin que el usuario lo note.
**Plans**: 5 plans
  - [ ] 03-01-PLAN.md — Gemini client + structured tool calling + sanitizer (LLM-01..03, PRIV-01, PRIV-02)
  - [ ] 03-02-PLAN.md — STT corrector vía Gemini + on-device recognizer API 31+ (STT-FIX-01, STT-FIX-02)
  - [ ] 03-03-PLAN.md — Acceso a contactos del sistema + permiso runtime (CONTACTS-01..03)
  - [ ] 03-04-PLAN.md — Memoria del usuario en EncryptedSharedPreferences (MEM-01..04)
  - [ ] 03-05-PLAN.md — Action router multi-canal con aprendizaje (ACT-03..05, LEARN-01, LEARN-02, LLM-05, LLM-06)

### Phase 4: Voz humana + UX senior + Compañero + Guía con gestos
**Status:** Planned (4 plans escritos, listos para ejecutar tras Phase 3)
**Goal**: La voz de Beto suena natural (mejor TTS neural disponible + frases generadas por LLM contextualmente), la UI propia respeta tipografía senior con alto contraste, la burbuja muestra 5 estados visuales claros, el usuario puede chatear conversacionalmente con Beto, y el Modo Guía explica con voz **mientras** dibuja una flecha animada sobre el botón a tocar en la app target.
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: VOICE-HUM-01, VOICE-HUM-02, OVERLAY-05, UX-01..03, COMP-01..04, OVERLAY-04, GUIDE-01..03
**Success Criteria** (what must be TRUE):
  1. Las respuestas de Beto en el flujo de acción son generadas por Gemini (no PhraseBank fijo) y suenan más naturales que en Phase 2 — verificable comparando audios A/B.
  2. La burbuja cambia de color/ícono entre 5 estados (idle, listening, thinking, speaking, error) en menos de 200ms ante cada transición real.
  3. Long-press de la burbuja abre un sheet de chat conversacional donde el usuario charla con Beto en tono cálido y argentino.
  4. Diciendo "Beto, ¿cómo mando un audio por WhatsApp?", Beto explica con voz **y** dibuja una flecha animada apuntando al botón del micrófono dentro de WhatsApp, en menos de 5 segundos.
  5. Toda la UI propia usa tipografía ≥22sp con alto contraste; los mensajes de error son siempre cálidos sin códigos técnicos.
**Plans**: 4 plans escritos
  - [ ] 04-01-PLAN.md — Voz humana: selector TTS neural + PhraseGenerator LLM-cached (VOICE-HUM-01..02)
  - [ ] 04-02-PLAN.md — UX senior: 5 bubble states + BetoTheme (OVERLAY-05, UX-01..03)
  - [ ] 04-03-PLAN.md — Modo Compañero: chat sheet Compose con Gemini Lite (OVERLAY-04, COMP-01..04)
  - [ ] 04-04-PLAN.md — Modo Guía con gestos en pantalla, capstone (GUIDE-01..03)
**UI hint**: yes
**Wave plan**: Wave 1 paraleliza 04-01 + 04-02 (independientes). Wave 2: 04-03 + 04-04 consumen primitivas de wave 1 (también paralelizables entre sí).

### Phase 5: Escudo Antiestafas (proactivo + reactivo)
**Status:** In progress (re-planificada 2026-05-10 para alinear al pitch final)
**Goal**: Beto detecta estafas **antes del clic**. AccessibilityService lee pasivamente el chat, ScamRiskEngine local cruza ≥3 señales offline y decide riesgo, overlay frena al usuario con 3 botones grandes (llamar a confianza · cancelar · entendido), TTS habla cálido. El LLM solo enriquece la frase, nunca decide. Path reactivo (`"¿esto es estafa?"`) reusa el mismo engine.
**Mode:** mvp
**Depends on**: Phase 3, Phase 4
**Requirements**: FRAUD-01, FRAUD-02 (extendidos por la nueva arquitectura)
**Success Criteria** (what must be TRUE):
  1. Sobre el caso del pitch (chat WhatsApp con *"cambié de número + 80 mil + urgente + no le digas"*), `ScamRiskEngine` retorna `HIGH` con ≥3 signals — verificable por unit test sin Android.
  2. Cuando llega ese mensaje a WhatsApp en el teléfono de demo, el overlay aparece encima del chat en menos de 2s con badge rojo, chips de signals y 3 botones grandes.
  3. Tocar "Llamar a mi nieto" lanza un intent `ACTION_CALL/DIAL` al contacto de confianza configurado.
  4. El motor decide **sin red**: con avión activo, el overlay y los botones siguen funcionando; solo la frase warm cae al canned por signal-combo.
  5. Sobre un mensaje legítimo (ej. confirmación de turno médico, mensaje real de un contacto guardado), no aparece overlay (cero falsos positivos en suite de control).
  6. Path reactivo (`"Beto, ¿esto es estafa?"`) sigue funcionando: pasa por el mismo engine y, si HIGH/MEDIUM, el LLM Explainer redacta la respuesta hablada.
**Plans**: 6 blocks iterativos (re-numerados)
  - [ ] 05-01-PLAN.md — **ScamRiskEngine core** (pure Kotlin): Signal enum, SignalDetector, engine, tests JUnit con demo case del pitch
  - [ ] 05-02-PLAN.md — Trusted contact (paralelo a 05-01): setting + persistencia + repo
  - [ ] 05-03-PLAN.md — Accessibility pipeline: filter packages, BFS extract, throttle, dedupe hash → engine
  - [ ] 05-04-PLAN.md — Scam Alert Overlay: bottom-sheet sobre chat (badge + chips + 3 botones)
  - [ ] 05-05-PLAN.md — LLM Explainer (Haiku) con timeout 1.5s + canned phrases por combo de signals
  - [ ] 05-06-PLAN.md — AlertOrchestrator: cooldown, dedupe, BubbleState.WARNING, TTS-overlay sync, fix mic-during-TTS, path reactivo

### Phase 6: Activación rápida (opcional)
**Status:** Planned (2 plans escritos), opcional según tiempo
**Goal**: El usuario puede activar Beto sin tapear la burbuja: mantener pulsado volumen-down por 2 segundos (mínimo viable) o decir "Hola Beto" (wake word, si el spike de 30 min sale viable).
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: ACTIV-01, ACTIV-02
**Success Criteria** (what must be TRUE):
  1. Mantener pulsado volumen-down 2s (con la app en foreground o background con overlay activo) lanza la captura de voz como si se tocara la burbuja.
  2. (Stretch) Decir "Hola Beto" con la pantalla activa lanza la captura de voz dentro de 1 segundo.
**Plans**: 2 plans escritos
  - [ ] 06-01-PLAN.md — Activación por volumen-down mantenido 2s (ACTIV-01)
  - [ ] 06-02-PLAN.md — Wake word "Hola Beto" — spike 30 min + implementación condicional (ACTIV-02)

### Phase 7: Demo Readiness
**Status:** Planned (1 plan escrito)
**Goal**: El día del pitch el equipo entra con dos teléfonos idénticamente configurados, un APK congelado hace 4+ horas, una red dedicada propia, un guion ensayado 5 veces y un video pre-grabado de respaldo, de modo que la demo en vivo sea robusta incluso si algo falla.
**Mode:** mvp
**Depends on**: Phase 4 (mínimo) — Phase 5 y 6 deseables pero no bloqueantes
**Requirements**: DEMO-01..03, DEMO-05..10, SUB-01, SUB-02
**Success Criteria** (what must be TRUE):
  1. El guion completo de 3-5 min corre extremo a extremo en el teléfono principal sin errores visibles.
  2. El hot-spare ejecuta el mismo guion sin diferencias notorias.
  3. El equipo completa el checklist físico de 16 items con todos los toggles verdes antes de subir al escenario.
  4. Si la demo en vivo falla, el equipo pasa al video pre-grabado en menos de 5 segundos de transición.
  5. La submission a Platanus Hack 26 está completa: `platanus-hack-project.json` con name + oneliner + descripción en español, README actualizado.
**Plans**: 1 plan escrito
  - [ ] 07-01-PLAN.md — Demo readiness completo (operativo: freeze APK, hot-spare, hotspot, ensayos 5x, video respaldo, checklist físico de 16 ítems, submission) — DEMO-01..03, DEMO-05..10, SUB-01

## Progress

**Execution Order:**
Phases ejecutan en orden numérico: 1 → 2 → 3 → 4 → 5 → 6 → 7. Phase 6 es opcional según tiempo.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Sync de Hora 0 | 3/3 | ✓ Done | 2026-05-09 |
| 2. Vertical Slice Mínimo (Plan C Offline) | 3/3 | ✓ Done | 2026-05-09 |
| 3. Cerebro IA + Memoria + Multi-canal | 5/5 | ✓ Done | 2026-05-09 |
| 4. Voz humana + UX + Compañero + Guía con gestos | 4/4 | ✓ Done | 2026-05-10 |
| 5. Escudo Antiestafas (proactivo + reactivo) | 0/6 | In progress (re-planificada 2026-05-10) | — |
| 6. Activación rápida (opcional) | 0/2 | Plans escritos | — |
| 7. Demo Readiness | 0/1 | Plans escritos | — |

---
*Last updated: 2026-05-09 — full reframe roadmap (5 → 7 phases). Phase 1 y 2 marcadas completas según código + STATUS.md. Phases 3-7 redefinidas según nueva visión.*
