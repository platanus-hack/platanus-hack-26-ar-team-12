package com.beto.app.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class SanitizerTest {

    @Test
    fun redactsArgentineDni() {
        assertEquals("mi DNI es [DNI]", Sanitizer.sanitize("mi DNI es 12345678"))
    }

    @Test
    fun redactsPhoneWithCountryCode() {
        assertEquals("llamame al [TEL]", Sanitizer.sanitize("llamame al +54 11 1234 5678"))
    }

    @Test
    fun redactsPhoneWithoutCountryCode() {
        assertEquals("mi numero [TEL]", Sanitizer.sanitize("mi numero 1123456789"))
    }

    @Test
    fun redactsCreditCard16Digits() {
        assertEquals(
            "mi tarjeta es [TARJETA]",
            Sanitizer.sanitize("mi tarjeta es 1234 5678 9012 3456"),
        )
    }

    @Test
    fun doesNotRedactFourDigitNumbers() {
        assertEquals("el año 2026", Sanitizer.sanitize("el año 2026"))
    }

    @Test
    fun doesNotRedactSmallNumbersInMessages() {
        assertEquals("comprame 2 manzanas", Sanitizer.sanitize("comprame 2 manzanas"))
    }

    @Test
    fun redactsMultipleSensitiveValues() {
        assertEquals(
            "DNI [DNI], tel [TEL], tarjeta [TARJETA]",
            Sanitizer.sanitize("DNI 12345678, tel +54 11 1234 5678, tarjeta 1234-5678-9012-3456"),
        )
    }
}
