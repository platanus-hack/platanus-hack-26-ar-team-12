package com.beto.app.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicMatcherTest {

    @Test
    fun matchesDemoNietoMessage() {
        val result = DeterministicMatcher.match("mandale a mi nieto que ya llegue")

        assertMatched(result, "Mi nieto", "ya llegue")
    }

    @Test
    fun matchesAvisaleVariant() {
        val result = DeterministicMatcher.match("avisale a mi nieto que ya llegue")

        assertMatched(result, "Mi nieto", "ya llegue")
    }

    @Test
    fun matchesDecileVariant() {
        val result = DeterministicMatcher.match("decile a mi nieto que ya llegue")

        assertMatched(result, "Mi nieto", "ya llegue")
    }

    @Test
    fun matchesDileVariant() {
        val result = DeterministicMatcher.match("dile a mi nieto que ya llegue")

        assertMatched(result, "Mi nieto", "ya llegue")
    }

    @Test
    fun matchesEscribileVariant() {
        val result = DeterministicMatcher.match("escribile a mi nieto que ya llegue")

        assertMatched(result, "Mi nieto", "ya llegue")
    }

    @Test
    fun extractsMessageWithParaDecirle() {
        val result = DeterministicMatcher.match("avisale a mi nieto para decirle ya llegue")

        assertMatched(result, "Mi nieto", "ya llegue")
    }

    @Test
    fun needsMessageWhenContactPresent() {
        val result = DeterministicMatcher.match("mandale a mi nieto")

        assertTrue(result is MatchResult.NeedsMessage)
        assertEquals("mi nieto", (result as MatchResult.NeedsMessage).contactAlias)
    }

    @Test
    fun needsContactWhenMessagePresent() {
        val result = DeterministicMatcher.match("avisale que ya llegue")

        assertTrue(result is MatchResult.NeedsContact)
        assertEquals("ya llegue", (result as MatchResult.NeedsContact).message)
    }

    @Test
    fun noMatchForUnrelatedCommand() {
        val result = DeterministicMatcher.match("abrime el mapa")

        assertEquals(MatchResult.NoMatch, result)
    }

    private fun assertMatched(result: MatchResult, contactName: String, message: String) {
        assertTrue(result is MatchResult.Matched)
        val matched = result as MatchResult.Matched
        assertEquals(contactName, matched.contact.canonicalName)
        assertEquals(message, matched.message)
    }
}
