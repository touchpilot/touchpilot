package dev.touchpilot.app.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.json.JSONObject

class WorkflowModelsTest {
    @Test
    fun roundTripsTypedExpectedStateWorkflowJson() {
        val json = JSONObject(
            """
            {
              "version": 1,
              "id": "open-settings",
              "title": "Open Settings",
              "steps": [
                {
                  "id": "tap-settings",
                  "tool": "tap",
                  "args": {"text": "Settings"},
                  "timeout_ms": 3000,
                  "expected_state": {"type": "text_present", "text": "Network"}
                }
              ]
            }
            """.trimIndent()
        )

        val workflow = WorkflowDefinition.fromJson(json)
        val restored = WorkflowDefinition.fromJson(workflow.toJson())

        assertEquals("open-settings", restored.id)
        assertEquals("Open Settings", restored.title)
        assertEquals(1, restored.steps.size)
        assertEquals("tap-settings", restored.steps.single().id)
        assertEquals("tap", restored.steps.single().tool)
        assertEquals("Settings", restored.steps.single().args["text"])
        assertEquals(3000L, restored.steps.single().timeoutMs)
        assertEquals(
            WorkflowExpectedState(screenTextContains = listOf("Network")),
            restored.steps.single().expectedState,
        )
        assertEquals(
            ExpectedState.TextPresent("Network"),
            restored.steps.single().expectedState?.toExpectedState(),
        )
    }

    @Test
    fun parsesCompositeExpectedState() {
        val state = ExpectedState.fromJson(
            JSONObject(
                """
                {
                  "type": "all",
                  "conditions": [
                    {"type": "keyboard_hidden"},
                    {"type": "foreground_app", "app": "Settings"}
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(
            ExpectedState.All(
                listOf(
                    ExpectedState.KeyboardVisible(visible = false),
                    ExpectedState.ForegroundApp("Settings"),
                )
            ),
            state,
        )
    }

    @Test
    fun workflowDefinitionRequiresNonBlankIdAndTitle() {
        assertFailsWith<IllegalArgumentException> {
            WorkflowDefinition(
                id = "",
                title = "Test",
                steps = listOf(step("observe_screen")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WorkflowDefinition(
                id = "wf-1",
                title = "",
                steps = listOf(step("observe_screen")),
            )
        }
    }

    @Test
    fun workflowDefinitionRequiresAtLeastOneStep() {
        assertFailsWith<IllegalArgumentException> {
            WorkflowDefinition(id = "wf-1", title = "Test", steps = emptyList())
        }
    }

    @Test
    fun workflowParametersResolveValuesWithDefaultsAndSupplied() {
        val definition = WorkflowDefinition(
            id = "wf-1",
            title = "Test",
            parameters = listOf(
                WorkflowParameter(name = "app_name", default = "Settings"),
                WorkflowParameter(name = "user_name", required = true),
            ),
            steps = listOf(step("open_app")),
        )

        val resolved = WorkflowParameters.resolveValues(
            definition,
            mapOf("user_name" to "Alex", "extra" to "bonus"),
        )

        assertEquals("Settings", resolved["app_name"])
        assertEquals("Alex", resolved["user_name"])
        assertEquals("bonus", resolved["extra"])
    }

    private fun step(tool: String, id: String = "step-1") = WorkflowStep(id = id, tool = tool)
}
