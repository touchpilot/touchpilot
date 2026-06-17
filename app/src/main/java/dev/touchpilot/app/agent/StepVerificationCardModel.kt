package dev.touchpilot.app.agent

import dev.touchpilot.app.security.SensitiveTextRedactor

data class StepVerificationCardModel(
    val stepIndex: Int,
    val tool: String,
    val passed: Boolean,
    val expectedSummary: String,
    val observedSummary: String,
    val reason: String,
) {
    val title: String
        get() = if (passed) {
            "Step $stepIndex verified"
        } else {
            "Step $stepIndex verification failed"
        }

    val body: String
        get() = buildString {
            append("After $tool:")
            append("\nExpected: $expectedSummary")
            append("\nObserved: $observedSummary")
            if (reason.isNotBlank()) {
                append("\nReason: $reason")
            }
        }

    companion object {
        fun from(event: AgentEvent.WorkflowStepVerificationPassed): StepVerificationCardModel {
            return StepVerificationCardModel(
                stepIndex = event.stepIndex,
                tool = event.tool,
                passed = true,
                expectedSummary = event.expectedSummary.redacted(),
                observedSummary = event.observedSummary.redacted(),
                reason = "Expected state reached.",
            )
        }

        fun from(event: AgentEvent.WorkflowStepVerificationFailed): StepVerificationCardModel {
            return StepVerificationCardModel(
                stepIndex = event.stepIndex,
                tool = event.tool,
                passed = false,
                expectedSummary = event.expectedSummary.redacted(),
                observedSummary = event.observedSummary.redacted(),
                reason = event.reason.redacted(),
            )
        }

        private fun String.redacted(): String = SensitiveTextRedactor.redact(this)
    }
}
