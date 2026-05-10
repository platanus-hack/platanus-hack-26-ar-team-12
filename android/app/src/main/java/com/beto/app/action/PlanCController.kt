package com.beto.app.action

import android.content.Context
import com.beto.app.bus.AgentCommand
import com.beto.app.util.LogTags
import com.beto.app.voice.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class PlanCController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sendCommand: suspend (AgentCommand) -> Unit,
    private val sendWhatsapp: (Context, DemoContact, String) -> ActionResult = IntentBranch::sendWhatsapp,
    private val speak: (String) -> Unit = TtsManager::speak,
) {
    private var sttRetryUsed = false
    private var clarificationUsed = false
    private var pendingContact: DemoContact? = null
    private var pendingMessage: String? = null

    fun startVoiceCapture(startedAtMs: Long? = null) {
        Timber.tag(LogTags.STT).i("PLAN_C_STT_START")
        scope.launch { sendCommand(AgentCommand.StartVoiceCapture(startedAtMs)) }
    }

    fun onVoiceCaptured(text: String, elapsedMs: Long) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) {
            handleVoiceFailure(elapsedMs)
            return
        }

        val merged = mergeWithPending(normalizedText)
        when (val result = DeterministicMatcher.match(merged)) {
            is MatchResult.Matched -> handleMatched(result)
            is MatchResult.NeedsContact -> askForContact(result.message)
            is MatchResult.NeedsMessage -> askForMessage(result.contactAlias)
            MatchResult.NoMatch -> handleNoMatch()
        }
    }

    fun onVoiceCaptureFailed(reason: String, elapsedMs: Long) {
        Timber.tag(LogTags.STT).w("PLAN_C_STT_RESULT elapsedMs=%d failed=%s", elapsedMs, reason)
        handleVoiceFailure(elapsedMs)
    }

    private fun handleMatched(result: MatchResult.Matched) {
        Timber.tag(LogTags.ACTION).i("PLAN_C_MATCHED contactResolved=true")
        speak("Abro WhatsApp con el mensaje para tu nieto.")
        when (val actionResult = sendWhatsapp(context, result.contact, result.message)) {
            ActionResult.Launched -> {
                Timber.tag(LogTags.ACTION).i("PLAN_C_WHATSAPP_LAUNCHED")
                speak("Listo, te deje el mensaje preparado.")
                reset()
            }
            is ActionResult.Failed -> {
                Timber.tag(LogTags.ACTION).w(
                    "PLAN_C_WHATSAPP_FAILED reason=%s",
                    actionResult.reason,
                )
                speak("No pude abrir WhatsApp. Probemos de nuevo en un ratito, dale.")
                reset()
            }
        }
    }

    private fun askForContact(message: String?) {
        if (clarificationUsed) {
            failWarmly()
            return
        }
        pendingMessage = message
        clarificationUsed = true
        speak("A quien le aviso?")
        startVoiceCapture()
    }

    private fun askForMessage(contactAlias: String?) {
        if (clarificationUsed) {
            failWarmly()
            return
        }
        pendingContact = contactAlias?.let(DemoContacts::resolve)
        clarificationUsed = true
        speak("Que queres que le diga?")
        startVoiceCapture()
    }

    private fun handleNoMatch() {
        if (clarificationUsed) {
            failWarmly()
        } else {
            handleVoiceFailure(0L)
        }
    }

    private fun handleVoiceFailure(elapsedMs: Long) {
        if (!sttRetryUsed) {
            sttRetryUsed = true
            speak("No te escuche bien. Probemos de nuevo, dale.")
            startVoiceCapture()
        } else {
            Timber.tag(LogTags.STT).w("Final STT failure elapsedMs=%d", elapsedMs)
            failWarmly()
        }
    }

    private fun failWarmly() {
        speak("Perdon, no te entendi bien. Probemos de nuevo, dale.")
        reset()
    }

    private fun mergeWithPending(text: String): String {
        val normalized = DeterministicMatcher.normalize(text)
        val contact = DemoContacts.resolve(normalized) ?: pendingContact
        val message = pendingMessage ?: normalized
        return if (contact != null && pendingMessage != null) {
            "mandale a ${contact.aliases.first()} que $message"
        } else if (pendingContact != null) {
            "mandale a ${pendingContact?.aliases?.first()} que $message"
        } else {
            text
        }
    }

    private fun reset() {
        sttRetryUsed = false
        clarificationUsed = false
        pendingContact = null
        pendingMessage = null
    }
}
