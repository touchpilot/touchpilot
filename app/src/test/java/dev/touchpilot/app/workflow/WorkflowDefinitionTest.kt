package dev.touchpilot.app.workflow

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import org.json.JSONObject

class WorkflowDefinitionParserTest {
  private fun validJson(): String = """
    {
      "version": 1,
      "id": "open-wifi-settings",
      "title": "Open Wi-Fi Settings",
      "description": "Open Wi-Fi settings and verify the panel.",
      "parameters": [
        {
          "name": "panel_label",
          "description": "Label shown on the Wi-Fi screen",
          "default": "Wi-Fi",
          "required": false
        }
      ],
      "skill_scope": {
        "skill_id": "settings",
        "allowed_tools": ["open_settings_panel", "wait_for_idle"]
      },
      "expected_foreground_package": "com.android.settings",
      "steps": [
        {
          "id": "open-panel",
          "tool": "open_settings_panel",
          "args": {
            "panel": "wifi"
          },
          "expected_state": {
            "package_name": "com.android.settings",
            "screen_text_contains": ["Wi-Fi"],
            "element_present": [
              {
                "text": "Wi-Fi",
                "match": "contains"
              }
            ]
          },
          "policy": {
            "requires_approval": true,
            "workflow_class": "security_settings"
          }
        }
      ]
    }
  """.trimIndent()

  private fun valid(result: WorkflowParseResult): WorkflowDefinition = when (result) {
    is WorkflowParseResult.Valid -> result.definition
    is WorkflowParseResult.Invalid -> fail("expected Valid, got Invalid: ${result.errors}")
  }

  private fun invalid(result: WorkflowParseResult): WorkflowParseResult.Invalid = when (result) {
    is WorkflowParseResult.Invalid -> result
    is WorkflowParseResult.Valid -> fail("expected Invalid, got Valid: ${result.definition.id}")
  }

  @Test
  fun parsesValidWorkflow() {
    val definition = valid(WorkflowDefinitionParser.parse(validJson()))

    assertEquals("open-wifi-settings", definition.id)
    assertEquals("Open Wi-Fi Settings", definition.title)
    assertEquals(1, definition.parameters.size)
    assertEquals("panel_label", definition.parameters.first().name)
    assertEquals("settings", definition.skillScope?.skillId)
    assertEquals("open_settings_panel", definition.steps.first().tool)
    assertEquals("com.android.settings", definition.expectedForegroundPackage)
    assertEquals(true, definition.steps.first().policy?.requiresApproval)
  }

  @Test
  fun rejectsUnknownTool() {
    val json = validJson().replace("\"open_settings_panel\"", "\"not_a_real_tool\"")
    val result = invalid(WorkflowDefinitionParser.parse(json))
    assertTrue(result.errors.any { it.contains("unknown tool") })
  }

  @Test
  fun rejectsUndefinedParameterPlaceholder() {
    val json = validJson().replace("\"wifi\"", "\"{missing_param}\"")
    val result = invalid(WorkflowDefinitionParser.parse(json))
    assertTrue(result.errors.any { it.contains("undefined parameter") })
  }

  @Test
  fun rejectsUnsupportedVersion() {
    val json = validJson().replace("\"version\": 1", "\"version\": 99")
    val result = invalid(WorkflowDefinitionParser.parse(json))
    assertTrue(result.errors.any { it.contains("unsupported version") })
  }

  @Test
  fun rejectsEmptySteps() {
    val json = validJson().replace(
      Regex(""""steps": \[[\s\S]*?\]"""),
      """"steps": []""",
    )
    val result = invalid(WorkflowDefinitionParser.parse(json))
    assertTrue(result.errors.any { it.contains("steps") })
  }
}

class WorkflowDefinitionRoundTripTest {
  @Test
  fun roundTripsThroughJson() {
    val original = WorkflowDefinition(
      id = "tap-save",
      title = "Tap Save",
      description = "Tap the Save button.",
      parameters = listOf(
        WorkflowParameter(name = "button_label", default = "Save"),
      ),
      steps = listOf(
        WorkflowStep(
          id = "tap",
          tool = "tap",
          args = mapOf("text" to "{button_label}"),
          expectedState = WorkflowExpectedState(
            screenTextContains = listOf("Saved"),
            elementPresent = listOf(
              WorkflowElementPredicate(text = "Saved", match = WorkflowTextMatch.EXACT),
            ),
          ),
        ),
      ),
    )

    val restored = WorkflowDefinition.fromJson(original.toJson())
    assertEquals(original, restored)
  }
}

class WorkflowTraceSerializerTest {
  @Test
  fun convertsTraceToDefinitionWithInferredParameters() {
    val trace = WorkflowTrace(
      id = "run-1",
      title = "Open Wi-Fi settings",
      task = "open Wi-Fi settings",
      steps = listOf(
        WorkflowTraceStep(
          sequenceNumber = 1,
          tool = "open_settings_panel",
          args = mapOf("panel" to "wifi"),
          screenAfter = settingsScreen(),
          requiresApproval = true,
        ),
      ),
    )

    val definition = WorkflowTraceSerializer.toDefinition(trace)

    assertEquals("open-wi-fi-settings", definition.id)
    assertEquals(1, definition.steps.size)
    assertEquals("open_settings_panel", definition.steps.first().tool)
    assertEquals("com.android.settings", definition.steps.first().expectedState?.packageName)
    assertEquals(true, definition.steps.first().policy?.requiresApproval)
  }

  @Test
  fun parameterizesArgsWhenValueAppearsInTask() {
    val trace = WorkflowTrace(
      id = "run-2",
      title = "Tap Bluetooth",
      task = "tap Bluetooth settings",
      steps = listOf(
        WorkflowTraceStep(
          sequenceNumber = 1,
          tool = "tap",
          args = mapOf("text" to "Bluetooth"),
          screenAfter = settingsScreen(windowTitle = "Bluetooth"),
        ),
      ),
    )

    val definition = WorkflowTraceSerializer.toDefinition(trace)
    assertEquals(1, definition.parameters.size)
    assertEquals("{bluetooth}", definition.steps.first().args["text"])
  }

  @Test
  fun slugifyNormalizesTitles() {
    assertEquals("open-wi-fi-settings", WorkflowTraceSerializer.slugify("Open Wi-Fi Settings!"))
  }

  private fun settingsScreen(windowTitle: String = "Wi-Fi"): ScreenContext {
    return ScreenContext(
      packageName = "com.android.settings",
      windowTitle = windowTitle,
      nodes = listOf(
        ScreenNode(
          role = NodeRole.TEXT,
          text = ScreenText.of("Wi-Fi"),
          bounds = NodeBounds(0, 0, 100, 40),
        ),
        ScreenNode(
          role = NodeRole.BUTTON,
          text = ScreenText.of("Network"),
          clickable = true,
          bounds = NodeBounds(0, 50, 100, 90),
        ),
      ),
    )
  }
}

class WorkflowParametersTest {
  @Test
  fun recognizesPlaceholders() {
    assertTrue(WorkflowParameters.isPlaceholder("{contact_name}"))
    assertFalse(WorkflowParameters.isPlaceholder("contact_name"))
    assertEquals("contact_name", WorkflowParameters.placeholderName("{contact_name}"))
  }

  @Test
  fun substitutesParameterValues() {
    val resolved = WorkflowParameters.substitute(
      mapOf("text" to "{name}"),
      mapOf("name" to "Alice"),
    )
    assertEquals("Alice", resolved["text"])
  }
}
