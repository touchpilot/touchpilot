package dev.touchpilot.app.demonstration

/**
 * Validates demonstration sessions for completeness and schema integrity.
 */
object DemonstrationValidator {
    fun validate(session: DemonstrationSession): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (session.metadata.sessionId.isBlank()) errors += "Missing session id"
        if (session.metadata.runId.isBlank()) errors += "Missing run id"
        if (session.metadata.task.isBlank()) warnings += "Empty task description"

        session.steps.forEach { step ->
            if (step.action.tool.isBlank()) errors += "Step ${step.index}: missing tool name"
            if (step.beforeFrame.contextJson.isBlank()) errors += "Step ${step.index}: missing before screen"
            if (step.afterFrame.contextJson.isBlank()) errors += "Step ${step.index}: missing after screen"
            if (step.beforeFrame.sequenceNumber == step.afterFrame.sequenceNumber) {
                warnings += "Step ${step.index}: before/after share sequence number"
            }
            if (!step.action.succeeded) {
                warnings += "Step ${step.index}: tool ${step.action.tool} failed"
            }
        }

        if (session.steps.isEmpty()) warnings += "No steps captured"
        if (session.initialFrame == null) warnings += "No initial screen frame"
        if (session.finalFrame == null && session.metadata.status != DemonstrationStatus.RECORDING) {
            warnings += "No final screen frame"
        }
        if (session.containsSensitiveContent) {
            warnings += "Session contains sensitive content (redacted)"
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    fun validateAll(sessions: List<DemonstrationSession>): Map<String, ValidationResult> {
        return sessions.associate { it.sessionId to validate(it) }
    }

    data class ValidationResult(
        val valid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
    ) {
        fun summary(): String {
            return when {
                !valid -> "Invalid: ${errors.joinToString("; ")}"
                warnings.isNotEmpty() -> "Valid with warnings: ${warnings.joinToString("; ")}"
                else -> "Valid"
            }
        }
    }
}
