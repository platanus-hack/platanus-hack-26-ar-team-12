# Phase 1: Foundation & Sync de Hora 0 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-09
**Phase:** 1-Foundation & Sync de Hora 0
**Areas discussed:** Identidad visual de la burbuja, Pre-flight UX cuando falta permiso, Repo layout + package name, Frase de boot + tono inicial

---

## Identidad visual de la burbuja

| Option | Description | Selected |
|--------|-------------|----------|
| Avatar/cara cariñosa de Beto | Personaje ilustrado simple (símil mascota) que cambia expresión entre estados — más cálido, requiere 5 assets PNG/SVG (~30 min de design) | |
| Logo abstracto del proyecto | El logo `project-logo.png` como avatar dentro del círculo — cero assets nuevos, identidad consistente con README/submission | ✓ |
| Círculo de color con ícono universal | Círculo (color cambia con estado) + ícono Material (mic/check/loader) — cero assets, más neutro, más fácil de implementar | |
| Letra 'B' tipográfica grande | Inicial estilizada en círculo, color cambia con estado — minimalista, instantáneo, solo CSS-equivalente | |

**User's choice:** Logo abstracto del proyecto.
**Notes:** El `project-logo.png` ya existe en raíz (1000×1000). Se copia a `android/app/src/main/res/drawable/`. Los 5 estados se comunican con el color del ring/anillo, no cambiando el logo. Phase 1 deja un placeholder con color por defecto; Phase 4 (`OVERLAY-05`) implementa los 5 estados completos.

---

## Pre-flight UX cuando falta permiso

| Option | Description | Selected |
|--------|-------------|----------|
| TTS + deep link automático a Settings | Beto dice por voz qué falta y abre Settings en la pantalla correcta (Accessibility o Overlay) sin que el usuario navegue | ✓ |
| Pantalla MainActivity con botón grande | MainActivity muestra estado de cada permiso + botón grande que abre Settings respectivo. TTS describe el problema. Menos mágico pero más Senior-friendly | |
| Solo TTS (asume que el dev configura) | TTS dice 'Falta el permiso X' — espera que alguien lo active manualmente. Mínimo código, OK para hackathon | |
| TTS + notificación persistente con tap-to-Settings | TTS + notif que al tocarla abre Settings. Sirve si la voz se pierde | |

**User's choice:** TTS + deep link automático a Settings.
**Notes:** Flow secuencial si faltan varios permisos: resolver uno → re-check al volver al foreground → siguiente. NO mostrar lista al adulto mayor. Si TTS falla, solo se abre el deep link.

---

## Repo layout + package name

### Sub-pregunta: ubicación del proyecto Android

| Option | Description | Selected |
|--------|-------------|----------|
| Subcarpeta `android/` (Recomendado) | Mantén raiz limpia con README/.planning/.git/platanus-hack-project.json. Si después sumamos backend/web es trivial. Estandar para repos hackathon | ✓ |
| Raíz del repo | El proyecto Android en /. Más simple pero ensucia raiz con build/, gradle/, etc. | |

**User's choice:** Subcarpeta `android/`.

### Sub-pregunta: package name

| Option | Description | Selected |
|--------|-------------|----------|
| com.beto.app | Corto, memorable, neutro. Bien para producto futuro | ✓ |
| ar.platanushack.beto | Refleja origen hackathon argentino. Reverse domain válido | |
| io.platanus.beto | Marca Platanus. Útil si la idea sigue viva post-hackathon dentro de Platanus | |
| com.team12.beto | Refleja team-12 del hack. Más 'temporal' | |

**User's choice:** `com.beto.app`.
**Notes:** Estructura inicial de paquetes derivada: `com.beto.app.{bus, service, overlay, voice, llm, action, agent, companion, ui, util}` — superficies disjuntas para 5 devs paralelos.

---

## Frase de boot + tono inicial

| Option | Description | Selected |
|--------|-------------|----------|
| 'Hola, soy Beto. Estoy acá para ayudarte.' | La del research — simple, cálida, presenta a Beto. Óptima para no asustar al adulto mayor en el primer encuentro | ✓ |
| 'Hola, soy Beto. Tocame cuando me necesites, dale.' | Más argentino, más instructivo. Le enseña cómo activarlo de paso | |
| 'Hola, soy Beto. ¿Te ayudo con algo?' | Pregunta abierta, invita a la primera interacción inmediatamente | |
| Frase muy corta sin presentación | 'Estoy listo' o '¡Beto en línea!' — mínima, ya conoce a Beto, asume contexto | |

**User's choice:** "Hola, soy Beto. Estoy acá para ayudarte."
**Notes:** Pronunciada apenas el `BetoForegroundService` arranca y el TTS está listo. Si TTS no está listo aún, se encola en cola interna pre-init y se reproduce cuando `onInit` recibe `SUCCESS` (Pitfall #3).

---

## Claude's Discretion

- Estructura interna de carpetas dentro de `com.beto.app.*` — los 9 sub-paquetes son guía, agruparlos diferente si emerge mejor durante implementación.
- Versiones exactas de dependencias auxiliares — usar SUMMARY.md, ajustar dentro del rango compatible si hay incompatibilidad.
- Iconografía de la notificación del FGS — usar `project-logo.png` redondeado o un drawable derivado.
- Color exacto del ring de la burbuja para el placeholder de Phase 1 — Phase 4 lo sobrescribe.

## Deferred Ideas

- Persistir posición de la burbuja entre boots (SharedPreferences) — nice-to-have, sumar en Phase 4 si sobra tiempo.
- Avatar caricaturizado que cambia entre estados — rechazado para no crear assets en hackathon. Roadmap post-MVP.
- Notificación del FGS variable según estado — Phase 4 si suma valor; Phase 1 fija.
- Onboarding visual completo (Out of Scope en PROJECT.md, v2).
- Wake word (Out of Scope, v2).
- Crashlytics / persistencia de logs — útil post-hackathon, no para demo.
