package dev.touchpilot.app.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkflowLibraryTest {
    @Test
    fun seedsRenamesRecordsAndDeletesLocalWorkflows() {
        val root = createTempDir(prefix = "workflow-library-test")
        root.deleteOnExit()

        val library = WorkflowLibrary(
            rootDir = root,
            seedDefinitions = listOf(sampleWorkflow())
        )

        val seeded = library.all()
        assertEquals(1, seeded.size)
        assertEquals("Open Wi-Fi Settings", seeded.single().definition.title)
        assertTrue(seeded.single().isEditable)

        val renamed = library.rename("open-wifi-settings", "Open Wi-Fi Settings Again")
        assertNotNull(renamed)
        assertEquals("Open Wi-Fi Settings Again", renamed.definition.title)

        library.recordRun(
            workflowId = "open-wifi-settings",
            status = WorkflowRunStatus.SUCCEEDED,
            message = "Replay finished successfully."
        )
        val withState = library.find("open-wifi-settings")
        assertNotNull(withState)
        assertEquals(WorkflowRunStatus.SUCCEEDED, withState.lastRun?.status)
        assertEquals("Replay finished successfully.", withState.lastRun?.message)

        assertTrue(library.delete("open-wifi-settings"))
        assertTrue(library.all().isEmpty())
        assertFalse(library.delete("open-wifi-settings"))

        val reopened = WorkflowLibrary(
            rootDir = root,
            seedDefinitions = listOf(sampleWorkflow())
        )
        assertTrue(reopened.all().isEmpty())
    }

    @Test
    fun uniqueIdAppendsSuffixOnlyWhenIdIsTaken() {
        val root = createTempDir(prefix = "workflow-library-unique-id-test")
        root.deleteOnExit()
        val library = WorkflowLibrary(rootDir = root)

        assertEquals("open-wifi-settings", library.uniqueId("open-wifi-settings"))

        library.save(sampleWorkflow())
        assertEquals("open-wifi-settings-2", library.uniqueId("open-wifi-settings"))

        library.save(sampleWorkflow().copy(id = "open-wifi-settings-2"))
        assertEquals("open-wifi-settings-3", library.uniqueId("open-wifi-settings"))
    }

    private fun sampleWorkflow(): WorkflowDefinition {
        return WorkflowDefinition(
            id = "open-wifi-settings",
            title = "Open Wi-Fi Settings",
            description = "Open the Wi-Fi settings panel.",
            steps = listOf(
                WorkflowStep(
                    id = "open-panel",
                    tool = "open_settings_panel",
                    args = mapOf("panel" to "wifi"),
                    description = "Open the Wi-Fi settings panel."
                )
            )
        )
    }
}
