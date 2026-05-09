# Phase 2 Demo Check

## Device Setup

- [ ] regular WhatsApp installed (`com.whatsapp`)
- [ ] WhatsApp Business not used for this phase
- [ ] demo contact "Mi nieto" seeded with E.164 number
- [ ] Android TTS Spanish voice works
- [ ] Android speech recognition Spanish/Argentina works on the demo phone

## Happy Path Script

- [ ] tap bubble
- [ ] say `mandale a mi nieto que ya llegue`
- [ ] confirm log `PLAN_C_STT_RESULT elapsedMs=<3000`
- [ ] confirm WhatsApp opens with `ya llegue` prefilled
- [ ] confirm Beto says `Listo, te deje el mensaje preparado.`

## Failure Checks

- [ ] empty speech retries once
- [ ] ambiguous missing message asks `Que queres que le diga?`
- [ ] missing WhatsApp says `No pude abrir WhatsApp. Probemos de nuevo en un ratito, dale.`

## Prohibited in Phase 2

- [ ] no LLM
- [ ] no ElevenLabs
- [ ] no auto-send
- [ ] no Android Contacts relationship learning
- [ ] no agentic fallback
