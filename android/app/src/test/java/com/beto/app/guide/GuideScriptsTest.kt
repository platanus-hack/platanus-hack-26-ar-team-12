package com.beto.app.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideScriptsTest {

    @Test
    fun `every action has a script`() {
        GuideAction.values().forEach { action ->
            val script = GuideScripts.forAction(action)
            assertNotNull("$action should have a script", script)
            assertEquals(action, script.action)
        }
    }

    @Test
    fun `every script has at least one step`() {
        GuideScripts.all().forEach { script ->
            assertTrue("${script.action} must have ≥ 1 step", script.steps.isNotEmpty())
        }
    }

    @Test
    fun `every step voice text is concise (less than 25 words)`() {
        GuideScripts.all().forEach { script ->
            script.steps.forEach { step ->
                val wordCount = step.voiceText.split(' ').count { it.isNotBlank() }
                assertTrue(
                    "${script.action} step '${step.voiceText}' too long ($wordCount words)",
                    wordCount <= 25,
                )
            }
        }
    }

    @Test
    fun `every step voice text uses voseo (no usted)`() {
        GuideScripts.all().forEach { script ->
            script.steps.forEach { step ->
                val text = step.voiceText.lowercase()
                assertTrue(
                    "${script.action} step '${step.voiceText}' contains 'usted' (formal)",
                    !Regex("\\busted(es)?\\b").containsMatchIn(text),
                )
            }
        }
    }

    @Test
    fun `every step voice text is non-blank`() {
        GuideScripts.all().forEach { script ->
            script.steps.forEach { step ->
                assertTrue(
                    "${script.action} has blank voice text",
                    step.voiceText.isNotBlank(),
                )
            }
        }
    }

    @Test
    fun `whatsapp audio guide targets 'Mensaje de voz'`() {
        val script = GuideScripts.forAction(GuideAction.SEND_WHATSAPP_AUDIO)
        val firstTarget = script.steps.first().target
        assertTrue(firstTarget is TargetSelector.ByContentDescription)
        assertEquals(
            "Mensaje de voz",
            (firstTarget as TargetSelector.ByContentDescription).description,
        )
        assertEquals("com.whatsapp", script.appPackage)
    }

    @Test
    fun `add contact uses OPEN_APP intent kind`() {
        val script = GuideScripts.forAction(GuideAction.ADD_CONTACT)
        assertEquals(IntentKind.OPEN_APP, script.intentKind)
    }

    @Test
    fun `increase volume has no app package`() {
        val script = GuideScripts.forAction(GuideAction.INCREASE_VOLUME)
        assertEquals(null, script.appPackage)
    }

    @Test
    fun `script init rejects empty steps`() {
        val ex = runCatching {
            GuideScript(
                action = GuideAction.OPEN_CAMERA,
                appPackage = null,
                intentKind = IntentKind.NONE,
                steps = emptyList(),
            )
        }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException", ex is IllegalArgumentException)
    }

    @Test
    fun `script init rejects step with too long voice text`() {
        val long = (1..30).joinToString(" ") { "palabra$it" }
        val ex = runCatching {
            GuideScript(
                action = GuideAction.OPEN_CAMERA,
                appPackage = null,
                intentKind = IntentKind.NONE,
                steps = listOf(
                    GuideStep(
                        target = TargetSelector.ByText("foo"),
                        voiceText = long,
                    ),
                ),
            )
        }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException", ex is IllegalArgumentException)
    }
}
