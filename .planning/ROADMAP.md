# Roadmap: Beto

## Overview

Beto se construye en 24-36 horas mediante 5 fases que entregan capacidades end-to-end de usuario, no capas técnicas. La Fase 1 sincroniza el contrato compartido (`AgentBus`, manifest, permisos, TTS init temprano, esqueleto de burbuja) que desbloquea paralelización de 5 devs sin colisiones. La Fase 2 entrega el Plan C offline-first funcionando: tap en burbuja → STT → matcher determinista → un Intent (WhatsApp) → TTS, sin LLM ni red. Esa fase ya prueba la tesis del producto en su forma mínima. La Fase 3 expande el Motor de Acciones a los 4 Intents completos, conecta el LLM real (Gemini Flash con sanitizer + tool calling), y suma el Modo Compañero. La Fase 4 agrega el loop agéntico de respaldo silencioso (no demostrado, pero existe) y la UX Senior que da credibilidad ante el jurado. La Fase 5 — Demo Readiness — es explícitamente la fase donde la mayoría de hackathons fallan: APK frozen, hot-spare phone, hotspot dedicado, checklist físico, ensayos 5x, video respaldo, submission completa.

**Granularity:** coarse — 5 fases.
**Mode:** mvp (vertical slicing, cada fase entrega capacidad observable de usuario).
**Parallelization:** habilitada — superficies de archivos disjuntas dentro de cada fase para 5 devs.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [ ] **Phase 1: Foundation & Sync de Hora 0** - Contrato compartido, manifest+permisos, esqueleto de burbuja, TTS pre-warmed, pre-flight check
- [ ] **Phase 2: Vertical Slice Mínimo (Plan C Offline)** - Tap → STT → matcher determinista → Intent WhatsApp → TTS, sin LLM
- [ ] **Phase 3: Motor de Acciones Completo + Compañero** - 4 Intents, LLM real con sanitizer + tool calling, Modo Compañero
- [ ] **Phase 4: Loop Agéntico de Respaldo + UX Senior** - Fallback agéntico silencioso, estados de burbuja, tipografía senior
- [ ] **Phase 5: Demo Readiness** - Freeze APK, hot-spare, hotspot, checklist físico, ensayos, video respaldo, submission

## Phase Details

### Phase 1: Foundation & Sync de Hora 0
**Goal**: El proyecto compila e instala en el teléfono de demo, la burbuja flotante aparece sobre cualquier app, el sistema valida al boot que tiene los permisos necesarios y la voz de Beto saluda al usuario al iniciar.
**Mode:** mvp
**Depends on**: Nothing (first phase)
**Requirements**: SETUP-01, SETUP-02, SETUP-03, SETUP-04, SETUP-05, BUS-01, BUS-02, BUS-03, OVERLAY-01, OVERLAY-02, VOICE-01, VOICE-02, DEMO-04
**Success Criteria** (what must be TRUE):
  1. Tras instalar el APK, la burbuja flotante aparece sobre WhatsApp, Maps y la home, y se puede arrastrar con magnet a borde.
  2. Al primer boot del servicio, Beto dice por voz "Hola, soy Beto. Estoy acá para ayudarte." en español argentino sin race condition.
  3. Si falta un permiso crítico (overlay, accessibility o TTS), Beto avisa por voz qué falta en lugar de crashear silenciosamente.
  4. Cualquier dev puede emitir un `AgentEvent` desde un componente y otro componente puede recibirlo en `SharedFlow` sin colisiones de contrato.
**Plans**: TBD

### Phase 2: Vertical Slice Mínimo (Plan C Offline)
**Goal**: El usuario toca la burbuja, dice "mandale a mi nieto que ya llegué", y WhatsApp se abre con el mensaje pre-llenado al contacto correcto, todo sin internet y sin LLM, con confirmación por voz.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: OVERLAY-03, VOICE-03, VOICE-04, VOICE-05, LLM-04, ACT-01, ACT-02, ACT-06
**Success Criteria** (what must be TRUE):
  1. Tap corto en la burbuja levanta la captura de voz en es-AR y devuelve el texto reconocido al sistema en menos de 3 segundos.
  2. Diciendo "mandale a mi nieto que ya llegué" (modo avión activado), Beto abre WhatsApp con el mensaje pre-llenado al número correcto del contacto "Mi nieto".
  3. Antes de ejecutar la acción, Beto confirma por voz qué va a hacer ("Le aviso a tu nieto que ya llegaste, dale") y al volver reporta éxito en una sola frase cálida.
  4. Si el Intent falla (app no instalada, número malformado), Beto lo dice por voz en tono cálido en lugar de fallar en silencio.
**Plans**: TBD

### Phase 3: Motor de Acciones Completo + Compañero
**Goal**: Beto entiende comandos arbitrarios en es-AR vía LLM, ejecuta llamadas / SMS / Maps además de WhatsApp, sanitiza datos sensibles antes de cualquier llamada cloud, y al hacer long-press abre un chat conversacional cálido.
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: ACT-03, ACT-04, ACT-05, LLM-01, LLM-02, LLM-03, LLM-05, LLM-06, PRIV-01, PRIV-02, OVERLAY-04, COMP-01, COMP-02, COMP-03, COMP-04
**Success Criteria** (what must be TRUE):
  1. Diciendo "llamá a mi hijo", el teléfono empieza a llamar al número correcto en menos de 5 segundos; lo mismo para "mandale un sms a Ana" y "abrime el mapa hasta la farmacia".
  2. Si el usuario dice un número de DNI, teléfono o tarjeta dentro del comando, ese dato sale tachado en el payload que va al LLM (verificable en logs `Beto-LLM`).
  3. Long-press en la burbuja abre un sheet de chat donde el usuario puede conversar con Beto en tono cálido y argentino, con respuestas cortas y empáticas.
  4. Si la red se cae durante un comando del guion principal, el sistema cae al matcher determinista de Phase 2 y la acción igual se ejecuta sin que el usuario lo note.
**Plans**: TBD
**UI hint**: yes

### Phase 4: Loop Agéntico de Respaldo + UX Senior
**Goal**: Cuando un Intent fijo falla, Beto intenta silenciosamente operar la pantalla leyendo el árbol de vistas y haciendo clicks reales; la burbuja muestra estados visuales claros y toda la UI propia respeta tipografía senior y tono cálido.
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: AGENTIC-01, AGENTIC-02, AGENTIC-03, AGENTIC-04, AGENTIC-05, OVERLAY-05, UX-01, UX-02, UX-03
**Success Criteria** (what must be TRUE):
  1. Si en un escenario controlado un Intent fijo falla, el loop agéntico completa la acción en hasta 5 iteraciones / 15 segundos sin colgarse, y nunca se invoca como camino principal.
  2. La burbuja cambia de color/ícono entre los 5 estados (idle, listening, thinking, speaking, error) en menos de 200ms ante cada transición real del flujo.
  3. Toda la UI propia (Compañero + atajos) usa tipografía ≥22sp con contraste alto, y los mensajes de error son siempre en tono cálido sin exponer códigos técnicos.
  4. El TTS nunca habla más de una frase por feedback, manteniendo la sensación de paciencia y simplicidad.
**Plans**: TBD
**UI hint**: yes

### Phase 5: Demo Readiness
**Goal**: El día del pitch el equipo entra con dos teléfonos idénticamente configurados, un APK congelado hace 4+ horas, una red dedicada propia, un guion ensayado 5 veces y un video pre-grabado de respaldo, de modo que la demo en vivo sea robusta incluso si algo falla.
**Mode:** mvp
**Depends on**: Phase 4
**Requirements**: DEMO-01, DEMO-02, DEMO-03, DEMO-05, DEMO-06, DEMO-07, DEMO-08, DEMO-09, DEMO-10, SUB-01, SUB-02
**Success Criteria** (what must be TRUE):
  1. El guion completo de 3-5 minutos corre extremo a extremo en modo avión sobre el teléfono principal sin errores visibles, validando el Plan C offline-first.
  2. El hot-spare phone ejecuta el mismo guion sin diferencias notorias, y el APK no se modifica en las últimas 4 horas previas a la demo.
  3. El equipo completa el checklist físico de 16 items con todos los toggles verdes (accessibility, overlay, battery, voz TTS descargada, hotspot ON, modo avión OFF, contactos seedeados) antes de subir al escenario.
  4. Si la demo en vivo falla en cualquier punto, el equipo puede pasar al video pre-grabado de 3 minutos sin más de 5 segundos de transición.
  5. La submission a Platanus Hack 26 está completa: `platanus-hack-project.json` con name + oneliner + descripción en español, README actualizado sin placeholders.
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Sync de Hora 0 | 0/TBD | Not started | - |
| 2. Vertical Slice Mínimo (Plan C Offline) | 0/TBD | Not started | - |
| 3. Motor de Acciones Completo + Compañero | 0/TBD | Not started | - |
| 4. Loop Agéntico de Respaldo + UX Senior | 0/TBD | Not started | - |
| 5. Demo Readiness | 0/TBD | Not started | - |
