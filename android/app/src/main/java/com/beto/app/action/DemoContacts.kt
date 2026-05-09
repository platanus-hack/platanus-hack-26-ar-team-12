package com.beto.app.action

data class DemoContact(
    val canonicalName: String,
    val e164: String,
    val aliases: Set<String>,
)

object DemoContacts {
    const val DEMO_NIETO_DISPLAY_NAME = "Mi nieto"
    const val DEMO_NIETO_E164 = "+54 9 11 3948-2682"

    val nieto = DemoContact(
        canonicalName = DEMO_NIETO_DISPLAY_NAME,
        e164 = DEMO_NIETO_E164,
        aliases = setOf("nieto", "mi nieto", DEMO_NIETO_DISPLAY_NAME.lowercase()),
    )

    val all: List<DemoContact> = listOf(nieto)

    fun resolve(alias: String): DemoContact? {
        val normalized = DeterministicMatcher.normalize(alias)
        return all.firstOrNull { contact ->
            contact.aliases.any { DeterministicMatcher.normalize(it) == normalized }
        }
    }
}
