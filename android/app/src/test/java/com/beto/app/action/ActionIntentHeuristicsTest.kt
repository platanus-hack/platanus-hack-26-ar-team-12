package com.beto.app.action

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionIntentHeuristicsTest {

    @Test
    fun detectsCallIntentEvenWhenTranscriptMentionsWhatsapp() {
        assertTrue(ActionIntentHeuristics.requestsCall("Beto podría llamar a Fran Iturain por WhatsApp"))
        assertTrue(ActionIntentHeuristics.requestsCall("llamá a Fran por whatsapp"))
    }

    @Test
    fun doesNotTreatPlainWhatsappMessageAsCall() {
        assertFalse(ActionIntentHeuristics.requestsCall("mandale a Fran por WhatsApp que ya llegué"))
    }
}
