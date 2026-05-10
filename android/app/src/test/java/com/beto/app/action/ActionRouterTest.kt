package com.beto.app.action

import com.beto.app.llm.Decision
import com.beto.app.llm.ToolDescriptors
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRouterTest {

    @Test
    fun planCHitShortCircuitsLlm() {
        val result = ActionRouter.routeTranscript("mandale a mi nieto que ya llegue")

        assertTrue(result is RouteOutcome.PlanC)
    }

    @Test
    fun noPlanCNeedsLlm() {
        val result = ActionRouter.routeTranscript("llama a Pedro")

        assertTrue(result is RouteOutcome.NeedsLlm)
    }

    @Test
    fun llmToolCallExecutesIntent() {
        val result = ActionRouter.routeDecision(
            Decision.ToolCall(ToolDescriptors.MAKE_CALL, mapOf("contact" to "Pedro")),
        )

        assertTrue(result is RouteOutcome.ExecuteTool)
    }

    @Test
    fun llmNeedsClarificationRoutesClarifier() {
        val result = ActionRouter.routeDecision(
            Decision.NeedsClarification("¿Quién es tu nieto?", com.beto.app.llm.ExpectedAnswer.CONTACT_NAME),
        )

        assertTrue(result is RouteOutcome.Clarify)
    }

    @Test
    fun unknownResponseLeadsToWarmFailurePath() {
        val result = ActionRouter.routeDecision(Decision.Unknown)

        assertTrue(result is RouteOutcome.Unknown)
    }
}
