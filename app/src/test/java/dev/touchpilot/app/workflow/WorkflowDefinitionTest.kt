package dev.touchpilot.app.workflow

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
  fun convertsCapturedTraceToDefinition() {
    val trace = WorkflowTrace(
      runId = "run-1",
      task = "open Wi-Fi settings",
      capturedAtMillis = 2_000L,
      steps = listOf(
        WorkflowTraceStep(
          index = 1,
          tool = "open_settings_panel",
          args = mapOf("panel" to "wifi"),
          source = "local_router",
          succeeded = true,
          verification = WorkflowTraceVerification(status = "passed", reason = "Wi-Fi visible"),
          requiresApproval = true,
        ),
      ),
      screenSignals = emptyList(),
      skillId = "settings",
      allowedTools = listOf("open_settings_panel"),
    )

    val definition = WorkflowTraceSerializer.toDefinition(trace)

    assertEquals("open-wi-fi-settings", definition.id)
    assertEquals("open Wi-Fi settings", definition.title)
    assertEquals(1, definition.steps.size)
    assertEquals("open_settings_panel", definition.steps.first().tool)
    assertEquals(true, definition.steps.first().policy?.requiresApproval)
    assertEquals("settings", definition.skillScope?.skillId)
    assertTrue(definition.steps.first().expectedState?.screenTextContains?.contains("Wi-Fi visible") == true)
  }

  @Test
  fun parameterizesArgsWhenValueAppearsInTask() {
    val trace = WorkflowTrace(
      runId = "run-2",
      task = "tap Bluetooth settings",
      capturedAtMillis = 2_000L,
      steps = listOf(
        WorkflowTraceStep(
          index = 1,
          tool = "tap",
          args = mapOf("text" to "Bluetooth"),
          succeeded = true,
          verification = null,
        ),
      ),
      screenSignals = emptyList(),
    )

    val definition = WorkflowTraceSerializer.toDefinition(trace)
    assertEquals(1, definition.parameters.size)
    assertEquals("{bluetooth}", definition.steps.first().args["text"])
    assertEquals(
      listOf("Bluetooth"),
      definition.steps.first().expectedState?.elementPresent?.map { it.text },
    )
  }

  @Test
  fun slugifyNormalizesTitles() {
    assertEquals("open-wi-fi-settings", WorkflowTraceSerializer.slugify("Open Wi-Fi Settings!"))
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
