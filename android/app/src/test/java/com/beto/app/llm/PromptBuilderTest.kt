package com.beto.app.llm

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun systemPromptIncludesCriticalKeywords() {
        val prompt = PromptBuilder.systemPrompt()

        listOf("Beto", "cálido", "Argentina", "voseo", "cortas", "clarification").forEach {
            assertTrue("Missing keyword $it", prompt.contains(it))
        }
    }

    @Test
    fun fewShotsAreValidDecisions() {
        PromptBuilder.fewShots.forEach { decision ->
            val encoded = DecisionJson.encode(decision)
            assertNotNull(DecisionJson.decode(encoded))
        }
    }

    @Test
    fun promptContainsAllAllowedToolNames() {
        val prompt = PromptBuilder.buildInterpretPrompt("mandale a Juan que ya llegué")

        ToolDescriptors.ALLOWED_TOOLS.forEach {
            assertTrue("Missing tool $it", prompt.contains(it))
        }
    }
}
