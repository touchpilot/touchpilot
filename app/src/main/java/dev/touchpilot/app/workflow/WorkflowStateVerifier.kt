package dev.touchpilot.app.workflow

import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenText

data class WorkflowVerificationOutcome(
    val passed: Boolean,
    val expectedSummary: String,
    val observedSummary: String,
    val reason: String,
)

fun interface WorkflowStepVerifier {
    fun verify(expected: ExpectedState, timeoutMs: Long): WorkflowVerificationOutcome
}

class WorkflowStateVerifier(
    private val observation: WorkflowObservation = AccessibilityWorkflowObservation,
    private val pollIntervalMs: Long = 150L,
) : WorkflowStepVerifier {
    override fun verify(expected: ExpectedState, timeoutMs: Long): WorkflowVerificationOutcome {
        val clampedTimeout = timeoutMs.coerceIn(
            WorkflowStep.MIN_TIMEOUT_MS,
            WorkflowStep.MAX_TIMEOUT_MS,
        )
        if (expected is ExpectedState.TextPresent && !expected.exact) {
            val found = observation.waitForText(expected.text, clampedTimeout)
            val observed = observation.observeScreenContext()
            return if (found) {
                WorkflowVerificationOutcome(
                    passed = true,
                    expectedSummary = expected.describe(),
                    observedSummary = describeObserved(observed),
                    reason = "Expected text appeared on screen.",
                )
            } else {
                WorkflowVerificationOutcome(
                    passed = false,
                    expectedSummary = expected.describe(),
                    observedSummary = describeObserved(observed),
                    reason = "Expected state was not reached within ${clampedTimeout}ms.",
                )
            }
        }

        val deadline = System.currentTimeMillis() + clampedTimeout
        var lastObserved = observation.observeScreenContext()
        while (System.currentTimeMillis() < deadline) {
            lastObserved = observation.observeScreenContext()
            if (matches(expected, lastObserved)) {
                return WorkflowVerificationOutcome(
                    passed = true,
                    expectedSummary = expected.describe(),
                    observedSummary = describeObserved(lastObserved),
                    reason = "Expected state reached.",
                )
            }
            Thread.sleep(pollIntervalMs)
        }
        return WorkflowVerificationOutcome(
            passed = false,
            expectedSummary = expected.describe(),
            observedSummary = describeObserved(lastObserved),
            reason = "Expected state was not reached within ${clampedTimeout}ms.",
        )
    }

    private fun matches(expected: ExpectedState, context: ScreenContext): Boolean {
        return when (expected) {
            is ExpectedState.TextPresent -> containsText(context, expected.text, expected.exact)
            is ExpectedState.WindowTitle ->
                context.windowTitle?.contains(expected.title, ignoreCase = true) == true
            is ExpectedState.KeyboardVisible -> observation.isKeyboardVisible() == expected.visible
            is ExpectedState.ForegroundPackage ->
                context.packageName.equals(expected.packageName, ignoreCase = true)
            is ExpectedState.ForegroundApp ->
                context.appLabel.equals(expected.appLabel, ignoreCase = true)
            is ExpectedState.All -> expected.conditions.all { matches(it, context) }
        }
    }

    private fun containsText(context: ScreenContext, text: String, exact: Boolean): Boolean {
        return context.nodes.any { node ->
            val textValue = node.text.raw
            val contentValue = node.contentDescription?.raw
            if (exact) {
                textValue.equals(text, ignoreCase = true) ||
                    contentValue?.equals(text, ignoreCase = true) == true
            } else {
                textValue.contains(text, ignoreCase = true) ||
                    contentValue?.contains(text, ignoreCase = true) == true
            }
        }
    }

    companion object {
        fun describeObserved(context: ScreenContext): String {
            val parts = mutableListOf<String>()
            context.appLabel?.takeIf { it.isNotBlank() }?.let { parts += "app=\"$it\"" }
            context.packageName?.takeIf { it.isNotBlank() }?.let { parts += "package=\"$it\"" }
            context.windowTitle?.takeIf { it.isNotBlank() }?.let { parts += "window=\"$it\"" }

            val visibleLabels = context.nodes
                .mapNotNull { node ->
                    val label = node.text.displaySafe.takeIf { it.isNotBlank() }
                        ?: node.contentDescription?.displaySafe?.takeIf { it.isNotBlank() }
                    label
                }
                .distinct()
                .take(5)
            if (visibleLabels.isNotEmpty()) {
                parts += "visible text includes ${visibleLabels.joinToString(", ") { "\"$it\"" }}"
            } else if (context.nodes.isEmpty()) {
                parts += "no accessibility nodes observed"
            }

            return if (parts.isEmpty()) {
                "empty screen context"
            } else {
                parts.joinToString("; ")
            }
        }

        internal fun screenWithText(text: String, appLabel: String? = null): ScreenContext {
            return ScreenContext(
                appLabel = appLabel,
                nodes = listOf(
                    dev.touchpilot.app.screen.ScreenNode(
                        text = ScreenText.of(text),
                    )
                ),
            )
        }
    }
}
