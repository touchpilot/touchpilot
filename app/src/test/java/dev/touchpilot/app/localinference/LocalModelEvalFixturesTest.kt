package dev.touchpilot.app.localinference

import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the committed local model evaluation fixtures (issue #263): every role
 * file must parse and follow the documented format
 * (app/src/test/resources/local-model-eval/README.md). This checks fixture
 * integrity only; it does not run a model or benchmark behavior.
 */
class LocalModelEvalFixturesTest {
    private val roleFiles = mapOf(
        "intent_classification" to "intent-classification.json",
        "tool_selection" to "tool-selection.json",
        "argument_extraction" to "argument-extraction.json",
        "screen_summary" to "screen-summary.json",
    )

    @Test
    fun everyRoleFixtureIsWellFormed() {
        roleFiles.forEach { (role, fileName) ->
            val json = JSONObject(readFixture(fileName))

            assertEquals(1, json.optInt("version", 0), "unexpected version in $fileName")
            assertEquals(role, json.optString("role"), "role mismatch in $fileName")
            assertTrue(json.optString("description").isNotBlank(), "missing description in $fileName")

            val cases = json.optJSONArray("cases")
            assertTrue(cases != null && cases.length() > 0, "no cases in $fileName")

            val ids = mutableSetOf<String>()
            for (index in 0 until cases!!.length()) {
                val case = cases.getJSONObject(index)
                val id = case.optString("id")
                assertTrue(id.isNotBlank(), "blank id in $fileName[$index]")
                assertTrue(ids.add(id), "duplicate id '$id' in $fileName")
                assertTrue(
                    case.optString("description").isNotBlank(),
                    "blank description for '$id' in $fileName",
                )
                assertTrue(case.has("input"), "missing input for '$id' in $fileName")
                assertTrue(case.has("expected"), "missing expected for '$id' in $fileName")
            }
        }
    }

    @Test
    fun coversEveryDocumentedRole() {
        // The canonical set must cover all four roles owned by this directory.
        assertEquals(
            setOf("intent_classification", "tool_selection", "argument_extraction", "screen_summary"),
            roleFiles.keys,
        )
    }

    private fun readFixture(fileName: String): String {
        val candidates = listOf(
            File("src/test/resources/$FIXTURE_DIR/$fileName"),
            File("app/src/test/resources/$FIXTURE_DIR/$fileName"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing fixture: $FIXTURE_DIR/$fileName")
        return file.readText()
    }

    private companion object {
        const val FIXTURE_DIR = "local-model-eval"
    }
}
