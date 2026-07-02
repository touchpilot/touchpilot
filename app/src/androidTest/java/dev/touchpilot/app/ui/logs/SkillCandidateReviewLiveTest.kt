package dev.touchpilot.app.ui.logs

import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.touchpilot.app.MainActivity
import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.agent.AgentRunResult
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.AgentStepFactory
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.memory.SkillRegistry
import dev.touchpilot.app.workflow.WorkflowTraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live proof for the skill candidate review/edit/save flow in PR #400.
 *
 * The test seeds a completed run, opens the generated skill candidate from the
 * run detail view, edits the title in the dialog, saves the candidate, and then
 * verifies the activity reloaded its skill registry with the edited title.
 */
@RunWith(AndroidJUnit4::class)
class SkillCandidateReviewLiveTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun reviewEditSaveReloadsSkillRegistry() {
        val activity = launchMainActivity()
        val timestamp = System.currentTimeMillis()
        val originalTitle = "Live review save reload $timestamp"
        val editedTitle = "Edited live review save reload $timestamp"
        val runId = "run-review-$timestamp"
        val record = completedRun(
            runId = runId,
            task = originalTitle,
            startedAtMillis = timestamp - 1_000L,
            completedAtMillis = timestamp,
        )

        seedRun(activity, record)
        openRunDetail(activity, runId)

        val reviewButton = findViewByText(
            accessibilityRoot(),
            "Review Skill Candidate",
        )
        assertNotNull("Review Skill Candidate button was not rendered", reviewButton)

        instrumentation.runOnMainSync {
            reviewButton!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        instrumentation.waitForIdleSync()

        val titleField = findNodeByHint("Title")
        assertNotNull("Title field was not shown in the review dialog", titleField)

        setText(titleField!!, editedTitle)
        instrumentation.waitForIdleSync()

        val saveButton = findViewByText(accessibilityRoot(), "Save skill")
        assertNotNull("Save skill button was not shown in the review dialog", saveButton)

        instrumentation.runOnMainSync {
            saveButton!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        instrumentation.waitForIdleSync()

        val registry = activity.privateField<SkillRegistry>("skillRegistry")
        val expectedId = WorkflowTraceSerializer.slugify(originalTitle)
        assertEquals(
            "The edited skill title was not reloaded into the registry",
            editedTitle,
            registry.findById(expectedId)?.title,
        )
        assertTrue(
            "Registry did not contain the edited candidate",
            registry.allSkills().any { it.id == expectedId && it.title == editedTitle }
        )
    }

    private fun launchMainActivity(): MainActivity {
        val context = instrumentation.targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return instrumentation.startActivitySync(intent) as MainActivity
    }

    private fun seedRun(activity: MainActivity, record: AgentRunRecord) {
        val controller = activity.privateField<Any>("agentRunController")
        val historyField = controller.javaClass.getDeclaredField("mutableRunHistory")
        historyField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val history = historyField.get(controller) as MutableList<AgentRunRecord>
        history += record
    }

    private fun openRunDetail(activity: MainActivity, runId: String) {
        activity.invokePrivate("openRunDetail", runId)
        instrumentation.waitForIdleSync()
    }

    private fun completedRun(
        runId: String,
        task: String,
        startedAtMillis: Long,
        completedAtMillis: Long,
    ): AgentRunRecord {
        val steps = listOf<AgentStep>(
            AgentStepFactory.act(
                sequenceNumber = 1,
                tool = "tap",
                args = mapOf("text" to "Go"),
                source = "local_router",
                inputSummary = "tap Go",
                outputSummary = "ok",
                startedAtMillis = startedAtMillis,
                endedAtMillis = startedAtMillis + 200L,
            ),
            AgentStepFactory.stop(
                sequenceNumber = 2,
                reason = AgentStepStopReason.COMPLETED,
                outputSummary = "done",
                startedAtMillis = startedAtMillis + 201L,
                endedAtMillis = completedAtMillis,
            ),
        )
        return AgentRunRecord(
            id = runId,
            task = task,
            startedAtMillis = startedAtMillis,
            completedAtMillis = completedAtMillis,
            result = AgentRunResult(
                transcript = "completed",
                finalAnswer = null,
                events = emptyList(),
                steps = steps,
                stopReason = AgentStepStopReason.COMPLETED,
                stopMessage = "completed",
            ),
            errorMessage = null,
            screenRecords = emptyList(),
        )
    }

    private fun findViewByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val nodeText = root.text?.toString().orEmpty()
        val nodeDescription = root.contentDescription?.toString().orEmpty()
        if (nodeText == text || nodeDescription == text) {
            return root
        }
        for (index in 0 until root.childCount) {
            val child = root.getChild(index)
            val found = findViewByText(child, text)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByHint(hint: String): AccessibilityNodeInfo? {
        return findNodeByHint(accessibilityRoot(), hint)
    }

    private fun findNodeByHint(root: AccessibilityNodeInfo?, hint: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val nodeHint = root.hintText?.toString().orEmpty()
        val nodeDescription = root.contentDescription?.toString().orEmpty()
        if (nodeHint == hint || nodeDescription == hint) {
            return root
        }
        for (index in 0 until root.childCount) {
            val child = root.getChild(index)
            val found = findNodeByHint(child, hint)
            if (found != null) return found
        }
        return null
    }

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        instrumentation.runOnMainSync {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }

    private fun accessibilityRoot(): AccessibilityNodeInfo? {
        return instrumentation.uiAutomation.rootInActiveWindow
    }

    private inline fun <reified T> Any.privateField(name: String): T {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as T
    }

    private fun Any.invokePrivate(name: String, vararg args: Any?) {
        val method = javaClass.getDeclaredMethod(
            name,
            *args.map { it?.javaClass ?: Any::class.java }.toTypedArray(),
        )
        method.isAccessible = true
        method.invoke(this, *args)
    }
}
