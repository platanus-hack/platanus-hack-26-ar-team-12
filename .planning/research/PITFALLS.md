# Pitfalls Research — Beto

**Domain:** Agente Android multimodal autónomo (AccessibilityService + LLM tool calling + STT/TTS + overlay flotante) construido en hackathon de 24-36hs con demo en vivo de 3-5 min frente a jurado.
**Researched:** 2026-05-09
**Confidence:** HIGH (fuentes oficiales Android Developers + post-mortems comunidad + experiencia documentada en proyectos similares)

> **Lente del documento:** cada pitfall se evalúa por **cuánto puede matar la demo en vivo**, no por deuda técnica. Bug que impacta producción a escala — fuera de scope. Bug que cuelga al agente cuando el jurado mira — flag rojo crítico.

---

## Critical Pitfalls (matan la demo)

### Pitfall 1: AccessibilityService desactivado silenciosamente por el sistema entre el setup y la demo

**Síntoma:**
- En logs: silencio total. `onAccessibilityEvent` deja de invocarse. `onServiceConnected` nunca se vuelve a llamar.
- En la app: la burbuja flotante reacciona al tap, pero el agente "no ve" lo que pasa en pantalla; los `findAccessibilityNodeInfosByText` devuelven `null` o lista vacía.
- En Settings → Accesibilidad: el toggle de Beto aparece como apagado aunque ayer estaba encendido.

**Por qué pasa:**
- Reinicio del teléfono de demo (¿se quedó sin batería?) — algunas OEMs (Xiaomi/MIUI especialmente, Samsung One UI en menor medida) **resetean los toggles de accesibilidad de apps que no son de "la lista blanca" de Google Play** después de OTA, force-stop, o a veces simplemente tras horas de inactividad.
- App update / reinstall durante el sprint: cualquier `adb install -r` o cambio de signing key invalida el toggle.
- Crash repetido del service → Android lo deshabilita automáticamente.
- "Restricted settings" en Android 13+: si la app fue instalada desde fuera de Play Store (lo cual será el caso en hackathon: APK por adb), el sistema bloquea el toggle de accesibilidad por defecto y obliga a habilitar manualmente "Allow restricted settings" desde el menú de tres puntos en App Info.

**Cómo detectarlo temprano:**
- Heartbeat en logs cada 10s desde `onAccessibilityEvent` → si pasan >30s sin heartbeat, el service murió.
- Pre-flight check al apretar la burbuja: `AccessibilityManager.isEnabled() && AccessibilityManager.getEnabledAccessibilityServiceList().any { it.id contains BuildConfig.APPLICATION_ID }`. Si false → mostrar overlay rojo "Accesibilidad apagada — abrí Ajustes".
- Test ritual antes de cada ensayo y antes de subir al escenario: ejecutar un comando trivial ("decime hola") y verificar que TTS responde.

**Prevención:**
- **Documentar en checklist físico de demo** (papel, no digital): (1) toggle Accessibility ON, (2) toggle Display over apps ON, (3) toggle Battery → Unrestricted, (4) toggle "Allow restricted settings" si Android 13+, (5) "Don't optimize" para Beto en Settings → Battery.
- En `accessibility_service_config.xml`, setear `canRetrieveWindowContent="true"` y `accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds"` para reducir restricciones que el sistema use como excusa para deshabilitar.
- Foreground service "Beto activo" además del AccessibilityService — el FGS actúa de tutor (re-arma el listener si nota silencio anormal).
- **NO actualizar el APK del teléfono de demo en las últimas 4 horas** antes de subir al escenario. Cada install es un reset potencial.

**Plan B en demo en vivo:**
- Si la primera interacción no responde: presentador dice "perdón, le doy un toque a Beto" mientras el dev de turno **abre Settings → Accessibility** desde un atajo en homescreen (poner shortcut visible) y vuelve a togglear. <8 segundos si está ensayado.
- Tener **dos teléfonos idénticamente configurados**. Uno principal, uno hot-spare. Si el principal se cuelga, el presentador continúa con el spare sin cortar la narrativa.

**Fase del roadmap:**
Fase 1 (Setup base — AccessibilityService + permisos). Verificación en Fase final (Demo readiness).

---

### Pitfall 2: SpeechRecognizer no funciona cuando se dispara desde un Service (no Activity)

**Síntoma:**
- `SpeechRecognizer.createSpeechRecognizer(context).startListening(intent)` retorna sin error, pero `onReadyForSpeech` nunca se llama, o se llama y luego `onError` con código 7 (`ERROR_NO_MATCH`) o 9 (`ERROR_INSUFFICIENT_PERMISSIONS`).
- Logcat muestra "RecognitionService: Recognition aborted" o "Caller does not have permission to record audio".
- Funciona perfecto cuando lo testeás en una Activity de prueba, falla cuando lo movés al servicio que se dispara desde la burbuja flotante.

**Por qué pasa:**
- `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` lanzado vía `startActivity()` necesita un Activity context. Desde un service tenés que usar `SpeechRecognizer` directamente (no el Intent que abre la UI estándar).
- `SpeechRecognizer` puede invocarse desde un service **pero solo desde el main thread** y requiere que el proceso tenga foreground (audio permission denegado en background después de Android 11+).
- Sin foreground service activo en el momento del `startListening`, Android puede silenciosamente bloquear el acceso al micrófono.
- `RECORD_AUDIO` runtime permission no otorgado o revocado en algún momento.

**Cómo detectarlo temprano:**
- Implementar un `RecognitionListener` con **todos** los callbacks logueando `Log.d("Beto-STT", ...)` con el código de error. Si solo escuchás `onReadyForSpeech` sin `onResults`, hay corte; si nunca llega `onReadyForSpeech`, hay bloqueo.
- Test en frío: cerrar la app completamente, reabrirla, primer comando — si falla la primera y funciona la segunda, hay race con permissions.
- Logear `ContextCompat.checkSelfPermission(RECORD_AUDIO)` antes de cada `startListening`.

**Prevención:**
- Foreground service "Beto activo" **siempre activo** mientras la burbuja está visible. Tipo `microphone` o `mediaProjection` según uso (Android 14+ exige tipo explícito).
- Crear el `SpeechRecognizer` en el main thread vía `Handler(Looper.getMainLooper()).post { ... }`.
- Tener fallback a `RecognizerIntent` (la UI estándar de Google) si la primera llamada falla — abrir una Activity transparente que dispare el Intent. Más feo visualmente pero confiable.
- Verificar `SpeechRecognizer.isRecognitionAvailable(context)` al boot y bloquear toda la app si false.
- **Pre-warmup:** llamar a `SpeechRecognizer.createSpeechRecognizer()` y mantener la instancia viva — recrearla por sesión introduce delay y errores.

**Plan B en demo en vivo:**
- Si el primer comando de voz falla: presentador apoya "voy a tipear lo que le quería decir" y muestra un input de texto de respaldo (botón secundario en la burbuja flotante en long-press, o atajo visible). Mantiene la narrativa de "agente entiende lenguaje natural" sin depender de STT.
- **Pre-cargar comandos del guion como botones ocultos** que el dev a 1 metro puede activar si el STT falla (Wizard of Oz fallback honesto: ensayar para que solo se use si falla).

**Fase del roadmap:**
Fase 2 (Voz: STT/TTS). Verificación con tests en frío y con app cerrada antes de Fase final.

---

### Pitfall 3: TextToSpeech race condition — primer `speak()` se pierde porque init no terminó

**Síntoma:**
- El primer comando del demo: el agente hace la acción pero **no dice nada**. Del segundo en adelante habla.
- `tts.speak(...)` retorna `TextToSpeech.SUCCESS` pero no sale audio.
- Logcat: silencio o "TextToSpeech not initialized" o "Language not available".

**Por qué pasa:**
- `TextToSpeech` es asíncrono: el constructor retorna inmediatamente pero `OnInitListener.onInit(SUCCESS)` puede tardar 200-2000ms.
- Si llamás `speak()` antes del `onInit`, la llamada se descarta silenciosamente (no encola).
- Algunos teléfonos (especialmente con motor de Samsung en lugar de Google) requieren `setLanguage(Locale("es","AR"))` que retorna `LANG_MISSING_DATA` o `LANG_NOT_SUPPORTED` → fallback necesario a `es-ES` o `es-MX`.
- Si la voz es-AR no está descargada, el motor TTS pide descarga la primera vez (en la red de hackathon esto puede colgar 30s).

**Cómo detectarlo temprano:**
- En `onInit`, logear `setLanguage` result code y la lista de `tts.voices` filtrada por idioma `es`.
- Test en frío: instalar la app, abrirla, mandar primer comando inmediatamente. Si no habla → race confirmada.
- Pre-warmup del TTS al lanzar el foreground service, mucho antes del primer comando.

**Prevención:**
- Inicializar TTS en `Application.onCreate()` o al instanciar el FGS, **no** en el momento del comando. Esperar `onInit` antes de marcar "Beto listo".
- Cola interna: si `speak()` se invoca antes de init, encolar y flush en `onInit`.
- Fallback de Locale en cascada: `es-AR` → `es-419` → `es-ES` → `es` → `en-US`. Logear cuál se usa.
- **Pre-descargar la voz es-AR en el teléfono de demo manualmente** desde Settings → Accessibility → Text-to-speech output → Install voice data. No depender de la red de hackathon.
- Testear el speak con una frase de boot ("hola, soy Beto") al iniciar el FGS — confirma que todo el pipeline funciona desde antes del primer comando del usuario.

**Plan B en demo en vivo:**
- Si TTS no habla: el agente igual hace la acción visible (mensaje de WhatsApp se manda, llamada se inicia). El presentador narra en voz alta "como ven, Beto está mandando el mensaje" — la falta de TTS pasa como detalle, no como fallo.
- Toast/snackbar en la burbuja con el texto que el TTS debía decir, como respaldo visual.

**Fase del roadmap:**
Fase 2 (Voz: STT/TTS). Pre-warmup integrado en Fase 1 (Foreground service base).

---

### Pitfall 4: WhatsApp Intent falla porque el contacto no existe / formato de URL incorrecto

**Síntoma:**
- Comando "mandale a mi nieto que ya llegué" dispara la acción, WhatsApp abre, pero muestra "Phone number shared via url is invalid" o abre un chat vacío sin pre-llenar el texto, o abre WhatsApp Business en lugar del WhatsApp normal.
- En el peor caso: WhatsApp abre y queda en el listado de chats — el usuario tiene que tappear manualmente.
- Logcat: `ActivityNotFoundException` si WhatsApp no maneja el Intent.

**Por qué pasa:**
- El formato `https://wa.me/<phone>?text=<urlEncodedText>` requiere número en formato internacional **sin** `+`, sin espacios, sin guiones (ej: `5491166778899`, no `+54 9 11 6677 8899`).
- Si el usuario tiene WhatsApp **y** WhatsApp Business instalados, el Intent dispara un chooser ("Abrir con") — rompe la demo automatizada.
- Si el contacto está guardado pero el número no tiene código de país, `wa.me` falla aun cuando el contacto exista en WhatsApp.
- Nombre "Mi nieto" en el comando — el LLM tiene que resolver del contact resolver de Android al número de teléfono. Sin permission `READ_CONTACTS` esto no funciona.
- En WhatsApp Web Mod / variantes pirateadas (presentes en algunos teléfonos de prueba) los Intents tienen schemes ligeramente distintos.

**Cómo detectarlo temprano:**
- Test el flow completo con cada contacto seedeado del guion (todos los "demo personas") y validá que el Intent llegue con texto pre-llenado.
- Logear el Intent URI exacto que se dispara: `Log.d("Beto-Intent", intent.dataString)`. Comparar con el formato canónico.
- Detectar `getPackageManager().queryIntentActivities(intent, 0).size > 1` → hay chooser, hay que setear `setPackage("com.whatsapp")`.

**Prevención:**
- **Hardcodear los contactos del guion** en una tabla local (`name → e164_phone`) — no depender del Contact resolver durante la demo. Tabla curada por mano.
- Normalizar números a E.164 sin `+` antes de construir la URL: `phone.replace(Regex("[^0-9]"), "")`.
- Forzar paquete específico: `intent.setPackage("com.whatsapp")` — bypassa el chooser. Si no está instalado, fallback a `com.whatsapp.w4b` (Business).
- **Desinstalar WhatsApp Business del teléfono de demo** si solo necesitás el WhatsApp normal — elimina el chooser de raíz.
- Prepara un fallback: si Intent falla, usar el loop agéntico (AccessibilityService) para buscar el chat por nombre y tipear el mensaje.

**Plan B en demo en vivo:**
- Si la primera vez falla: el presentador dice "voy a probar otro" y va al siguiente comando del guion. Tener **al menos 2 contactos demo** y rotar si uno falla.
- Si WhatsApp abre pero el texto no se pre-llena: el AccessibilityService de respaldo pega el texto en el campo de input (ya tenés el árbol de nodos) — el usuario solo tiene que apretar enviar, **o** Beto encuentra el botón de enviar y lo clickea.

**Fase del roadmap:**
Fase 3 (Motor de Acciones — Intents). Tabla de contactos demo curada en Fase final (Demo readiness).

---

### Pitfall 5: AccessibilityNodeInfo árbol obsoleto / referencia stale después de cambio de pantalla

**Síntoma:**
- El loop agéntico encuentra un botón, llama `performAction(ACTION_CLICK)`, retorna `true`, **pero no pasa nada visible**.
- Peor: la app navega a una pantalla nueva pero el siguiente paso del loop sigue apuntando a nodos de la pantalla vieja → toda la cadena de acciones falla en silencio.
- Crash en `IllegalStateException: Cannot perform action on AccessibilityNodeInfo that has been recycled`.

**Por qué pasa:**
- `AccessibilityNodeInfo` es un **snapshot**. Después de cualquier `WindowContentChanged` o `WindowStateChanged`, el snapshot anterior es obsoleto.
- `nodeInfo.refresh()` retorna `false` si el nodo ya no existe en el árbol → muchos devs ignoran el return value.
- Eventos `TYPE_WINDOW_CONTENT_CHANGED` se disparan en cascada (a veces 50+ por segundo en apps con animaciones tipo WhatsApp). Si el handler no debouncea, el LLM recibe contexto inconsistente.
- El LLM, basándose en una captura/árbol de hace 2 segundos, "alucina" que el botón "Enviar" está en una posición que ya cambió.

**Cómo detectarlo temprano:**
- Antes de cada `performAction`, llamar `nodeInfo.refresh()` y validar return value. Si false → reconstruir el árbol desde root.
- Logear `(eventType, packageName, className, timestamp)` de cada AccessibilityEvent y revisar que entre "LLM decide acción" y "performAction" no haya `WindowStateChanged` de por medio.
- En tests, inducir cambio rápido de pantalla (ej: tappear un chat de WhatsApp) y verificar que el agente no ejecute el plan viejo.

**Prevención:**
- **Re-snapshot del árbol antes de cada `performAction`**, no antes de cada bloque de acciones. Caro pero confiable.
- Política "una acción por turno LLM": el loop agéntico hace `(snapshot → LLM decide → 1 acción → re-snapshot → LLM decide → 1 acción)`. No batch.
- Usar `AccessibilityWindowInfo` para identificar si la ventana activa cambió de paquete (ej: estabas en Beto y saltó WhatsApp) → invalidar plan.
- Throttle de `WINDOW_CONTENT_CHANGED`: agrupar eventos de la misma ventana en ventanas de 200ms.
- **Para el guion principal NO usar el loop agéntico** — usar Intents (Pitfall 4). El loop es solo bonus / fallback para acciones fuera del top-N.

**Plan B en demo en vivo:**
- Si el loop agéntico se cuelga > 5s: timeout duro, TTS dice "perdón, no llegué a hacer eso, ¿lo intentamos de otra forma?" — recupera narrativa.
- Hard timeout en cada acción (3s) y máximo 5 acciones por comando — corta el riesgo de loop infinito.
- **Demo solo loop agéntico en un comando explícitamente etiquetado como "el camino largo"** (ej: "abrime YouTube y poneme un video del Indio") — el jurado entiende que es la parte aspiracional y perdona si demora.

**Fase del roadmap:**
Fase 4 (Loop agéntico). Hard limits de timeout/iteraciones decididos antes de Fase final.

---

### Pitfall 6: LLM tool calling devuelve JSON malformado / parámetros faltantes / tool inexistente

**Síntoma:**
- La response del LLM tiene `tool_use` pero el `input` está vacío, tiene campos extra, o tipos errados (string donde esperás number).
- El LLM "inventa" un tool que no existe (`open_whatsapp_business` cuando solo registraste `send_whatsapp_message`).
- Excepción de parseo en el cliente: `JSONException`, `kotlinx.serialization.SerializationException`.
- Worst case en demo: el LLM responde texto natural en lugar de tool call — Beto "habla" pero no actúa.

**Por qué pasa:**
- Schemas JSON ambiguos (descripciones vagas, `required` mal definido) llevan al LLM a improvisar.
- Modelos chicos / quantizados / variantes flash son más propensos a malformar.
- Tool descriptions en inglés mientras el system prompt es en español — el modelo a veces se confunde y responde en lenguaje natural.
- Temperature > 0.3 aumenta variabilidad de la salida estructurada.
- Vertex/Gemini en particular tiene `MALFORMED_FUNCTION_CALL` documentado en multi-agent.

**Cómo detectarlo temprano:**
- Validar la response contra el schema **antes** de ejecutar la acción. Pydantic/kotlinx-serialization con `ignoreUnknownKeys = false`.
- Logear cada `tool_use` completo (`name`, `input`) en debug.
- Test del happy path con cada uno de los 3-4 comandos del guion **al menos 20 veces** — si 1 falla, el modelo es inestable, hay que rebajar temperature o reescribir el schema.

**Prevención:**
- **Esquemas estrictos y minimalistas:** un solo tool por comando del guion (`send_whatsapp_message`, `make_phone_call`, `open_maps`). Cada parámetro con descripción de 1 línea + ejemplo. `required` explícito en cada uno.
- `temperature: 0` para tool calling. Sin creatividad.
- **System prompt en español** alineado con el tono de Beto, pero **descriptions de tools también en español** para evitar confusión bilingüe.
- Retry-on-malformed: si el JSON falla parsing, reenviar al LLM el error + el JSON malformado y pedir corrección. Máximo 1 retry para no romper latencia de demo.
- **Validación post-LLM:** si el LLM no devuelve `tool_use` pero el comando es uno del top-N, **caer al matcher determinista** (regex/keyword sobre el comando original) y disparar el Intent directamente. El LLM se "saltea" — la demo no se rompe.
- Allow-list de tool names — si el LLM inventa un tool fuera de la lista, error fatal y fallback al matcher.

**Plan B en demo en vivo:**
- El matcher determinista (regex sobre el texto del STT) tiene precedencia sobre el LLM para los comandos del guion. Si el guion dice "mandale a X", el regex captura `(mandale|escribile|avisale)\s+a\s+(\w+)` y dispara el flow sin pasar por el LLM. **El LLM solo se usa para extraer el cuerpo del mensaje** (parte que sí necesita lenguaje natural).
- Si todo falla: TTS dice "no te entendí, ¿podés repetirlo más despacio?" — humaniza el error, da tiempo al presentador para reintentar.

**Fase del roadmap:**
Fase 3 (Motor de Acciones — Intents) + Fase 4 (LLM integration). Allow-list y matcher determinista locked antes de Fase final.

---

### Pitfall 7: Loop agéntico se cuelga / loop infinito / pierde contexto

**Síntoma:**
- El agente repite la misma acción tres veces (clickea el mismo botón porque no detecta que ya ejecutó).
- El agente "se olvida" del objetivo original ("mandar mensaje") y queda navegando WhatsApp sin propósito.
- Costo runaway: 30 turnos LLM en un solo comando, $$$$ y latencia.
- En demo: 20 segundos de "loading" sin que pase nada visible — el jurado se aburre.

**Por qué pasa:**
- Sin memoria explícita del **plan inicial** y del **state actual**, el LLM solo ve el último snapshot y se desorienta.
- Sin detección de "loop": si el LLM emite la misma `tool_use` con los mismos params dos veces seguidas, hay loop.
- Sin presupuesto de turnos: el loop corre hasta que el LLM diga "done" — que a veces nunca dice.
- Context window se llena con árboles de nodos largos → al turn 8 el modelo ya no ve la instrucción original.

**Cómo detectarlo temprano:**
- Métrica simple: turnos por comando. Si la mediana > 4 en testing, el loop está mal diseñado.
- Hash de las últimas 3 acciones (`tool_name + params`). Si dos consecutivas son iguales → loop, abort.
- Token counter por comando. Si > 8K tokens en un solo comando, algo anda mal.

**Prevención:**
- **Hard limits sagrados:** max 5 turnos por comando, max 15s wallclock, max 4K input tokens por turn. Excedido = abort + TTS "no llegué a completarlo, ¿probamos otra cosa?".
- Estructura del prompt: `[GOAL inmutable] + [PLAN de pasos del turn 1] + [HISTORIAL compacto: solo últimas 3 acciones + resultado] + [SNAPSHOT actual filtrado]`. **No incluir el árbol completo cada turn.**
- Filtrar el AccessibilityNodeInfo tree: solo nodos clickables / con texto visible / con resource-id. Limita 50 nodos máximo. El resto, descartado.
- Detector de progreso: si después de 2 turnos el `windowState.packageName` no cambió y no hubo `tool_use` distinto, asumir stuck → abort.
- **Solo usar el loop para 1 comando "wow" del guion**, no para los 4 del happy path. Los happy path van por Intents (Pitfall 4).

**Plan B en demo en vivo:**
- Timeout duro a 15s con TTS empático: "uy, esto es nuevo para mí, mejor lo hacemos por el camino corto" + ejecuta el Intent equivalente si existe.
- Si el comando "loop-only" falla, presentador continúa con el siguiente comando del guion. Aceptamos que el camino largo sea frágil — está etiquetado en el guion como "ambición".

**Fase del roadmap:**
Fase 4 (Loop agéntico). Hard limits y detector de stuck son criterio de "done" de la fase.

---

### Pitfall 8: Foreground service muerto por OEM / Doze / battery optimization durante la demo

**Síntoma:**
- La burbuja flotante desaparece de la pantalla.
- Después de 30-60 min sin tocar el teléfono, al volver, Beto no responde.
- Logcat: el proceso de Beto fue killed por `ActivityManager` con razón `"cached"` o `"empty"`.
- En Xiaomi/MIUI, Samsung One UI, Oppo: comportamiento agresivo bien documentado en [dontkillmyapp.com](https://dontkillmyapp.com).

**Por qué pasa:**
- Doze mode después de pantalla apagada o teléfono inactivo.
- "Sleeping apps" de Samsung — aplica si la app no tuvo foreground activity por días, pero también con app standby.
- MIUI tiene autostart restrictions extra que se resetean tras OTA o force-stop.
- Foreground service sin `foregroundServiceType` correcto en Android 14+ → el sistema mata el servicio al iniciar.

**Cómo detectarlo temprano:**
- Heartbeat del FGS visible en notificación persistente con timestamp. Si la notificación desaparece, el FGS murió.
- Test: bloquear el teléfono por 10 min, despertar, intentar comando. Repetir con 30, 60 min.

**Prevención:**
- Foreground service con `foregroundServiceType="microphone|specialUse"` (Android 14+) en el manifest **y** en `startForeground(id, notification, type)`.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` solicitado en setup; verificado en pre-flight check.
- En el teléfono de demo, manualmente: Settings → Apps → Beto → Battery → **Unrestricted**. Settings → Apps → Beto → "Pause app activity if unused" → **OFF**.
- **Mantener la pantalla del teléfono encendida durante toda la demo** — en Settings → Display → Screen timeout → "Never" para el sample. Cero Doze posible.
- No usar el teléfono para nada más entre setup y demo. Pantalla encendida, app abierta, modo "no molestar" para que nada empuje a Beto al background.

**Plan B en demo en vivo:**
- Si la burbuja desapareció: el dev abre la app desde launcher (atajo en homescreen muy visible). Re-spawn del FGS y de la burbuja en <3s.
- Hot-spare phone (Pitfall 1).

**Fase del roadmap:**
Fase 1 (Setup base). Battery exemption como ítem en checklist físico de demo.

---

### Pitfall 9: Red de hackathon caída / lenta — LLM cloud no responde a tiempo

**Síntoma:**
- Comando ejecutado por usuario, STT lo captura, app envía request al LLM, **silencio 8-15 segundos**, después timeout o respuesta tardía cuando el momento ya pasó.
- Wi-Fi del venue saturado por 200 personas.
- 4G del teléfono de demo con señal débil dentro del salón.

**Por qué pasa:**
- Realidad de hackathon: la red es no negociable y siempre falla en el peor momento (es un meme, pero es verdad).
- Algunos LLM endpoints tienen latencia variable (Gemini Flash 1-3s; Claude Sonnet 2-6s para responses largos).
- Rate limits de Anthropic/OpenAI/Google free tier: 5 req/min. Si el dev testea durante el pitch, te quedaste sin cuota.

**Cómo detectarlo temprano:**
- Medir latencia p50/p99 en el venue **antes** de la demo, idealmente desde el día anterior.
- Tener cuotas con margen: pre-cargar créditos pagos en al menos 2 providers, no depender de free tier el día D.

**Prevención:**
- **Hotspot personal del celular del dev como red dedicada** para el teléfono de demo. No usar Wi-Fi del venue. Tarifa 4G/5G mensual ya pagada.
- Plan C: **respuestas pre-grabadas** para los 4 comandos del guion. Si el LLM tarda > 4s, ejecutar la acción determinista (Intent) **sin** LLM y usar respuesta TTS hardcoded ("listo, le mandé el mensaje a tu nieto"). El jurado no ve la diferencia.
- Cache de respuestas LLM idénticas — si el guion dice "avisale a mi nieto que ya llegué" y testeás 30 veces, cachealo localmente (no es trampa, es ingeniería de demo).
- Streaming en lugar de full response: feedback al usuario ("estoy escribiendo...") en cuanto el primer token llega.
- Provider con menor latencia conocida para tool calling: Gemini Flash > GPT-4o mini > Claude Haiku. Verificar en research del STACK.

**Plan B en demo en vivo:**
- Modo offline: matcher determinista del Pitfall 6 + respuestas TTS hardcoded por comando. **El demo principal funciona sin LLM.** El LLM agrega "magia" (reformulación natural del mensaje, parsing de "avisale a mi nieto" → contacto + mensaje) pero la espina dorsal son Intents + matcher.
- Si el LLM no responde en 4s: timeout, fallback a matcher, TTS hardcoded. El demo continúa sin pausa visible.

**Fase del roadmap:**
Fase 4 (LLM integration). Plan C de offline locked en Fase final (Demo readiness).

---

### Pitfall 10: SYSTEM_ALERT_WINDOW — burbuja flotante no aparece encima de WhatsApp / apps con FLAG_SECURE

**Síntoma:**
- La burbuja desaparece cuando se abre WhatsApp o cualquier app que use `FLAG_SECURE` (apps bancarias, contraseñas).
- En Android 12+ algunos OEMs ocultan overlays sobre apps en foreground por seguridad.
- En Android 14: `HIDE_OVERLAY_WINDOWS` puede ser usado por apps para esconder la burbuja explícitamente.

**Por qué pasa:**
- `TYPE_APPLICATION_OVERLAY` (el único disponible desde API 26) tiene restricciones que dependen del OEM.
- Apps con `FLAG_SECURE` (bancarias, password managers) pueden esconder overlays como protección antiphishing.
- En Android 14+, el sistema fuerza ocultar overlays sobre activities sensibles (PIN, biometría).

**Cómo detectarlo temprano:**
- Test la burbuja en cada app del guion. Si en alguna desaparece → ajustar guion o detectar en `WindowStateChanged` y mostrar/ocultar manualmente.
- `Settings.canDrawOverlays(context)` debe retornar true; si false, abort + alerta.

**Prevención:**
- Usar `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`, **no** los tipos deprecados.
- Setear flags: `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_NOT_TOUCH_MODAL`.
- **Excluir apps bancarias del scope del demo.** El guion solo navega WhatsApp, Maps, Phone, SMS — apps que no tienen FLAG_SECURE.
- Si la burbuja desaparece en X app, escuchar `WindowStateChanged` y re-attach la burbuja. Alternativa: usar la barra de notificaciones expandida (notificación persistente con quick action) como entry point secundario.

**Plan B en demo en vivo:**
- Si la burbuja desaparece: el atajo en homescreen es el plan B — el dev tappea el icono y abre la activity principal de Beto, que también dispara el flujo.
- Pre-grabar video corto (10s) de la burbuja funcionando en cada app del guion como "respaldo absoluto" — si todo falla, mostrar el video en el pitch.

**Fase del roadmap:**
Fase 1 (Setup base + overlay). Verificación per-app en Fase final.

---

### Pitfall 11: Apps bancarias bloquean lectura del AccessibilityService

**Síntoma:**
- El loop agéntico funciona perfecto en WhatsApp, Maps, Settings... pero al abrir una app bancaria (Mercado Pago, Banco X) el árbol de nodos viene **vacío** o solo con `View` genéricos sin texto.
- Algunas apps bancarias detectan "AccessibilityService activo" y bloquean la app entera, mostrando "Por seguridad, no podés usar esta app con servicios de accesibilidad activos".

**Por qué pasa:**
- Las apps bancarias usan `View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`, `FLAG_SECURE`, o detectan AccessibilityServices no whitelisted (vía `AccessibilityManager.getEnabledAccessibilityServiceList()`) y se autorefuse.
- Es comportamiento intencional — protección antifraude.

**Cómo detectarlo temprano:**
- N/A — no es relevante para el guion. Solo importa si alguien pretende incluir bancos en la demo.

**Prevención:**
- **NO incluir apps bancarias / pagos en el guion del demo.** El scope del MVP ya las excluye.
- Si alguien pregunta en el pitch "¿funciona con Mercado Pago?", respuesta honesta: "Por diseño de seguridad, las apps de banca bloquean accesibilidad. Es una decisión de la app, no nuestra. Para el caso de uso de adultos mayores, lo abordaríamos con integraciones específicas, no con AccessibilityService."

**Plan B en demo en vivo:**
- N/A. No tocar bancos.

**Fase del roadmap:**
N/A. Decisión de scope ya tomada en PROJECT.md.

---

### Pitfall 12: Permisos resetean tras `adb install -r` o cambio de signing key

**Síntoma:**
- Después de `gradle installDebug` o `adb install -r app.apk`, todo el setup (Accessibility, Overlay, Battery) se mantiene... excepto cuando se mantiene **a veces no**.
- Si cambia el signing key (debug → release, o cualquier rebuild de keystore), Android trata el APK como app nueva — todos los permisos resetean.
- Restricted settings (Android 13+) resetea cada vez que la app es reinstalada desde fuera de Play Store.

**Por qué pasa:**
- Android 13+ "Restricted settings": las apps sideloaded no pueden tener Accessibility / Notification Listener habilitado por default — se requiere acción manual cada vez.
- Cambio de signing → app nueva desde el punto de vista del package manager.

**Cómo detectarlo temprano:**
- Pre-flight check al lanzar la app: verificar Accessibility ON, Overlay permitido, Battery unrestricted. Si alguno falla, mostrar overlay rojo bloqueante con instrucciones de activación.

**Prevención:**
- **Freeze del APK** ≥ 4hs antes de la demo. Última build instalada y verificada con tests del guion.
- **Solo usar debug builds firmados con la misma keystore** durante todo el sprint. No tocar signing config.
- Documentar en checklist: tras cualquier reinstall, re-activar manualmente: (1) Accessibility, (2) Display over apps, (3) Allow restricted settings (Android 13+), (4) Battery unrestricted, (5) Microphone permission, (6) Contacts permission.

**Plan B en demo en vivo:**
- Atajo a Settings desde la burbuja flotante para re-activar rápido. El dev sabe exactamente dónde tappear (3 lugares).

**Fase del roadmap:**
Fase final (Demo readiness — checklist físico).

---

## Technical Debt Patterns (aceptables en hackathon)

| Shortcut | Beneficio inmediato | Costo a largo plazo | Cuándo aceptable |
|----------|--------------------|--------------------|------------------|
| Hardcodear contactos demo en código | Bypass del Contact resolver y READ_CONTACTS frágiles | Producción no escala más allá de 1 usuario | **Siempre en hackathon** — quitar pre-merge a producción |
| Tabla de comandos regex matcher antes que LLM | Independencia de red + 0 latencia | Sin generalización a comandos nuevos | **Siempre como fallback** del LLM |
| Cache de respuestas LLM por hash del comando exacto | Demo determinístico + latencia 0 | No es agente "real", es script | Solo durante el demo en vivo, etiquetar en código |
| `temperature: 0` siempre | Estabilidad máxima | Modelo robótico, no creativo | Para tool calling **siempre**. Para Modo Compañero conversacional, 0.3-0.5 |
| Sin tests automatizados | Velocidad de desarrollo | Regresiones invisibles | Siempre — testing manual ensayado del guion es el "test suite" |
| Sin tracking de errores / Sentry | Setup más simple | Perdés bugs en producción | Para hackathon sí. Para el día siguiente del demo, no |
| Logs verbose en prod build | Debug fácil en demo | Performance + privacy | Aceptable solo en debug build del demo |

---

## Integration Gotchas

| Integration | Error común | Forma correcta |
|-------------|-------------|----------------|
| WhatsApp Intent | URL con `+` o espacios en el número | E.164 sin `+` y sin separadores: `5491166778899` |
| WhatsApp Intent | No setear `setPackage` → chooser aparece | `intent.setPackage("com.whatsapp")` |
| Maps Intent | Usar `geo:` con dirección sin URL-encode | `Uri.parse("geo:0,0?q=" + Uri.encode(address))` |
| Phone call Intent | Usar `ACTION_CALL` sin permiso `CALL_PHONE` | Usar `ACTION_DIAL` (no requiere permiso, abre dialer pre-llenado) |
| SpeechRecognizer | Crear instancia desde background thread | `Handler(Looper.getMainLooper()).post { ... }` |
| TextToSpeech | Llamar `speak` antes de `onInit` | Encolar y flush en `onInit` |
| AccessibilityService | Usar nodo después de `WindowContentChanged` | `nodeInfo.refresh()` antes de cada `performAction`; recyclear si false |
| AccessibilityService | `findAccessibilityNodeInfosByText` con texto traducido | Usar resource-ids cuando sea posible (más estables que texto) |
| LLM tool calling | Schema con descriptions vagas | Description estricta + ejemplo + `required` explícito |
| LLM tool calling | Mezclar idioma del system prompt y de las tool descriptions | Todo en español (o todo en inglés) consistente |
| MediaProjection | Pedir consent en cada captura | Solicitar 1 vez, mantener `MediaProjection` viva mientras la sesión esté activa |
| Foreground service | `startForeground` sin `foregroundServiceType` en Android 14+ | Type `microphone` o `specialUse` declarado en manifest **y** runtime |

---

## Performance Traps

| Trap | Síntoma | Prevención | Cuándo rompe |
|------|---------|------------|--------------|
| Procesar todos los `TYPE_VIEW_*` events del AccessibilityService | UI lag, ANR, batería caliente | Filter `eventTypes` en config XML — solo `WINDOW_STATE_CHANGED \| WINDOW_CONTENT_CHANGED \| VIEW_CLICKED` | En cualquier app con animaciones (WhatsApp, social media) |
| Enviar el árbol de nodos completo al LLM | Latencia + costo tokens explosivos | Filtrar a clickables/visible/textuales; limit 50 nodos | Apps con listas largas (chats, feeds) |
| `findAccessibilityNodeInfosByText` recursivo en cada evento | App freezea | Cache + debounce 200ms entre búsquedas | A partir de 5 eventos/seg sostenidos |
| LLM calls síncronas en main thread | ANR | Coroutines + Dispatchers.IO | Inmediato en main thread |
| Crear nuevo `OkHttpClient` por request | Connection storm | Singleton client | A partir de 10 calls |
| TTS speak sin queue mode `QUEUE_FLUSH` | Voces se montan unas sobre otras | `tts.speak(text, QUEUE_FLUSH, ...)` para barge-in | Comandos rápidos consecutivos |

---

## UX Pitfalls (en contexto de adultos mayores + demo en vivo)

| Pitfall | Impacto en usuario / jurado | Mejor approach |
|---------|------------------------------|----------------|
| TTS verboso ("Voy a proceder a enviar el mensaje al contacto identificado como...") | El abuelo se pierde, el jurado se aburre | TTS de 1 frase máximo: "Listo, ya le avisé." |
| Sin feedback durante latencia LLM | El usuario habla otra vez encima → STT cancela el flow | TTS inmediato "ya escuché, dame un momento" + animación visible en burbuja |
| Errores técnicos al usuario ("Error 500: invalid_request") | Quiebre absoluto de la narrativa empática | Errores siempre traducidos a tono cálido: "uy, algo no salió, ¿lo intentamos de nuevo?" |
| Burbuja en posición que tapa el contenido relevante | Usuario no puede ver lo que Beto está haciendo | Burbuja con drag + magnet a borde + auto-min cuando está escuchando |
| Confirmación "¿Estás seguro?" antes de cada acción | Anula la promesa "Beto opera el teléfono por vos" | Sin confirmación. Hablar mientras se hace. Si se equivoca, "lo arreglo": deshacer |
| Comando reconocido pero ambiguo ("mandale a Juan" cuando hay 3 Juan) | Loop infinito o llamada a la persona equivocada | Pedir desambiguación cálida: "tenés varios Juan, ¿le hablo al Juan que es tu hijo?" |
| Pantalla apagada durante el demo | Beto "muere" en background | Screen timeout = Never durante el demo |

---

## "Looks Done But Isn't" Checklist

- [ ] **AccessibilityService:** ¿`onAccessibilityEvent` se ejecuta también en cold start? Verificar reiniciando el teléfono y testeando primer comando sin abrir la app.
- [ ] **STT:** ¿Funciona desde la burbuja flotante (no solo desde una Activity de prueba)? Test cierra app, presiona burbuja, comando debe funcionar primer intento.
- [ ] **TTS:** ¿Voz es-AR está descargada en el teléfono físico de demo? Settings → Accessibility → TTS → Install voice data.
- [ ] **Burbuja flotante:** ¿Aparece encima de cada app del guion? Test en cada app individualmente.
- [ ] **WhatsApp Intent:** ¿Pre-llena el texto, no solo abre el chat? Test cada contacto del guion. Verificar que aparece "Mensaje" en el input.
- [ ] **Foreground service:** ¿Sobrevive 30 min con pantalla bloqueada? Test físico con timer.
- [ ] **LLM tool calling:** ¿Devuelve JSON válido en 20/20 ejecuciones del guion? Si 1 falla, retry o reescribir schema.
- [ ] **Matcher determinista (offline mode):** ¿Funciona con la red apagada? Test poniendo el teléfono en avión.
- [ ] **Battery exemption:** ¿Settings → Apps → Beto → Battery dice "Unrestricted"? Verificar visualmente en el teléfono de demo.
- [ ] **Restricted settings (Android 13+):** ¿"Allow restricted settings" está ON para Beto? Si no, accessibility no se puede activar.
- [ ] **Pre-flight check:** ¿La app detecta y avisa cuando Accessibility se desactivó? Test apagando el toggle manualmente, abriendo la app y verificando que muestra alerta.
- [ ] **Hot-spare:** ¿Hay un segundo teléfono idénticamente configurado y testado con el mismo guion?
- [ ] **Demo offline (red caída):** ¿Los 4 comandos del guion funcionan con avión activado? El LLM caerá, pero el matcher + TTS hardcoded debe sostenerlo.
- [ ] **Logs:** ¿Tag `Beto-*` consistente y filtrable? Test `adb logcat | grep Beto-` muestra todo lo relevante.
- [ ] **Volumen del teléfono:** ¿Al máximo? El sample tiene ruido ambiente, TTS bajo no se escucha.

---

## Recovery Strategies (cuando ya pasó en vivo)

| Pitfall | Costo de recuperación | Pasos |
|---------|----------------------|-------|
| Accessibility desactivado | LOW si ensayado | Atajo a Settings → Accessibility → toggle ON. <8s |
| STT no responde | LOW | Botón secundario "tipear" en burbuja → input de texto. Mantiene narrativa |
| TTS no habla | LOW | Acción visible igual ocurre. Snackbar con texto como respaldo |
| WhatsApp Intent falla | LOW | Saltar al siguiente comando del guion. Tener 2-3 contactos de respaldo |
| Loop agéntico colgado | MEDIUM | Hard timeout 15s + TTS empático "lo intentamos por otro camino" + ejecutar Intent equivalente |
| Foreground service muerto | LOW si hay atajo | Tappear icono en homescreen, reabre app y FGS |
| Red caída / LLM timeout | LOW si offline mode listo | Matcher determinista + TTS hardcoded — invisible al jurado |
| Burbuja no aparece sobre app X | LOW si video de respaldo | Pre-grabado de demo + atajo en homescreen como entry point |
| Teléfono crasheó / hard freeze | HIGH | Hot-spare phone — switch en <30s, presentador sostiene narrativa |
| Restricted settings activado por OTA inesperado | MEDIUM | Settings → Apps → Beto → menú 3 puntos → Allow restricted settings |

---

## Pitfall-to-Phase Mapping

| Pitfall | Fase de prevención | Verificación |
|---------|-------------------|--------------|
| 1. Accessibility deshabilitado | Fase 1 (Setup) + Fase final (checklist) | Pre-flight check + heartbeat + checklist físico |
| 2. SpeechRecognizer en service | Fase 2 (Voz) | Test con app cerrada → primer comando funciona |
| 3. TTS race condition | Fase 2 (Voz) | TTS de boot al abrir app — si habla, no hay race |
| 4. WhatsApp Intent fallido | Fase 3 (Motor — Intents) | Test cada contacto del guion 5 veces |
| 5. NodeInfo árbol stale | Fase 4 (Loop agéntico) | Detector de WindowStateChanged + refresh() check |
| 6. LLM JSON malformado | Fase 4 (LLM integration) | 20/20 ejecuciones del guion sin parse error |
| 7. Loop infinito | Fase 4 (Loop agéntico) | Hard limits + detector de stuck — tests con commands ambiguos |
| 8. FGS killed por OEM | Fase 1 (Setup base) | Test 30 min screen lock → primer comando funciona |
| 9. Red de hackathon | Fase final (Demo readiness) | Test modo avión + matcher determinista funciona |
| 10. Overlay no aparece | Fase 1 (Setup) | Test burbuja en cada app del guion |
| 11. Apps bancarias | Decisión de scope (PROJECT.md) | N/A — fuera del guion |
| 12. Permisos reseteados por reinstall | Fase final (Demo readiness) | Freeze APK + checklist post-install |

---

## Prioridad para Roadmap

**Fase 1 — Setup base (NO negociable):**
- Pitfall 1, 8, 10, 12 — todo lo de permisos, FGS, overlay debe estar resuelto antes de cualquier otra cosa.

**Fase 2 — Voz:**
- Pitfall 2, 3 — STT y TTS robustos.

**Fase 3 — Motor de Acciones (camino confiable):**
- Pitfall 4 — Intents bien construidos, contactos hardcoded, fallback de paquete.

**Fase 4 — LLM + Loop agéntico (camino ambicioso):**
- Pitfall 5, 6, 7 — todo lo del LLM con hard limits, schemas estrictos, retries.

**Fase final — Demo readiness:**
- Pitfall 9, 12 — Plan C offline, freeze APK, hot-spare, checklist físico, ensayo completo del guion 5+ veces.

---

## Sources

- [AccessibilityService — Android Developers](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) — official API reference
- [AccessibilityNodeInfo — Android Developers](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo) — `refresh()` semantics y stale node handling
- [SpeechRecognizer — Android Developers](https://developer.android.com/reference/android/speech/SpeechRecognizer) — main thread requirement, error codes
- [TextToSpeech.OnInitListener — Android Developers](https://developer.android.com/reference/android/speech/tts/TextToSpeech.OnInitListener) — async init pattern
- [Behavior changes: Apps targeting Android 15 or higher](https://developer.android.com/about/versions/15/behavior-changes-15) — foreground service types, restricted settings
- [Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby) — Doze mode reference
- [How Dare You Kill My Foreground Service — Hones Dev](https://honesdev.com/how-dare-you-kill-my-foreground-service/) — OEM killing patterns
- [What Android OEMs do to background apps — DEV Community](https://dev.to/stoyan_minchev/what-android-oems-do-to-background-apps-and-the-11-layers-i-built-to-survive-it-28bb) — Xiaomi/Samsung specifics
- [dontkillmyapp.com](https://dontkillmyapp.com) — comprehensive OEM behavior catalog
- [Function Calling & Tool Use Complete Guide 2026 — ofox.ai](https://ofox.ai/blog/function-calling-tool-use-complete-guide-2026/) — tool calling pitfalls per provider
- [Malformed Function Call Errors in Multi-Agentic Systems — Medium](https://medium.com/@mukrimenurgumus/malformed-function-call-errors-in-multi-agentic-systems-d7462a33b91b) — Gemini MALFORMED_FUNCTION_CALL patterns
- [The guide to structured outputs and function calling with LLMs — Agenta](https://agenta.ai/blog/the-guide-to-structured-outputs-and-function-calling-with-llms) — schema strictness best practices
- [WhatsApp Click-to-Chat — wa.me documentation](https://faq.whatsapp.com/5913398998672934) — URL format requirements

---

*Pitfalls research for: Agente Android multimodal en hackathon*
*Researched: 2026-05-09*
