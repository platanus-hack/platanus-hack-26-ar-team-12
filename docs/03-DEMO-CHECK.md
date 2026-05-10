# Phase 3 Demo Check

Run these on the demo phone after installing the debug APK.

- [ ] Plan C guaranteed: airplane mode ON, say `mandale a mi nieto que ya llegue`; WhatsApp opens with the message prepared.
- [ ] LLM known contact: say `Llamá a Pedro`; Pedro resolves from contacts/memory and the phone intent opens.
- [ ] Unknown alias learning: say `Llamá a mi médica`; Beto asks `¿Quién es tu médica?`, resolves a real contact, saves it, and does not ask the second time.
- [ ] Messaging channel learning: say `Mandale a Juan que pase por casa`; Beto asks `¿Por WhatsApp, SMS o llamada?`, saves the answer, and does not ask the second time.
- [ ] Homonym handling: seed two Carlos contacts; Beto asks `¿Cuál?` instead of assuming.
- [ ] Network down with script command: airplane mode ON, Plan C still works.
- [ ] Network down with complex command: Beto fails warmly with `No te entendí del todo, repetímelo más despacito.` and does not crash.

Expected log markers:

- `DISPATCH_START`
- `DISPATCH_PLANC_HIT`
- `DISPATCH_LLM_DECISION tool=...`
- `DISPATCH_CLARIFY_CONTACT`
- `DISPATCH_CLARIFY_CHANNEL`
- `DISPATCH_EXECUTED`
- `DISPATCH_FAILED reason=...`
