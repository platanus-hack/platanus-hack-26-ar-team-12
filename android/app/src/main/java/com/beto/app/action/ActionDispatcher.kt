package com.beto.app.action

import android.content.Context
import com.beto.app.contacts.ContactRepository
import com.beto.app.llm.Decision
import com.beto.app.llm.LlmClient
import com.beto.app.llm.ToolDescriptors
import com.beto.app.memory.Channel
import com.beto.app.memory.ContactRef
import com.beto.app.memory.UserMemoryStore
import com.beto.app.util.LogTags
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

class ActionDispatcher(
    private val context: Context,
    private val llm: LlmClient,
    private val memory: UserMemoryStore,
    private val contacts: ContactRepository,
    private val contactClarifier: ContactClarifier,
    private val channelClarifier: ChannelClarifier,
    private val speaker: Speaker,
    private val sendWhatsapp: (Context, DemoContact, String) -> ActionResult = IntentBranch::sendWhatsapp,
    private val scope: CoroutineScope? = null,
) {
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
                executeTool(routed.call)
            }
            is RouteOutcome.Clarify -> handleClarification(routed.decision)
            RouteOutcome.Unknown -> failDidNotUnderstand()
            else -> Unit
        }
    }

    private fun executePlanC(match: MatchResult.Matched) {
        speaker.speak("Abro WhatsApp con el mensaje para tu nieto.")
        when (val result = sendWhatsapp(context, match.contact, match.message)) {
            ActionResult.Launched -> {
                Timber.tag(LogTags.ACTION).d("DISPATCH_EXECUTED tool=send_whatsapp")
                speaker.speak("Listo, te deje el mensaje preparado.")
            }
            is ActionResult.Failed -> {
                Timber.tag(LogTags.ACTION).w("DISPATCH_FAILED reason=%s", result.reason)
                speaker.speak("No pude abrir WhatsApp. Probemos de nuevo en un ratito, dale.")
            }
        }
    }

    private suspend fun executeTool(call: Decision.ToolCall) {
        when (call.tool) {
            ToolDescriptors.OPEN_MAPS -> executeMaps(call.args["query"].orEmpty())
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
                val channel = memory.preferredChannel(contact)
                    ?: channelClarifier.clarify(contact)
                    ?: return
                executeChannel(contact, channel, message)
            }
            else -> failDidNotUnderstand()
        }
    }

    private suspend fun handleClarification(decision: Decision.NeedsClarification) {
        Timber.tag(LogTags.ACTION).d("DISPATCH_LLM_DECISION clarification=%s", decision.expecting)
        speaker.speak(decision.question)
        failDidNotUnderstand()
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
                speaker.speak("Listo.")
            }
            is ActionResult.Failed -> {
                Timber.tag(LogTags.ACTION).w("DISPATCH_FAILED reason=%s", result.reason)
                speaker.speak("No pude hacerlo. Probemos de nuevo en un ratito.")
            }
        }
    }

    private fun executeMaps(query: String) {
        if (query.isBlank()) {
            failDidNotUnderstand()
            return
        }
        when (val result = IntentBranch.openMaps(context, query)) {
            ActionResult.Launched -> {
                Timber.tag(LogTags.ACTION).d("DISPATCH_EXECUTED tool=open_maps")
                speaker.speak("Listo, te abro el mapa.")
            }
            is ActionResult.Failed -> {
                Timber.tag(LogTags.ACTION).w("DISPATCH_FAILED reason=%s", result.reason)
                speaker.speak("No pude abrir el mapa. Probemos de nuevo en un ratito.")
            }
        }
    }

    private fun failDidNotUnderstand() {
        Timber.tag(LogTags.ACTION).w("DISPATCH_FAILED reason=unknown")
        speaker.speak("No te entendí del todo, repetímelo más despacito.")
    }
}
