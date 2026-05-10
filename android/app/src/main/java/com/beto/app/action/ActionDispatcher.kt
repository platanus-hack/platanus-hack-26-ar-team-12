package com.beto.app.action

import android.content.Context
import com.beto.app.contacts.ContactRepository
import com.beto.app.guide.GuideAction
import com.beto.app.guide.GuideController
import com.beto.app.llm.Decision
import com.beto.app.llm.LlmClient
import com.beto.app.llm.ToolDescriptors
import com.beto.app.memory.Channel
import com.beto.app.memory.ContactRef
import com.beto.app.memory.UserMemoryStore
import com.beto.app.util.LogTags
import com.beto.app.voice.PhraseFallbacks
import com.beto.app.voice.PhraseGenerator
import com.beto.app.voice.PhraseIntent
import com.beto.app.voice.PhraseParams
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import java.util.Locale

class ActionDispatcher(
    private val context: Context,
    private val llm: LlmClient,
    private val memory: UserMemoryStore,
    private val contacts: ContactRepository,
    private val contactClarifier: ContactClarifier,
    private val channelClarifier: ChannelClarifier,
    private val speaker: Speaker,
    private val phraseGenerator: PhraseGenerator? = null,
    private val guideController: GuideController? = null,
    private val sendWhatsapp: (Context, DemoContact, String) -> ActionResult = IntentBranch::sendWhatsapp,
    private val scope: CoroutineScope? = null,
) {

    /**
     * Genera una frase via LLM si hay PhraseGenerator inyectado, sino usa el fallback hardcoded.
     * Mantenemos compatibilidad hacia atrás (tests sin PhraseGenerator siguen funcionando).
     */
    private suspend fun phrase(intent: PhraseIntent, params: PhraseParams = PhraseParams.empty()): String =
        phraseGenerator?.forIntent(intent, params) ?: PhraseFallbacks.forIntent(intent, params)
    suspend fun handle(transcript: String) {
        Timber.tag(LogTags.ACTION).d("DISPATCH_START")
        when (val routed = ActionRouter.routeTranscript(transcript)) {
            is RouteOutcome.PlanC -> {
                Timber.tag(LogTags.ACTION).d("DISPATCH_PLANC_HIT")
                executePlanC(routed.match)
            }
            is RouteOutcome.NeedsLlm -> executeLlmRoute(routed.transcript)
            else -> Unit
        }
    }

    private suspend fun executeLlmRoute(transcript: String) {
        val decision = runCatching { llm.interpret(transcript) }
            .getOrElse {
                Timber.tag(LogTags.ACTION).w(it, "DISPATCH_FAILED reason=llm_error")
                Decision.Unknown
            }
        when (val routed = ActionRouter.routeDecision(decision)) {
            is RouteOutcome.ExecuteTool -> {
                Timber.tag(LogTags.ACTION).d("DISPATCH_LLM_DECISION tool=%s", routed.call.tool)
                executeTool(routed.call, transcript)
            }
            is RouteOutcome.Clarify -> handleClarification(routed.decision, transcript)
            RouteOutcome.Unknown -> failDidNotUnderstand()
            else -> Unit
        }
    }

    private suspend fun executePlanC(match: MatchResult.Matched) {
        val contactName = match.contact.canonicalName
        speaker.speak(phrase(PhraseIntent.CONFIRM_WHATSAPP, PhraseParams.forContact(contactName)))
        when (val result = sendWhatsapp(context, match.contact, match.message)) {
            ActionResult.Launched -> {
                Timber.tag(LogTags.ACTION).d("DISPATCH_EXECUTED tool=send_whatsapp")
                speaker.speak(phrase(PhraseIntent.SUCCESS_WHATSAPP, PhraseParams.forContact(contactName)))
            }
            is ActionResult.Failed -> {
                Timber.tag(LogTags.ACTION).w("DISPATCH_FAILED reason=%s", result.reason)
                speaker.speak(phrase(PhraseIntent.FAILED_INTENT))
            }
        }
    }

    private suspend fun executeTool(call: Decision.ToolCall, transcript: String) {
        when (call.tool) {
            ToolDescriptors.OPEN_MAPS -> executeMaps(call.args["query"].orEmpty())
            ToolDescriptors.SHOW_HOW_TO -> executeShowHowTo(call.args["action"].orEmpty())
            ToolDescriptors.MAKE_CALL -> {
                val contact = resolveContactOrClarify(call.args["contact"].orEmpty()) ?: return
                executeChannel(contact, Channel.CALL, null)
            }
            ToolDescriptors.SEND_SMS, ToolDescriptors.SEND_WHATSAPP -> {
                val contact = resolveContactOrClarify(call.args["contact"].orEmpty()) ?: return
                val message = call.args["message"].orEmpty()
                if (message.isBlank()) {
                    failDidNotUnderstand()
                    return
                }
                val channel = explicitChannelFromTranscript(transcript)
                    ?: memory.preferredChannel(contact)
                    ?: channelClarifier.clarify(contact)
                    ?: return
                executeChannel(contact, channel, message)
            }
            else -> failDidNotUnderstand()
        }
    }

    private suspend fun handleClarification(decision: Decision.NeedsClarification, transcript: String) {
        Timber.tag(LogTags.ACTION).d("DISPATCH_LLM_DECISION clarification=%s", decision.expecting)
        when (decision.expecting) {
            com.beto.app.llm.ExpectedAnswer.CONTACT_NAME -> {
                val alias = extractAlias(decision.question, transcript) ?: run {
                    failDidNotUnderstand()
                    return
                }
                val contact = contactClarifier.clarify(alias) ?: return
                executeAfterContactClarification(transcript, contact)
            }
            com.beto.app.llm.ExpectedAnswer.CHANNEL -> {
                val contactArg = extractContactArg(transcript) ?: run {
                    failDidNotUnderstand()
                    return
                }
                val contact = resolveContactOrClarify(contactArg) ?: return
                val channel = channelClarifier.clarify(contact) ?: return
                executeChannel(contact, channel, extractMessage(transcript))
            }
            com.beto.app.llm.ExpectedAnswer.FREE_TEXT -> failDidNotUnderstand()
        }
    }

    private suspend fun resolveContactOrClarify(contactArg: String): ContactRef? {
        val normalized = contactArg.trim()
        if (normalized.isBlank()) {
            failDidNotUnderstand()
            return null
        }

        memory.resolveAlias(normalized)?.let { return it }
        val matches = contacts.resolve(normalized)
        return when (matches.size) {
            1 -> matches.single().toContactRef()
            0 -> contactClarifier.clarify(normalized)
            else -> contactClarifier.clarify(normalized)
        }
    }

    private suspend fun executeChannel(contact: ContactRef, channel: Channel, message: String?) {
        val params = message
            ?.let { PhraseParams.forMessage(contact.displayName, it) }
            ?: PhraseParams.forContact(contact.displayName)

        // Confirma antes de actuar
        when (channel) {
            Channel.WHATSAPP -> speaker.speak(phrase(PhraseIntent.CONFIRM_WHATSAPP, params))
            Channel.SMS -> speaker.speak(phrase(PhraseIntent.CONFIRM_SMS, params))
            Channel.CALL -> speaker.speak(phrase(PhraseIntent.CONFIRM_CALL, params))
        }

        val result = when (channel) {
            Channel.WHATSAPP -> {
                if (message.isNullOrBlank()) {
                    failDidNotUnderstand()
                    return
                }
                IntentBranch.sendWhatsapp(context, contact, message)
            }
            Channel.SMS -> IntentBranch.sendSms(context, contact.phoneE164, message.orEmpty())
            Channel.CALL -> IntentBranch.makeCall(context, contact.phoneE164)
        }
        when (result) {
            ActionResult.Launched -> {
                Timber.tag(LogTags.ACTION).d("DISPATCH_EXECUTED")
                val successIntent = when (channel) {
                    Channel.WHATSAPP -> PhraseIntent.SUCCESS_WHATSAPP
                    Channel.SMS -> PhraseIntent.SUCCESS_SMS
                    Channel.CALL -> PhraseIntent.SUCCESS_CALL
                }
                speaker.speak(phrase(successIntent, params))
            }
            is ActionResult.Failed -> {
                Timber.tag(LogTags.ACTION).w("DISPATCH_FAILED reason=%s", result.reason)
                speaker.speak(phrase(PhraseIntent.FAILED_INTENT))
            }
        }
    }

    private suspend fun executeShowHowTo(actionArg: String) {
        val action = parseGuideAction(actionArg)
        if (action == null) {
            Timber.tag(LogTags.ACTION).w("DISPATCH_FAILED reason=unknown_guide_action arg=%s", actionArg)
            failDidNotUnderstand()
            return
        }
        val controller = guideController
        val scope = scope
        if (controller == null || scope == null) {
            Timber.tag(LogTags.ACTION).w("DISPATCH_FAILED reason=guide_unavailable")
            failDidNotUnderstand()
            return
        }
        Timber.tag(LogTags.ACTION).d("DISPATCH_EXECUTED tool=show_how_to action=%s", action)
        controller.start(action, scope)
    }

    private fun parseGuideAction(arg: String): GuideAction? = runCatching {
        GuideAction.valueOf(arg.uppercase().replace('-', '_'))
    }.getOrNull()

    private suspend fun executeMaps(query: String) {
        if (query.isBlank()) {
            failDidNotUnderstand()
            return
        }
        val params = PhraseParams.forMaps(query)
        speaker.speak(phrase(PhraseIntent.CONFIRM_MAPS, params))
        when (val result = IntentBranch.openMaps(context, query)) {
            ActionResult.Launched -> {
                Timber.tag(LogTags.ACTION).d("DISPATCH_EXECUTED tool=open_maps")
                speaker.speak(phrase(PhraseIntent.SUCCESS_MAPS, params))
            }
            is ActionResult.Failed -> {
                Timber.tag(LogTags.ACTION).w("DISPATCH_FAILED reason=%s", result.reason)
                speaker.speak(phrase(PhraseIntent.FAILED_INTENT))
            }
        }
    }

    private suspend fun failDidNotUnderstand() {
        Timber.tag(LogTags.ACTION).w("DISPATCH_FAILED reason=unknown")
        speaker.speak(phrase(PhraseIntent.UNKNOWN_COMMAND))
    }

    private suspend fun executeAfterContactClarification(transcript: String, contact: ContactRef) {
        val normalized = transcript.lowercase(Locale("es", "AR"))
        if ("llam" in normalized || "telefon" in normalized) {
            executeChannel(contact, Channel.CALL, null)
            return
        }
        val message = extractMessage(transcript)
        if (message.isBlank()) {
            failDidNotUnderstand()
            return
        }
        val channel = memory.preferredChannel(contact)
            ?: channelClarifier.clarify(contact)
            ?: return
        executeChannel(contact, channel, message)
    }

    private fun extractAlias(question: String, transcript: String): String? {
        Regex("tu\\s+([^?]+)", RegexOption.IGNORE_CASE).find(question)?.let {
            return it.groupValues[1].trim().lowercase(Locale("es", "AR"))
        }
        Regex("mi\\s+(\\w+)", RegexOption.IGNORE_CASE).find(transcript)?.let {
            return it.groupValues[1].trim().lowercase(Locale("es", "AR"))
        }
        return null
    }

    private fun extractContactArg(transcript: String): String? {
        val normalized = transcript.trim()
        Regex("\\ba\\s+(.+?)\\s+que\\b", RegexOption.IGNORE_CASE).find(normalized)?.let {
            return it.groupValues[1].trim()
        }
        Regex("\\ba\\s+(.+)$", RegexOption.IGNORE_CASE).find(normalized)?.let {
            return it.groupValues[1].trim()
        }
        return null
    }

    private fun extractMessage(transcript: String): String =
        Regex("\\bque\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(transcript)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

    private fun explicitChannelFromTranscript(transcript: String): Channel? {
        val normalized = transcript.lowercase(Locale("es", "AR"))
        return when {
            "whatsapp" in normalized || "wasap" in normalized -> Channel.WHATSAPP
            "sms" in normalized -> Channel.SMS
            "mensaje de texto" in normalized -> Channel.SMS
            "llamada" in normalized || "telefono" in normalized || "teléfono" in normalized -> Channel.CALL
            else -> null
        }
    }
}
