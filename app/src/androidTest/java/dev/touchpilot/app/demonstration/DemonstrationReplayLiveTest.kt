package dev.touchpilot.app.demonstration

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.touchpilot.app.MainActivity
import dev.touchpilot.app.navigation.AppSection
import dev.touchpilot.app.runtime.AgentRunController
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import dev.touchpilot.app.demonstration.storage.DemonstrationStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live behavior proof for issue #401.
 *
 * Seeds a completed demonstration into the activity, renders Settings, verifies
 * the replay card is visible, then taps it and checks that the controller enters
 * the workflow replay run state.
 */
@RunWith(AndroidJUnit4::class)
class DemonstrationReplayLiveTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun completedDemonstrationAppearsInSettingsAndStartsReplay() {
        val activity = launchMainActivity()
        val session = completedSession()

        seedSession(activity, session)
        renderSettings(activity)

        val contentRoot = activity.privateField<LinearLayout>("contentRoot")
        val replayCard = findClickableCardWithTitle(contentRoot, "Open Settings")
        assertNotNull("Replay card was not rendered in Settings", replayCard)
        assertTrue("Replay card should be clickable", replayCard!!.isClickable)

        instrumentation.runOnMainSync {
            replayCard.performClick()
        }
        instrumentation.waitForIdleSync()

        val controller = activity.privateField<AgentRunController>("agentRunController")
        assertEquals(
            "Expected tapping the replay card to enter the replay run state",
            dev.touchpilot.app.agent.AgentRunState.RUNNING,
            controller.runState,
        )
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

    private fun seedSession(activity: MainActivity, session: DemonstrationSession) {
        val manager = activity.privateField<DemonstrationSessionManager>("demonstrationManager")
        val store = manager.privateField<DemonstrationStore>("store")
        store.record(session)
    }

    private fun renderSettings(activity: MainActivity) {
        instrumentation.runOnMainSync {
            activity.invokePrivate("showSection", AppSection.SETTINGS)
        }
        instrumentation.waitForIdleSync()
    }

    private fun completedSession(): DemonstrationSession {
        val screen = ScreenContext(
            packageName = "com.test",
            nodes = listOf(
                ScreenNode(
                    nodeId = "n1",
                    role = NodeRole.BUTTON,
                    text = ScreenText.of("Go"),
                    clickable = true,
                ),
            ),
        )
        val before = DemonstrationScreenFrame.capture(
            sequenceNumber = 1,
            phase = DemonstrationCapturePhase.BEFORE_ACTION,
            timestampMillis = 100L,
            context = screen,
        )
        val after = DemonstrationScreenFrame.capture(
            sequenceNumber = 2,
            phase = DemonstrationCapturePhase.AFTER_ACTION,
            timestampMillis = 200L,
            context = screen,
        )
        val step = DemonstrationStep(
            index = 1,
            action = DemonstrationToolAction(
                tool = "tap",
                args = mapOf("text" to "Go"),
                source = "local_router",
                succeeded = true,
                message = "ok",
            ),
            beforeFrame = before,
            afterFrame = after,
        )
        return DemonstrationSession.create(
            sessionId = "demo-replay-401",
            runId = "run-replay-401",
            task = "Open Settings",
            startedAtMillis = 100L,
        )
            .copy(steps = listOf(step))
            .withCompleted(DemonstrationStatus.COMPLETED, 300L)
    }

    private fun findClickableCardWithTitle(root: View, title: String): View? {
        if (root is TextView && root.text?.toString() == title) {
            var parent = root.parent
            while (parent is View) {
                if (parent.isClickable) return parent
                parent = parent.parent
            }
        }

        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                val found = findClickableCardWithTitle(root.getChildAt(index), title)
                if (found != null) return found
            }
        }

        return null
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
