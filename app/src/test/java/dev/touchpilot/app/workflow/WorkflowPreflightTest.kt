package dev.touchpilot.app.workflow

import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WorkflowPreflightTest {
    @Test
    fun passesWhenNoExpectedForegroundAppConfigured() {
        val definition = WorkflowDefinition(
            id = "wf-1",
            title = "Any app",
            steps = listOf(step("observe_screen")),
        )

        val result = WorkflowPreflight.check(
            definition,
            WorkflowLivePolicyContext(
                foregroundApp = ForegroundAppInfo(
                    accessibilityConnected = true,
                    packageName = "com.other.app",
                ),
            ),
        )

        assertIs<WorkflowPreflight.Result.Ok>(result)
    }

    @Test
    fun passesWhenForegroundPackageMatches() {
        val definition = WorkflowDefinition(
            id = "wf-1",
            title = "Messages",
            steps = listOf(step("observe_screen")),
            expectedForegroundPackage = "com.example.messages",
        )

        val result = WorkflowPreflight.check(
            definition,
            WorkflowLivePolicyContext(
                foregroundApp = ForegroundAppInfo(
                    accessibilityConnected = true,
                    packageName = "com.example.messages",
                ),
            ),
        )

        assertIs<WorkflowPreflight.Result.Ok>(result)
    }

    @Test
    fun reportsMismatchWhenForegroundPackageDiffers() {
        val definition = WorkflowDefinition(
            id = "wf-1",
            title = "Messages",
            steps = listOf(step("observe_screen")),
            expectedForegroundPackage = "com.example.messages",
        )

        val mismatch = assertIs<WorkflowPreflight.Result.Mismatch>(
            WorkflowPreflight.check(
                definition,
                WorkflowLivePolicyContext(
                    foregroundApp = ForegroundAppInfo(
                        accessibilityConnected = true,
                        packageName = "com.other.app",
                        appLabel = "Other",
                    ),
                ),
            )
        )

        assertEquals("com.example.messages", mismatch.expectedPackage)
        assertEquals("com.other.app", mismatch.currentPackage)
        assertEquals("Other", mismatch.currentLabel)
    }

    private fun step(tool: String) = WorkflowStep(id = "step-1", tool = tool)
}
