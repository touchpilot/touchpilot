package dev.touchpilot.app.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
