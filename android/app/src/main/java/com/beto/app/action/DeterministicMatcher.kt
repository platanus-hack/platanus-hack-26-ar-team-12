package com.beto.app.action

import java.text.Normalizer
import java.util.Locale

sealed class MatchResult {
    data class Matched(val contact: DemoContact, val message: String) : MatchResult()
    data class NeedsContact(val message: String?) : MatchResult()
    data class NeedsMessage(val contactAlias: String?) : MatchResult()
    object NoMatch : MatchResult()
}

object DeterministicMatcher {

    private val verbs = listOf(
        "mandale",
        "manda",
        "mandar",
        "avisale",
        "avisa",
        "decile",
        "dile",
        "escribile",
    )

    private val connectors = listOf(
        "con el mensaje",
        "para decirle",
        "diciendo",
        "de que",
        "que",
    )

    fun match(input: String): MatchResult {
        val normalized = normalize(input)
        if (normalized.isBlank() || verbs.none { normalized.startsWithVerb(it) }) {
            return MatchResult.NoMatch
        }

        val contactMatch = findContactAlias(normalized)
        val message = extractMessage(normalized, contactMatch?.range)

        return when {
            contactMatch != null && !message.isNullOrBlank() -> {
                MatchResult.Matched(contactMatch.contact, message)
            }
            contactMatch != null -> MatchResult.NeedsMessage(contactMatch.alias)
            !message.isNullOrBlank() -> MatchResult.NeedsContact(message)
            else -> MatchResult.NoMatch
        }
    }

    fun normalize(value: String): String {
        val noAccents = Normalizer.normalize(value.lowercase(Locale("es", "AR")), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return noAccents
            .replace("[^a-z0-9 ]+".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun String.startsWithVerb(verb: String): Boolean =
        this == verb || startsWith("$verb ") || startsWith("$verb a ")

    private fun findContactAlias(input: String): ContactMatch? {
        return DemoContacts.all.asSequence()
            .flatMap { contact ->
                contact.aliases.asSequence().map { alias -> contact to normalize(alias) }
            }
            .mapNotNull { (contact, alias) ->
                val index = input.indexOfAlias(alias)
                if (index == -1) null else ContactMatch(contact, alias, index until index + alias.length)
            }
            .maxByOrNull { it.alias.length }
    }

    private fun String.indexOfAlias(alias: String): Int {
        val match = Regex("(^| )${Regex.escape(alias)}( |$)").find(this) ?: return -1
        return match.range.first + match.value.indexOf(alias)
    }

    private fun extractMessage(input: String, contactRange: IntRange?): String? {
        for (connector in connectors) {
            val marker = " $connector "
            val index = input.indexOf(marker)
            if (index != -1) {
                return input.substring(index + marker.length).trim().takeIf { it.isNotBlank() }
            }
        }

        if (contactRange != null) {
            return null
        }

        val verb = verbs.firstOrNull { input.startsWithVerb(it) } ?: return null
        return input.removePrefix(verb)
            .removePrefix(" a ")
            .removePrefix(" ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private data class ContactMatch(
        val contact: DemoContact,
        val alias: String,
        val range: IntRange,
    )
}
