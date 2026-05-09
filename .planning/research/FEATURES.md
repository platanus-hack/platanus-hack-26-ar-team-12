# Feature Research

**Domain:** Agente Android multimodal autónomo de voz para adultos mayores (operador del teléfono + compañero conversacional + escudo antiestafas)
**Researched:** 2026-05-09
**Confidence:** MEDIUM-HIGH (alta sobre patrones de competidores y restricciones técnicas; media sobre qué subset realmente cabe en 24-36hs sin ensayar)

---

## Mapeo de flujos del producto

Para cada feature mapeamos a uno de los 3 flujos definidos en PROJECT.md:

- **MA** = Motor de Acciones (HERO)
- **MC** = Modo Compañero
- **EA** = Escudo Antiestafas
- **CR** = Cross-cutting (UI/UX/permisos/privacidad — sirve a los tres)

---

## Feature Landscape

### Table Stakes (Sin esto, el adulto mayor abandona el producto)

Lo que el usuario final espera o no puede usar el producto. Si falta, el demo "se ve bien" pero el target real lo descarta. Para hackathon, varios de estos son obligatorios para que la **demo emocional** funcione (el jurado tiene que creer que un adulto mayor podría usarlo).

| Feature | Flujo | Por qué se espera | Complejidad | Notas hackathon |
|---------|-------|------------------|-------------|-----------------|
| **Tipografía grande + alto contraste** en cualquier UI propia (sheet del compañero, confirmaciones, estado del agente) | CR | Adultos mayores con presbicia/baja visión — Necta/BIG Launcher lo tienen como base. Sin esto el producto se ve "para jóvenes" | **S** | Definir 2-3 estilos de Compose (`textStyleHero`, `textStyleBody`) con tamaños 22sp+. Un día de un dev en paralelo |
| **Confirmación por voz antes de acción destructiva o irreversible** ("¿Le mando *este* mensaje a tu nieto?") | MA | Estándar VUI ([Google Design](https://design.google/library/speaking-the-same-language-vui)), y especialmente crítico con adultos mayores que dudan; sin esto el agente da miedo | **S** | Solo afecta enviar mensaje, llamar, abrir links externos. TTS + 1 prompt simple "decí *sí* para confirmar". Latencia importa |
| **Latencia de respuesta corta y predecible** (idealmente <2s desde fin del comando hasta TTS de confirmación) | MA, MC | Adultos mayores tienden a repetir el comando si no hay feedback rápido — esto rompe el STT. Investigación clínica en VUI lo confirma | **M** | Streaming del LLM + TTS apenas llega texto. NO es trivial: requiere arquitectura asíncrona desde el inicio. **Riesgo grande de hackathon** |
| **Feedback constante de estado** ("Te estoy escuchando", "Estoy pensando", "Ya está") por TTS o visual | CR | El usuario no sabe si la app lo escuchó. Sin feedback, repite o se frustra. Patrón obligado en VUI | **S** | Estados de la burbuja: idle/listening/thinking/speaking. Cambio de color + ícono. TTS corto en cada transición |
| **Vocabulario simple, cálido y argentino en TODAS las respuestas** del agente | MC, MA | CLAUDE.md lo define como regla del proyecto. Sin esto el producto no es "para adultos mayores", es "Siri en español" | **S** | System prompt curado + 5-10 few-shots argentinos. Probar en demo: que diga "dale", "tranqui", "ya está". Corregir hasta que sale natural |
| **Single entry point claro** (la burbuja flotante) sin ambigüedad sobre cómo invocar al agente | CR | Adultos mayores se pierden con múltiples affordances. Bald Phone / Necta usan "una sola cosa que aprender". PROJECT.md ya lo decidió | **S** | Ya está en scope. Solo asegurar que la burbuja sea **muy** visible (tamaño, color, no se confunde con notificación) |
| **Tolerancia a errores de STT** — si entiende "WhasAp" o "guhasap", igual abre WhatsApp | MA | El STT nativo de Android tiene errores frecuentes con acento mayor + ruido. Sin tolerancia, el demo falla en vivo | **M** | Fuzzy matching de nombres de app + nombres de contacto (Levenshtein o substring). LLM también ayuda con normalización |
| **Llamar y mandar mensaje (WhatsApp/SMS) por voz, a contacto por nombre** | MA | Es **el** caso de uso principal de adultos mayores con familiares — todos los launchers seniors lo tienen. Sin esto Beto no resuelve nada real | **M** | Top-N Intent path (`Intent.ACTION_VIEW` con `whatsapp://send?phone=`, `Intent.ACTION_CALL`). Ya en scope. Necesita resolver contacto desde nombre fuzzy |
| **Funcionar offline o degradado cuando no hay internet** (al menos botón flotante + indicar el estado) | CR | Adultos mayores frecuentemente están en redes débiles. Si el LLM falla, mostrar un error cálido — no una excepción técnica | **S** | Try/catch alrededor del cliente LLM → TTS "Disculpá, no pude entenderte ahora. Intentemos de nuevo". Crítico para demo si la WiFi del venue se cae |
| **Respeto por privacidad básica** (no enviar DNI/teléfono/tarjeta plain text al cloud) | CR | Audiencia adulta mayor es target #1 de fraude — confianza es el producto. PROJECT.md ya lo lista | **S** | Regex sanitizer ya en scope. Mostrar en pitch que sucede on-device |
| **Onboarding-zero o casi cero** — el adulto mayor no configura nada | CR | Necta, GrandPad, BIG Launcher venden "el familiar lo configura una vez y listo". Si Beto requiere setup activo del usuario, fracasa con el target real | **S (para demo)** / **L (para producto real)** | En hackathon: PROJECT.md ya asume permisos pre-configurados. **Aclararlo en pitch** (es honesto y refuerza la idea de "el familiar se lo instala") |

### Differentiators (Ventaja competitiva — Beto's bet)

Donde Beto compite. Apple Intelligence (spring 2026) y Gemini Screen Automation (Pixel 10/Galaxy S26) están entrando justo ahora pero cubren **otra audiencia** (early adopters, US/Korea, apps específicas). Beto compite en **es-AR + adultos mayores + cualquier app vía AccessibilityService**.

| Feature | Flujo | Value proposition | Complejidad | Notas hackathon |
|---------|-------|------------------|-------------|-----------------|
| **Loop agéntico universal** sobre AccessibilityNodeInfo (LLM ve árbol de vistas → decide click/scroll/type → performAction) | MA | Esto es **la tesis**. Apple Intelligence requiere App Intents adoptadas por cada app. Gemini Screen Automation solo soporta 6 apps en US. Beto promete cualquier app | **L** | El loop es frágil por naturaleza (PROJECT.md lo reconoce). Para la demo, basta que funcione **una vez** sobre una app fuera del top-N (ej: setear despertador en Reloj). MVP: 1 demostración exitosa, no robustez general |
| **Híbrido Intents fijos + loop agéntico** | MA | Garantiza que el guion principal nunca falle (Intents = path confiable) **y** muestra ambición (loop). Apple/Gemini no tienen este patrón explícito porque no lo necesitan en sus walled gardens | **M** | Ya en scope. Diseñar el "router": classifier decide si el comando matchea top-N → Intent; si no → loop. Importante: el classifier debe ser **rápido** (LLM o reglas) |
| **Tono empático en es-AR** ("dale viejito, lo hacemos juntos", "tranqui, ya está") | MC, MA | Apple/Gemini son neutrales, frías, anglo-céntricas. Meela/SeniorTalk son en inglés. **Nadie tiene tono argentino para abuelos** | **S** | System prompt + few-shots. Sin costo extra en runtime. Demo killer feature: el jurado lo escucha y se ríe-se-emociona |
| **Modo Compañero conversacional** integrado en el mismo agente que ejecuta acciones | MC | ElliQ ($600+ hardware) y Meela (calls programadas) son productos separados de "operar el teléfono". Beto los junta — es soledad **+** brecha digital en una sola app | **S** (dado que LLM + UI básica ya están) | Ya en scope. System prompt diferente al del Motor. Mismo cliente LLM. Hold/long-press en burbuja → sheet de chat |
| **Filtrado on-device de datos sensibles** antes de cloud LLM (regex de DNI/teléfono/tarjeta) | CR | Diferenciador narrativo grande — pitch a un público que sabe que abuelos son target de fraude. Apple Intelligence lo hace pero no lo expone, Gemini no | **S** | Ya en scope. Versión simple = regex. Mostrar en demo: input "mi DNI es 12345678" → request al LLM con "mi DNI es ████████" |
| **Escudo Antiestafas** (clasificador on-screen de mensajes WhatsApp sospechosos + overlay rojo + alerta TTS) | EA | Meta lanzó scam warnings en oct/2025 ([TechCrunch](https://techcrunch.com/2025/10/21/whatsapp-and-messenger-add-new-warnings-to-help-older-people-avoid-online-scams/)) pero solo desde *unknown contacts*. Beto puede flaggear de cualquier remitente, en cualquier app, en es-AR. Diferencial fuerte | **L** | PROJECT.md lo movió a out-of-scope. **Recomendación:** dejarlo para post-MVP en código, **mencionarlo en pitch como roadmap** con un mockup. No arriesgar el sprint |
| **Activación universal por burbuja flotante** (vs Apple/Gemini que requieren wake word + permisos OS-level) | CR | El adulto mayor no aprende "Hey Siri/Hey Gemini". Una burbuja persistente siempre visible es más simple. Bald Phone/Necta usan paradigma similar | **M** | Ya en scope. Asegurar que sobreviva reboots (foreground service + autostart hint) |
| **Resolución de entidades fuzzy** (nombre contacto + nombre app + intención compleja) | MA | Apple App Intents son rígidos (parámetros tipados). Gemini funciona pero solo en apps validadas. Beto promete "avisale a mi nieto que ya llegué" → resuelve "mi nieto" → contacto correcto + redacta mensaje + manda | **M** | LLM con tool calling. Schema simple: `{intent: "send_message", contact_query: "mi nieto", body: "..."}`. Resolver contact_query contra `ContactsContract` con fuzzy match |
| **Demo en vivo confiable** (3-5 min, guion ensayado) | CR | Hackathon-meta: la mayoría de proyectos fallan en vivo. Tener un guion **ensayado** que funciona reliably es competitive advantage frente al jurado | **M** | Setup del teléfono dedicado + ensayo. Ya en scope. **No subestimar** — son ~3-4 horas de polish |

### Anti-Features (Tentadoras pero NO construir)

| Feature | Por qué tienta | Por qué problemático | Alternativa |
|---------|---------------|---------------------|-------------|
| **Wake word "Beto"** (Porcupine, Vosk, Whisper streaming) | Suena natural — "Hey Beto, llamá a mi hija" | Porcupine es non-commercial en free tier (riesgo legal en pitch). Vosk/Whisper streaming on-device es 1-2 días + drena batería + falsos positivos. La burbuja flotante cubre el rol con cero riesgo | Burbuja flotante con tap único. Wake word = post-hackathon roadmap |
| **Onboarding visual de permisos** (AccessibilityService + SYSTEM_ALERT_WINDOW) | "Es lo correcto para producción", educa al usuario | Esos permisos requieren navegación a Settings que **no se puede automatizar**. Construir un flujo guiado consume horas y no aporta a la demo (que asume teléfono pre-configurado) | PROJECT.md ya decidió: pre-configurar el teléfono manualmente. Mencionarlo honestamente en pitch ("lo instala el familiar una vez") |
| **Cuenta de usuario / login / sync entre dispositivos** | Parece "producto serio" | Para demo single-device es overhead enorme (auth, backend, persistencia). El target real (abuelo + 1 teléfono) no necesita multi-device | Single-device, single-user, stateless entre sesiones (PROJECT.md lo confirma) |
| **Historial de conversaciones persistente** | "Memoria del agente" suena bien | Persistencia + UI de historial + privacidad de un historial = scope grande. En demo, contexto en memoria de sesión alcanza | Contexto in-memory por sesión. Reset al cerrar app. Postergar |
| **Notificaciones push / proactividad** ("Hola, ¿cómo estás hoy?") | ElliQ y Meela lo hacen — feels caring | Adultos mayores se asustan o irritan con notificaciones inesperadas. Requiere lógica de timing, opt-in, fatigue management. Para hackathon = tiempo perdido | Solo respuestas a invocación del usuario. Proactividad = roadmap post-validación |
| **Configuración del agente** (settings, voz, velocidad, "personalidad") | "Personalización es UX moderna" | El target real **no** entra a Settings. Si Beto requiere config para ser útil, fracasa con abuelos. Cada toggle es complejidad | Cero settings visibles. Defaults curados. Un dev/familiar puede tocar resources si hace falta |
| **Suscripción / paywall / gating de features** | Modelo de negocio | Fuera de scope para hackathon. Distrae del pitch | Discusión post-hackathon |
| **NER on-device sofisticado para sanitizar** (ML Kit, TF Lite) | "Más robusto que regex" | 2-3 días de trabajo, muchísimo riesgo en demo (modelos no embarcados, latencia). PROJECT.md ya lo descartó | Regex DNI/teléfono/tarjeta. Cuenta la historia en pitch. Roadmap post |
| **Ejecutar acciones bancarias / login en apps sensibles** vía loop agéntico | Casos de uso "wow" | Apps bancarias bloquean accessibility por seguridad (PROJECT.md lo nota). Riesgo legal + técnico altísimo. Apple/Gemini explícitamente lo excluyen ([AppleInsider](https://appleinsider.com/articles/25/08/10/new-siri-will-bring-voice-control-to-just-about-all-apps----but-maybe-not-banking)) | Excluir explícitamente del scope. En pitch decir "Beto no toca tu banco" — refuerza confianza |
| **Multi-idioma** (es-ES, en, pt) | "Más mercado" | Tono argentino es **diferenciador**, no defecto. Multi-idioma diluye el pitch | Solo es-AR. Mejor focalizado |
| **Modo manos-libres total** (siempre escuchando) | Real assistant feel | Siempre escuchando = batería + falsos positivos + privacidad + Porcupine. Ver wake word arriba | Burbuja flotante con tap |
| **Animaciones / personaje visual / avatar de Beto** | "Carisma del agente" | Tiempo de diseño + assets + tuning. La voz cálida ya carga la personalidad | Solo emojis o íconos simples en UI. Personalidad = tono del LLM |

---

## Feature Dependencies

```
Burbuja flotante (SYSTEM_ALERT_WINDOW)
    └──requires──> Permisos pre-otorgados (asumido)
    └──enables──>  Captura de voz (tap)
    └──enables──>  Modo Compañero (long-press)

Captura de voz (RecognizerIntent es-AR)
    └──feeds────> Sanitizador on-device (regex)
                       └──feeds──> Cliente LLM (cloud)
                                       └──returns──> Intent estructurado / texto

Intent estructurado
    ├──top-N──>   Android Intent fijo (WhatsApp/llamar/SMS/Maps)
    │                └──requires──> Resolución de contacto fuzzy
    │                └──requires──> Confirmación por voz (acciones destructivas)
    │
    └──fallback──> Loop agéntico
                        └──requires──> AccessibilityService activo
                        └──requires──> Lectura AccessibilityNodeInfo tree
                        └──requires──> LLM con visión (opcional captura) + tool calling
                        └──invokes───> performAction() (click/scroll/type)

Cualquier acción
    └──ends──> TTS feedback (es-AR)
    └──updates──> Estado de la burbuja (visual)

Modo Compañero (sheet de chat)
    └──reuses──> Cliente LLM
    └──requires──> System prompt distinto al Motor de Acciones
    └──reuses──> TTS opcional (puede ser solo texto)

Escudo Antiestafas (post-MVP)
    └──requires──> AccessibilityService (lectura WhatsApp)
    └──requires──> Clasificador (LLM o heurística)
    └──requires──> Overlay rojo (SYSTEM_ALERT_WINDOW reutilizado)
    └──requires──> TTS de alerta
    └──conflicts──> Latencia de Motor de Acciones (compite por mismos recursos en runtime)
```

### Dependency Notes

- **Burbuja flotante es root del árbol** — sin ella ningún flujo se invoca. Punto único de falla → priorizar robustez (foreground service)
- **Confirmación por voz depende de TTS funcionando temprano** — TTS es task de día 1, no día 2
- **Loop agéntico depende del classifier que decide top-N vs fallback** — si el classifier es lento, mata la latencia. Considerar reglas simples antes de invocar LLM
- **Modo Compañero comparte cliente LLM con Motor de Acciones** — diseñar `LlmClient` con dos system prompts intercambiables, no dos clientes separados
- **Escudo conflicta con el Motor en runtime**: si Beto está escuchando WhatsApp todo el tiempo para flaggear estafas, eso compite por CPU/red con la respuesta rápida del Motor. En MVP están desacoplados (Escudo out-of-scope) — pero cuando entren juntos, requiere arquitectura de prioridades
- **Sanitizador on-device debe correr en path crítico** — entre STT y LLM. No puede ser async. Es S pero no se puede saltar

---

## MVP Definition

### Launch With (Demo del hackathon — guion principal)

Lo mínimo para que la tesis del producto se demuestre en vivo en 3-5 min. Equivalente a "Active" en PROJECT.md, con priorización dura.

- [ ] **Burbuja flotante persistente** (SYSTEM_ALERT_WINDOW, foreground service) — sin esto no hay producto
- [ ] **Captura de voz por tap** (RecognizerIntent es-AR) — sin esto no hay input
- [ ] **Cliente LLM con tool calling** (provider TBD en STACK research) — sin esto no hay agente
- [ ] **Sanitizador regex on-device** (DNI/teléfono/tarjeta) — narrativa de privacidad en pitch
- [ ] **Intents fijos para 3-4 acciones top** (mandar WhatsApp, llamar, mandar SMS, abrir Maps) — el path **confiable** del demo
- [ ] **Resolución de contacto fuzzy** (`ContactsContract` + match aproximado) — sin esto, "mi nieto" no resuelve y todo el demo falla
- [ ] **Confirmación por voz antes de mandar/llamar** ("¿le mando *este* mensaje?") — UX correcta + safety
- [ ] **TTS feedback en es-AR con tono cálido** — sin esto el agente parece robot, pierde el alma del producto
- [ ] **Tipografía grande + alto contraste** en el sheet de Compañero y cualquier UI propia — credibilidad con jurado de que es para abuelos
- [ ] **Modo Compañero mínimo** (long-press → sheet de chat con system prompt cálido) — alma del producto, casi gratis dado el cliente LLM
- [ ] **Loop agéntico con UNA demo exitosa** (ej: "ponete una alarma a las 7" → abre Reloj → setea alarma) — prueba la visión universal **una vez**
- [ ] **Manejo de errores cálido** (try/catch + TTS "no pude, perdoná, probemos de nuevo") — si algo falla en vivo
- [ ] **Setup de demo:** teléfono dedicado + contactos seedeados + WhatsApp instalado + guion ensayado 2-3 veces

### Add After Validation (post-hackathon, pre-producto real)

- [ ] **Escudo Antiestafas v1** (clasificador básico de WhatsApp + overlay rojo) — trigger: validamos en pitch que la audiencia compra la idea
- [ ] **Wake word "Beto"** (cambiar Porcupine free tier por Vosk u otro open source) — trigger: usuarios reales dicen que prefieren manos libres a tap
- [ ] **Onboarding asistido de permisos** (deep link a Settings + tutorial visual paso a paso) — trigger: distribución a familias reales (no demo)
- [ ] **Cloud STT** (Whisper / Realtime API) con fallback offline — trigger: notamos que el STT nativo falla mucho con acentos en testing real
- [ ] **Modelo de privacidad más profundo** (ML Kit NER, redacción visual de capturas) — trigger: feedback de usuarios o jurado sobre privacidad
- [ ] **Tono adaptado por usuario** (más formal, más casual) — trigger: pruebas A/B con familias
- [ ] **Más Intents fijos en el top-N** (Spotify, calendario, recordatorios, fotos) — trigger: telemetría de qué piden los usuarios reales
- [ ] **Persistencia de contexto entre sesiones** (memory simple) — trigger: usuarios pidiendo "Beto, ¿qué te dije ayer?"

### Future Consideration (v2+)

- [ ] **Proactividad** (Beto inicia conversaciones, recuerda tomar pastilla, etc.) — defer: requiere modelo de timing + opt-in serio + fatigue management
- [ ] **Multi-dispositivo / cuenta familiar** (familiar configura desde su teléfono) — defer: requiere backend, auth, sync — fuera de DNA de demo
- [ ] **Integraciones con dispositivos médicos / wearables** — defer: validación clínica, regulación
- [ ] **Multi-idioma (es-ES, pt-BR, en)** — defer: el tono argentino es el diferenciador; expansión geográfica viene después de PMF
- [ ] **iOS** — defer: sin AccessibilityService equivalente, la tesis no se sostiene en iOS hasta que App Intents universalice (2026+)
- [ ] **Modelo on-device** (Gemma 4 mobile, Llama mobile) — defer: latencia/calidad/batería no compiten todavía con cloud para nuestro caso

---

## Feature Prioritization Matrix

Priorización dura para el sprint de 24-36hs. P1 = cae si no está; P2 = polish si sobra tiempo; P3 = roadmap.

| Feature | Flujo | User value | Implementation cost | Priority |
|---------|-------|-----------|---------------------|----------|
| Burbuja flotante persistente | CR | HIGH | LOW | **P1** |
| Captura de voz por tap (STT nativo) | MA | HIGH | LOW | **P1** |
| Cliente LLM + tool calling | MA, MC | HIGH | MEDIUM | **P1** |
| Sanitizador regex | CR | MEDIUM (narrativa) | LOW | **P1** |
| 3-4 Intents fijos (WhatsApp/llamar/SMS/Maps) | MA | HIGH | MEDIUM | **P1** |
| Resolución de contacto fuzzy | MA | HIGH | MEDIUM | **P1** |
| Confirmación por voz | MA | HIGH | LOW | **P1** |
| TTS feedback en es-AR | CR | HIGH | LOW | **P1** |
| Tipografía grande + alto contraste | CR | HIGH | LOW | **P1** |
| Modo Compañero (sheet chat) | MC | MEDIUM | LOW | **P1** |
| System prompt cálido es-AR | MC, MA | HIGH | LOW | **P1** |
| Loop agéntico — UNA demo exitosa | MA | HIGH | HIGH | **P1** (riesgo) |
| Manejo de errores cálido | CR | MEDIUM | LOW | **P1** |
| Tolerancia a errores STT (fuzzy app/contact) | MA | MEDIUM | MEDIUM | **P2** |
| Latencia <2s end-to-end | CR | HIGH | HIGH | **P2** (target, no gating) |
| Estado visual rico de la burbuja (idle/listening/...) | CR | MEDIUM | LOW | **P2** |
| Loop agéntico robusto sobre múltiples apps | MA | HIGH | HIGH | **P3** |
| Escudo Antiestafas (mockup en pitch) | EA | HIGH | LOW (solo mockup) | **P2** (visual) |
| Escudo Antiestafas (real, runtime) | EA | HIGH | HIGH | **P3** |
| Wake word | CR | MEDIUM | HIGH | **P3** |
| Onboarding de permisos | CR | LOW (demo) / HIGH (real) | MEDIUM | **P3** |
| Cloud STT | MA | MEDIUM | MEDIUM | **P3** |

**Lectura de la matriz:**
- 13 P1s — todos chicos o medianos excepto el loop agéntico. **El loop es el único riesgo grande de scope dentro del MVP.** Tiempo-box explícito (ej: 6 horas de un dev) y si no funciona, demo solo Intents fijos
- Latencia <2s es target pero no debe ser gate (si tarda 3s pero funciona, alcanza para demo)
- Escudo en P2 solo como **mockup visual en pitch deck** (1 hora de un dev, gran retorno narrativo)

---

## Competitor Feature Analysis

| Feature | Apple Intelligence (Siri 2026) | Google Gemini Android (Pixel 10 / S26) | HMD AI seniors (2026) | ElliQ / Meela | Necta / BIG Launcher | **Beto (nuestro plan)** |
|---------|-------------------------------|-----------------------------------------|----------------------|---------------|----------------------|--------------------------|
| **Operación universal del teléfono por voz** | Sí, vía App Intents adoptados por la app (rígido) | Sí, en 6 apps verificadas (Lyft, Uber, DoorDash, etc.), beta US/Korea | No (es feature phone con AI básico) | No (companion only) | No (lanza apps, no las opera) | **Sí**, vía AccessibilityService — universal pero frágil. Diferenciador clave |
| **Foco en adultos mayores** | No (audiencia general) | No (audiencia general, además limitado a Pixel 10/S26) | Sí (HMD partnership con inTouch, voz lenta, contexto personal) | Sí (compañía + recordatorios, hardware/calls) | Sí (UI accesible, no agente) | **Sí**, pero como agente que opera el teléfono — **único en intersección** |
| **Idioma argentino (es-AR) + tono** | No (es genérico) | No (es-LATAM neutro) | No (en/global) | No (en) | No (es genérico) | **Sí, diferenciador fuerte** |
| **Confirmación antes de acción** | Sí (estándar Siri) | Sí (pausa pidiendo control en tareas sensibles) | N/A | N/A | N/A | **Sí**, en acciones destructivas |
| **Funciona en apps bancarias** | No (excluido por seguridad) | No (excluido) | N/A | N/A | N/A | **No** (excluir explícitamente; refuerza confianza) |
| **Wake word** | Sí ("Hey Siri") | Sí ("Hey Google") | Sí (en algunos modelos) | Hardware dedicado | No | **No** (burbuja flotante, post-MVP wake word) |
| **Detección de estafas** | No directo | No directo | No | No | No | **Roadmap (Escudo Antiestafas)** — Meta WhatsApp lo hace solo desde unknowns; Beto lo extiende |
| **Compañía conversacional** | No (es task-oriented) | Limitado | Sí (vía partner inTouch) | Sí (es el producto) | No | **Sí, integrado** con el Motor de Acciones |
| **Privacy on-device antes de cloud** | Sí (Private Cloud Compute) | Parcial | Limitado | Cloud | No | **Sí (regex simple, narrativa fuerte)** |
| **Modelo de distribución** | Built-in iOS 26+ | Built-in Android 16 (Pixel/S26) | Hardware con AI | Hardware $ + suscripción | Launcher app | **App Android instalable** (ideal: el familiar la instala una vez) |
| **Costo de desarrollo de 24-36hs** | N/A | N/A | N/A | N/A | N/A | **Alto pero focalizado** — la verticalidad nos salva del scope-creep |

**Conclusión competitiva:** Beto vive en una **intersección no ocupada**: agente que opera el teléfono **+** target adultos mayores **+** español argentino con tono cálido **+** universal vía AccessibilityService **+** integra compañía + acciones + (futuro) escudo. Apple/Gemini se le acercan en agency pero no en target ni tono. ElliQ/Meela compiten en compañía pero no operan el teléfono. Necta/BIG son UI estática, no agente. **El moat real es el tono + la verticalidad + la integración de los 3 flujos** — no la tecnología base.

---

## Sources

- [WhatsApp and Messenger add new warnings to help older people avoid online scams (TechCrunch, 2025-10-21)](https://techcrunch.com/2025/10/21/whatsapp-and-messenger-add-new-warnings-to-help-older-people-avoid-online-scams/)
- [Meta Boosts Elder Scam Protection on WhatsApp & Messenger (MEF, 2025-11-27)](https://mobileecosystemforum.com/2025/11/27/meta-boosts-elder-scam-protection-on-whatsapp-messenger/)
- [HMD Smart Feature Phones Bring AI and Digital Wallet Access (TechTimes, 2026-03-03)](https://www.techtimes.com/articles/314901/20260303/hmd-smart-feature-phones-bring-ai-digital-wallet-access-bridge-gap-older-offline-users.htm)
- [HMD to bring Digital Wallets, AI, and Video Calling to feature phones in H1 2026 (FoneArena)](https://www.fonearena.com/blog/476679/hmd-2026-feature-phone-innovations.html)
- [Apple Intelligence & Siri in 2026 (Medium / Taoufiq El Moutaouakil)](https://medium.com/@taoufiq.moutaouakil/apple-intelligence-siri-in-2026-fe509d8813fd)
- [Apple Overhauls Siri to Enable Voice-Controlled App Actions (AInvest)](https://www.ainvest.com/news/apple-overhauls-siri-enable-voice-controlled-app-actions-2508/)
- [Integrating actions with Siri and Apple Intelligence (Apple Developer)](https://developer.apple.com/documentation/appintents/integrating-actions-with-siri-and-apple-intelligence)
- [New Siri will bring voice control to just about all apps -- but maybe not banking (AppleInsider)](https://appleinsider.com/articles/25/08/10/new-siri-will-bring-voice-control-to-just-about-all-apps----but-maybe-not-banking)
- [Ask Gemini to handle your multi-step tasks in select Android apps (Gemini Help)](https://support.google.com/gemini/answer/16940971?hl=en)
- [Google just gave Gemini the power to control apps on the Galaxy S26 (Android Central)](https://www.androidcentral.com/apps-software/gemini-screen-automation-rolling-out-for-galaxy-s26)
- [Gemini control to Android: Will we be talking to our phones more in 2026? (9to5Google)](https://9to5google.com/2025/12/29/gemini-android-control/)
- [Mobile AI Agents Tested Across 65 Real-World Tasks (AIMultiple)](https://aimultiple.com/mobile-ai-agent)
- [MobileAgentBench: An Efficient and User-Friendly Benchmark for Mobile LLM Agents (arXiv 2406.08184)](https://arxiv.org/pdf/2406.08184)
- [AutoDroid-V2: Boosting SLM-based GUI Agents via Code Generation (arXiv 2412.18116)](https://arxiv.org/pdf/2412.18116)
- [GitHub - X-PLUG/MobileAgent: Mobile-Agent GUI Agent Family](https://github.com/x-plug/mobileagent)
- [GPTVoiceTasker: LLM-Powered Virtual Assistant for Smartphone (arXiv 2401.14268)](https://arxiv.org/html/2401.14268v1)
- [Examining the Use of Intelligent Conversational Voice-Assistants for Older Adults (Taylor & Francis, 2024)](https://www.tandfonline.com/doi/full/10.1080/10447318.2024.2344145)
- [Investigating the Accessibility of Voice Assistants With Impaired Users (PMC)](https://pmc.ncbi.nlm.nih.gov/articles/PMC7547392/)
- [Voice User Interface (VUI) Design Principles (Parallel HQ, 2026)](https://www.parallelhq.com/blog/voice-user-interface-vui-design-principles)
- [UI & UX Principles for Voice Assistants (Google Design)](https://design.google/library/speaking-the-same-language-vui)
- [Necta Launcher review (AndroidAyuda)](https://en.androidayuda.com/help-to-use-your-android-small-and-old-with-necta-launcher/)
- [BIG Launcher | BIG Phone | BIG SMS for seniors](http://biglauncher.com/)
- [ElliQ - Companion Robot for Seniors](https://elliq.com/)
- [Meela - AI Companion for Older Adults](https://www.meela.ai/)
- [SeniorTalk — AI Companion for Elderly People](https://www.senior-talk.com/)
- [Top 19 Mobile Apps for Better Senior Living in 2026 (Senior Safety Advice)](https://seniorsafetyadvice.com/best-phone-apps-for-seniors/)
- [Easy-to-Use Messaging Apps for the Elderly (Morada Senior Living)](https://moradaseniorliving.com/senior-living-blog/easy-to-use-messaging-apps-for-the-elderly/)

---
*Feature research for: Beto — agente Android multimodal autónomo de voz para adultos mayores*
*Researched: 2026-05-09*
