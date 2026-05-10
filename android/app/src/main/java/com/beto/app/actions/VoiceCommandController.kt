package com.beto.app.actions

import com.beto.app.bus.AgentCommand
import com.beto.app.llm.LLMRouter
import com.beto.app.llm.OfflineIntentClassifier
import com.beto.app.util.LogTags
import com.beto.app.voice.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Orquestador principal de comandos de voz naturales.
 * Maneja el flujo: Captura -> Clasificación -> Confirmación -> Ejecución.
 */
class VoiceCommandController(
    private val scope: CoroutineScope,
    private val sendCommand: suspend (AgentCommand) -> Unit,
    private val classifier: OfflineIntentClassifier,
    private val llmRouter: LLMRouter,
    private val contactsResolver: ContactsResolver,
    private val actionExecutor: IntentActionExecutor
) {
    private enum class State { IDLE, AWAITING_CONFIRMATION }

    private var currentState = State.IDLE
    private var pendingIntent: OfflineIntentClassifier.ClassifiedIntent? = null

    fun onVoiceCaptured(text: String) {
        val input = text.trim().lowercase()
        Timber.tag(LogTags.ACTION).i("Procesando voz en estado %s: %s", currentState, input)

        when (currentState) {
            State.IDLE -> processInitialCommand(text)
            State.AWAITING_CONFIRMATION -> processConfirmation(input)
        }
    }

    private fun processInitialCommand(text: String) {
        scope.launch {
            // 1. Clasificación Offline (Filtro rápido)
            val classified = classifier.classify(text) ?: llmRouter.route(text)

            if (classified == null) {
                TtsManager.speak("Perdón, no entendí qué querés hacer. ¿Podés repetirlo?")
                return@launch
            }

            // 2. Resolver contacto si aplica
            val contactName = classified.args["contact"]
            if (!contactName.isNullOrBlank()) {
                val contact = contactsResolver.resolveContact(contactName)
                if (contact == null) {
                    TtsManager.speak("No encontré a $contactName en tus contactos.")
                    return@launch
                }
            }

            // 3. Solicitar confirmación
            pendingIntent = classified
            currentState = State.AWAITING_CONFIRMATION
            
            val confirmationPhrase = buildConfirmationPhrase(classified)
            TtsManager.speak("$confirmationPhrase ¿Confirmás?")
            
            // Disparar captura de voz para el "Sí/No"
            sendCommand(AgentCommand.StartVoiceCapture())
        }
    }

    private fun processConfirmation(input: String) {
        val isAffirmative = input.contains("si") || input.contains("dale") || input.contains("bueno") || input.contains("confirmado")
        val isNegative = input.contains("no") || input.contains("pará") || input.contains("cancel")

        if (isAffirmative) {
            executePending()
        } else if (isNegative) {
            TtsManager.speak("Bueno, cancelado.")
            reset()
        } else {
            TtsManager.speak("No te entendí. ¿Querés que lo haga? Decime sí o no.")
            scope.launch { sendCommand(AgentCommand.StartVoiceCapture()) }
        }
    }

    private fun executePending() {
        val intent = pendingIntent
        if (intent != null) {
            TtsManager.speak("Dale, ahí lo hago.")
            val success = actionExecutor.execute(intent.toolName, intent.args)
            if (!success) {
                TtsManager.speak("Hubo un problema y no pude hacerlo.")
            }
        }
        reset()
    }

    private fun buildConfirmationPhrase(intent: OfflineIntentClassifier.ClassifiedIntent): String {
        val contact = intent.args["contact"] ?: ""
        return when (intent.toolName) {
            "make_call" -> "Voy a llamar a $contact."
            "send_whatsapp" -> "Le mando un WhatsApp a $contact."
            "send_sms" -> "Le mando un mensaje a $contact."
            "open_maps" -> "Abro el mapa para ir a ${intent.args["query"]}."
            else -> "Voy a hacer eso."
        }
    }

    private fun reset() {
        currentState = State.IDLE
        pendingIntent = null
    }
}
