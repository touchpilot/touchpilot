package dev.touchpilot.app.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkflowParameterSubstitutorTest {
    @Test
    fun substitutesPlaceholdersInArgs() {
        val args = mapOf(
            "target" to "{app_name}",
            "text" to "Hello {user_name}",
        )
        val resolved = WorkflowParameterSubstitutor.substitute(
            args,
            mapOf("app_name" to "Settings", "user_name" to "Alex"),
        )

        assertEquals("Settings", resolved["target"])
        assertEquals("Hello Alex", resolved["text"])
    }

    @Test
    fun leavesUnknownPlaceholdersUntouched() {
        val value = WorkflowParameterSubstitutor.substitute(
            value = "open {missing}",
            parameters = mapOf("known" to "value"),
        )

        assertEquals("open {missing}", value)
    }

    @Test
    fun resolvesDeclaredParametersWithDefaultsAndSuppliedValues() {
        val definition = WorkflowDefinition(
            id = "wf-1",
            title = "Test",
            parameters = listOf(
                WorkflowParameter(name = "app_name", default = "Settings"),
                WorkflowParameter(name = "user_name", required = true),
            ),
            steps = listOf(WorkflowStep(id = "open-app", tool = "open_app")),
        )

        val resolved = WorkflowParameterSubstitutor.resolveParameters(
            definition,
            mapOf("user_name" to "Alex", "extra" to "bonus"),
        )

        assertEquals("Settings", resolved["app_name"])
        assertEquals("Alex", resolved["user_name"])
        assertEquals("bonus", resolved["extra"])
    }

    @Test
    fun failsWhenRequiredParameterIsMissing() {
        val definition = WorkflowDefinition(
            id = "wf-1",
            title = "Test",
            parameters = listOf(WorkflowParameter(name = "user_name", required = true)),
            steps = listOf(WorkflowStep(id = "open-app", tool = "open_app")),
        )

        assertFailsWith<IllegalStateException> {
            WorkflowParameterSubstitutor.resolveParameters(definition, emptyMap())
        }
    }
}
