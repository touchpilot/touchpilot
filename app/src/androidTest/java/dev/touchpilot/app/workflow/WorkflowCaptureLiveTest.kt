package dev.touchpilot.app.workflow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.touchpilot.app.MainActivity
import dev.touchpilot.app.R
import dev.touchpilot.app.navigation.AppSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live behavior proof for issue #381.
 *
 * Seeds a captured workflow trace directly into the trace store (mirroring
 * what AgentRunController.captureWorkflowTrace does after a real successful
 * run), opens the workflow editor for that run, and taps Save. Verifies the
 * saved definition lands in the local workflow library so it appears on the
 * Product screen and replays like any other workflow.
 */
@RunWith(AndroidJUnit4::class)
class WorkflowCaptureLiveTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun capturedRunCanBeSavedAsWorkflowFromEditor() {
        val activity = launchMainActivity()
        val trace = sampleTrace()

        seedTrace(activity, trace)
        openEditor(activity, trace.runId)

        val contentRoot = activity.privateField<LinearLayout>("contentRoot")
        val saveButton = contentRoot.findViewById<View>(R.id.workflow_editor_save_button)
        assertNotNull("Save button was not rendered by the workflow editor", saveButton)

        instrumentation.runOnMainSync { saveButton.performClick() }
        instrumentation.waitForIdleSync()

        val library = activity.privateField<WorkflowLibrary>("workflowLibrary")
        val saved = library.all().firstOrNull { it.definition.title == trace.task }
        assertNotNull("Expected the captured run to be saved as a workflow", saved)
        assertEquals(1, saved!!.stepCount)
    }

    @After
    fun returnHome() {
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    private fun launchMainActivity(): MainActivity {
        val context = instrumentation.targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return instrumentation.startActivitySync(intent) as MainActivity
    }

    private fun seedTrace(activity: MainActivity, trace: WorkflowTrace) {
        val store = activity.privateField<WorkflowTraceStore>("workflowTraceStore")
        store.record(trace)
    }

    private fun openEditor(activity: MainActivity, runId: String) {
        instrumentation.runOnMainSync {
            activity.invokePrivate("showSection", AppSection.CHAT)
            activity.invokePrivate("openWorkflowEditor", runId)
        }
        instrumentation.waitForIdleSync()
    }

    private fun sampleTrace(): WorkflowTrace {
        return WorkflowTrace(
            runId = "run-capture-381",
            task = "Open Wi-Fi settings",
            capturedAtMillis = 1_000L,
            steps = listOf(
                WorkflowTraceStep(
                    index = 1,
                    tool = "open_settings_panel",
                    args = mapOf("panel" to "wifi"),
                    source = "local_router",
                    succeeded = true,
                    verification = WorkflowTraceVerification(status = "verified", reason = "Wi-Fi panel visible"),
                    requiresApproval = true,
                ),
            ),
            screenSignals = emptyList(),
        )
    }

    private inline fun <reified T> Any.privateField(name: String): T {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this) as T
    }

    private fun Any.invokePrivate(name: String, vararg args: Any?) {
        val types = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
        val method = javaClass.getDeclaredMethod(name, *types)
        method.isAccessible = true
        method.invoke(this, *args)
    }
}
