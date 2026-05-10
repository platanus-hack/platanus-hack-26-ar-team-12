package com.beto.app.llm

object Sanitizer {
    private val CARD = Regex("""(?<!\d)(?:\d[\s-]?){15}\d(?!\d)""")
    private val PHONE_WITH_COUNTRY = Regex("""(?<!\d)\+?54[\s-]?(?:\d[\s-]?){10}(?!\d)""")
    private val PHONE_LOCAL = Regex("""(?<!\d)(?:\d[\s-]?){9,10}\d(?!\d)""")
    private val DNI = Regex("""(?<!\d)\d{7,8}(?!\d)""")

    fun sanitize(input: String): String =
        input
            .replace(CARD, "[TARJETA]")
            .replace(PHONE_WITH_COUNTRY, "[TEL]")
            .replace(PHONE_LOCAL, "[TEL]")
            .replace(DNI, "[DNI]")
}
