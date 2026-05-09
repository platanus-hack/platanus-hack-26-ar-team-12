---
status: complete
phase: 02-vertical-slice-minimo-plan-c-offline
source:
  - 02-01-SUMMARY.md
  - 02-02-SUMMARY.md
  - 02-03-SUMMARY.md
started: 2026-05-09T16:27:43Z
updated: 2026-05-09T16:45:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Tap burbuja → captura de voz
expected: Al tocar la burbuja flotante, se abre la captura de voz (VoiceCaptureActivity transparente). El micrófono empieza a escuchar en es-AR sin pedir confirmación adicional.
result: pass

### 2. Happy path WhatsApp
expected: Decir `mandale a mi nieto que ya llegue`. Se abre WhatsApp regular (com.whatsapp) en el chat de "Mi nieto" con el texto `ya llegue` precargado en el campo de mensaje. Beto dice por TTS `Listo, te dejé el mensaje preparado.` NO se envía automáticamente — el usuario tiene que tocar enviar manualmente.
result: pass

### 3. Variante de conector "para decirle"
expected: Decir `avisale a mi nieto para decirle que ya llegue` (o `con el mensaje`, `diciendo`, `de que`). El matcher reconoce el conector y se abre WhatsApp con `ya llegue` prefilled igual que el happy path.
result: issue
reported: "Funciona pero agrega el texto 'decile' al mensaje prefilled, lo cual no debería pasar"
severity: major

### 4. Latencia STT
expected: En logcat aparece `PLAN_C_STT_RESULT elapsedMs=<algo>` con valor menor a 3000 ms desde fin del habla hasta resultado. Filtrar con `adb logcat -s "Beto-Plan-C:D"` o equivalente.
result: pass

### 5. Reintento en habla vacía
expected: Tocar burbuja y quedarse en silencio (o decir algo no reconocible). Beto pide reintentar una vez con frase cálida bloqueada. Si el segundo intento también falla, Beto dice la frase final de fallo de STT y termina el flujo sin crashear.
result: pass

### 6. Clarificación por mensaje faltante
expected: Decir `mandale a mi nieto` (sin mensaje). Beto pregunta `¿Qué querés que le diga?` (una sola vez). Al responder con el mensaje, se abre WhatsApp con ese texto prefilled.
result: issue
reported: "La clarificación funciona, pero al responder con 'decile que [mensaje]' también pega 'decile que' en WhatsApp — mismo bug que test 3"
severity: major

### 7. Clarificación por contacto faltante
expected: Decir `mandale que ya llegue` (sin contacto). Beto pide el contacto con frase bloqueada. Si se responde `mi nieto` o `nieto`, sigue el flujo y abre WhatsApp.
result: pass

### 8. Frase fuera de Plan C
expected: Decir algo que no matchee `mandale/avisale/decile/dile/escribile` (ej. `che, qué hora es`). Beto dice la frase final locked de matcher failure y termina sin abrir WhatsApp ni crashear.
result: pass

### 9. WhatsApp no instalado / launch failure
expected: Forzar fallo de launch (desinstalar com.whatsapp o disable). Decir `mandale a mi nieto que ya llegue`. Beto dice `No pude abrir WhatsApp. Probemos de nuevo en un ratito, dale.` y resetea el flujo de Plan C sin crashear. En logcat aparece `PLAN_C_WHATSAPP_FAILED`.
result: skipped

### 10. Logs de demo presentes
expected: Durante un happy path completo, `adb logcat` muestra en orden: `PLAN_C_STT_START`, `PLAN_C_STT_RESULT elapsedMs=...`, `PLAN_C_MATCHED contact=Mi nieto`, `PLAN_C_WHATSAPP_LAUNCHED`. Sin `PLAN_C_WHATSAPP_FAILED` en el camino feliz.
result: pass

### 11. Sin auto-send
expected: En todos los caminos exitosos, WhatsApp queda con el mensaje precargado pero NUNCA enviado automáticamente. El usuario debe tocar el botón enviar manualmente. No hay click automation vía Accessibility en este Phase.
result: pass

## Summary

total: 11
passed: 8
issues: 2
pending: 0
skipped: 1
skipped: 0

## Gaps

- truth: "El mensaje prefilled en WhatsApp debe contener solo el contenido del mensaje, sin incluir palabras del conector (ej. 'decile')"
  status: failed
  reason: "User reported: Funciona pero agrega el texto 'decile' al mensaje prefilled, lo cual no debería pasar"
  severity: major
  test: 3
  artifacts: []
  missing: []

- truth: "Al responder la clarificación de mensaje con 'decile que [texto]', WhatsApp debe prefillarse solo con '[texto]', sin incluir 'decile que'"
  status: failed
  reason: "User reported: mismo bug que test 3 — el conector 'decile que' también se filtra al prefill en el flujo de clarificación"
  severity: major
  test: 6
  artifacts: []
  missing: []
