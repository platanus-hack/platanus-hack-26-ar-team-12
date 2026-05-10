package com.beto.app.action

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentBranchTest {

    @Test
    fun buildsWaMeUri() {
        val contact = DemoContact(
            canonicalName = "Mi nieto",
            e164 = "+54 9 11 3948-2682",
            aliases = setOf("nieto"),
        )

        val spec = IntentBranch.buildWhatsappIntentSpec(contact, "ya llegue")

        assertTrue(spec.uri.contains("https://wa.me/5491139482682?text=ya%20llegue"))
    }

    @Test
    fun targetsRegularWhatsapp() {
        val spec = IntentBranch.buildWhatsappIntentSpec(DemoContacts.nieto, "ya llegue")

        assertEquals("com.whatsapp", spec.packageName)
        assertEquals(Intent.ACTION_VIEW, spec.action)
    }
}
