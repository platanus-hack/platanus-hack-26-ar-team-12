package com.beto.app.action

import com.beto.app.llm.Decision
import com.beto.app.llm.ToolDescriptors

sealed class RouteOutcome {
    data class PlanC(val match: MatchResult.Matched) : RouteOutcome()
    data class NeedsLlm(val transcript: String) : RouteOutcome()
    data class ExecuteTool(val call: Decision.ToolCall) : RouteOutcome()
    data class Clarify(val decision: Decision.NeedsClarification) : RouteOutcome()
    object Unknown : RouteOutcome()
}

object ActionRouter {
    fun routeTranscript(transcript: String): RouteOutcome =
        when (val match = DeterministicMatcher.match(transcript)) {
            is MatchResult.Matched -> RouteOutcome.PlanC(match)
            else -> RouteOutcome.NeedsLlm(transcript)
        }

    fun routeDecision(decision: Decision): RouteOutcome =
        when (decision) {
            is Decision.ToolCall -> {
                if (decision.tool in ToolDescriptors.ALLOWED_TOOLS) RouteOutcome.ExecuteTool(decision) else RouteOutcome.Unknown
            }
            is Decision.NeedsClarification -> RouteOutcome.Clarify(decision)
            Decision.Unknown -> RouteOutcome.Unknown
        }
}
