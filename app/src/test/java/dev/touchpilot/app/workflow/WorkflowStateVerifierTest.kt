package dev.touchpilot.app.workflow

import dev.touchpilot.app.screen.ScreenContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowStateVerifierTest {
    @Test
    fun passesWhenTextAppearsWithinTimeout() {
        val observation = FakeWorkflowObservation(
            contexts = listOf(WorkflowStateVerifier.screenWithText("Network")),
            waitForTextResults = listOf(true),
        )
        val verifier = WorkflowStateVerifier(observation = observation, pollIntervalMs = 1L)

        val outcome = verifier.verify(ExpectedState.TextPresent("Network"), timeoutMs = 500L)

        assertTrue(outcome.passed)
        assertEquals("Text \"Network\" is present on screen", outcome.expectedSummary)
        assertTrue(outcome.observedSummary.contains("Network"))
    }

    @Test
    fun failsWhenTextNeverAppears() {
        val observation = FakeWorkflowObservation(
            contexts = listOf(WorkflowStateVerifier.screenWithText("Home")),
            waitForTextResults = listOf(false),
        )
        val verifier = WorkflowStateVerifier(observation = observation, pollIntervalMs = 1L)

        val outcome = verifier.verify(ExpectedState.TextPresent("Network"), timeoutMs = 250L)

        assertFalse(outcome.passed)
        assertTrue(outcome.observedSummary.contains("Home"))
        assertTrue(outcome.reason.contains("250"))
    }

    @Test
    fun passesWhenKeyboardHidden() {
        val observation = FakeWorkflowObservation(
            contexts = listOf(WorkflowStateVerifier.screenWithText("Compose")),
            keyboardVisible = false,
        )
        val verifier = WorkflowStateVerifier(observation = observation, pollIntervalMs = 1L)

        val outcome = verifier.verify(ExpectedState.KeyboardVisible(visible = false), timeoutMs = 500L)

        assertTrue(outcome.passed)
    }

    @Test
    fun failsWhenForegroundPackageDoesNotMatch() {
        val observation = FakeWorkflowObservation(
            contexts = listOf(
                ScreenContext(packageName = "com.example.other", appLabel = "Other"),
            ),
        )
        val verifier = WorkflowStateVerifier(observation = observation, pollIntervalMs = 1L)

        val outcome = verifier.verify(
            ExpectedState.ForegroundPackage("com.android.settings"),
            timeoutMs = 250L,
        )

        assertFalse(outcome.passed)
        assertTrue(outcome.observedSummary.contains("com.example.other"))
    }

    @Test
    fun passesWhenAllConditionsMatch() {
        val observation = FakeWorkflowObservation(
            contexts = listOf(
                ScreenContext(
                    appLabel = "Settings",
                    packageName = "com.android.settings",
                    nodes = listOf(
                        dev.touchpilot.app.screen.ScreenNode(
                            text = dev.touchpilot.app.screen.ScreenText.of("Network"),
                        )
                    ),
                )
            ),
            keyboardVisible = false,
        )
        val verifier = WorkflowStateVerifier(observation = observation, pollIntervalMs = 1L)

        val outcome = verifier.verify(
            ExpectedState.All(
                listOf(
                    ExpectedState.ForegroundApp("Settings"),
                    ExpectedState.TextPresent("Network"),
                    ExpectedState.KeyboardVisible(visible = false),
                )
            ),
            timeoutMs = 500L,
        )

        assertTrue(outcome.passed)
    }

    @Test
    fun passesWhenWindowTitleMatches() {
        val observation = FakeWorkflowObservation(
            contexts = listOf(
                ScreenContext(
                    windowTitle = "Wi-Fi settings",
                    nodes = emptyList(),
                )
            ),
        )
        val verifier = WorkflowStateVerifier(observation = observation, pollIntervalMs = 1L)

        val outcome = verifier.verify(ExpectedState.WindowTitle("Wi-Fi"), timeoutMs = 500L)

        assertTrue(outcome.passed)
    }

    @Test
    fun failsWhenExactTextDoesNotMatch() {
        val observation = FakeWorkflowObservation(
            contexts = listOf(WorkflowStateVerifier.screenWithText("Network settings")),
        )
        val verifier = WorkflowStateVerifier(observation = observation, pollIntervalMs = 1L)

        val outcome = verifier.verify(ExpectedState.TextPresent("Network", exact = true), timeoutMs = 250L)

        assertFalse(outcome.passed)
    }

    private class FakeWorkflowObservation(
        private val contexts: List<ScreenContext>,
        private val waitForTextResults: List<Boolean> = emptyList(),
        private var keyboardVisible: Boolean = false,
    ) : WorkflowObservation {
        private var contextIndex = 0
        private var waitIndex = 0

        override fun waitForText(text: String, timeoutMs: Long): Boolean {
            return waitForTextResults.getOrElse(waitIndex++) { false }
        }

        override fun isKeyboardVisible(): Boolean = keyboardVisible

        override fun observeScreenContext(): ScreenContext {
            val context = contexts.getOrElse(contextIndex) { contexts.last() }
            if (contextIndex < contexts.lastIndex) {
                contextIndex += 1
            }
            return context
        }
    }
}
