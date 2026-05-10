package com.beto.app.action

import com.beto.app.contacts.ContactRepository
import com.beto.app.memory.ContactRef
import com.beto.app.memory.UserMemoryStore
import com.beto.app.util.LogTags
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class ContactClarifier(
    private val speaker: Speaker,
    private val contacts: ContactRepository,
    private val memory: UserMemoryStore,
    private val voiceCapture: SuspendableVoiceCapture,
) {
    suspend fun clarify(alias: String): ContactRef? {
        repeat(MAX_ATTEMPTS) { attempt ->
            speaker.speak(if (attempt == 0) "¿Quién es tu $alias?" else "Probá con otro nombre.")
            val response = voiceCapture.captureOnce(CONTACT_CAPTURE_TIMEOUT_MS) ?: return null
            val matches = contacts.resolve(response)
            when (matches.size) {
                0 -> speaker.speak("No encuentro a nadie así. Probá con otro nombre.")
                1 -> {
                    val ref = matches.single().toContactRef()
                    memory.recordAlias(alias, ref)
                    Timber.tag(LogTags.ACTION).d("DISPATCH_CLARIFY_CONTACT alias=%s resolved=%d", alias, ref.id)
                    return ref
                }
                else -> {
                    val names = matches.joinToString(" o ") { it.displayName }
                    speaker.speak("Tengo varios. ¿Cuál? $names")
                    val pick = voiceCapture.captureOnce(CONTACT_CAPTURE_TIMEOUT_MS) ?: return null
                    val resolved = matches.firstOrNull {
                        it.displayName.contains(pick, ignoreCase = true) ||
                            pick.contains(it.displayName, ignoreCase = true)
                    }
                    if (resolved != null) {
                        val ref = resolved.toContactRef()
                        memory.recordAlias(alias, ref)
                        Timber.tag(LogTags.ACTION).d("DISPATCH_CLARIFY_CONTACT alias=%s resolved=%d", alias, ref.id)
                        return ref
                    }
                }
            }
        }
        speaker.speak("Mejor lo intentamos en otro momento.")
        return null
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        private const val CONTACT_CAPTURE_TIMEOUT_MS = 10_000L
    }
}

fun ContactClarifier.blockingClarify(alias: String): ContactRef? = runBlocking { clarify(alias) }
