package com.beto.app.memory

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class UserMemory(
    val version: Int = 1,
    val aliases: Map<String, ContactRef> = emptyMap(),
    val channelPreferences: Map<String, Channel> = emptyMap(),
    val profile: Map<String, List<String>> = emptyMap(),
) {
    fun knowsAlias(alias: String): Boolean = aliases.containsKey(alias.normalizedKey())

    fun resolveAlias(alias: String): ContactRef? = aliases[alias.normalizedKey()]

    fun preferredChannel(contact: ContactRef): Channel? = channelPreferences[contact.id.toString()]

    fun withAlias(alias: String, contact: ContactRef): UserMemory =
        copy(aliases = aliases + (alias.normalizedKey() to contact))

    fun withChannelPreference(contact: ContactRef, channel: Channel): UserMemory =
        copy(channelPreferences = channelPreferences + (contact.id.toString() to channel))

    fun withFact(category: String, fact: String): UserMemory {
        val key = category.normalizedKey()
        return copy(profile = profile + (key to (profile[key].orEmpty() + fact)))
    }

    companion object {
        fun empty(): UserMemory = UserMemory()
    }
}

@Serializable
data class ContactRef(
    val id: Long,
    val displayName: String,
    val phoneE164: String,
)

@Serializable
enum class Channel {
    WHATSAPP,
    SMS,
    CALL,
}

private fun String.normalizedKey(): String =
    trim().lowercase(Locale("es", "AR"))
