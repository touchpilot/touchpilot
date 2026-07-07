package dev.touchpilot.app.tools

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Live runtime proof for the `drag_and_drop` tool: a press-hold-move gesture
 * dispatched through [dev.touchpilot.app.androidcontrol.TouchPilotAccessibilityService.drag]
 * must be accepted and completed by the real Android gesture pipeline on a
 * device/emulator, and the `drag_and_drop` tool must route a coordinate-mode
 * request all the way through [AndroidToolExecutor] without validation, policy,
 * or dispatch errors.
 *
 * Run on an emulator/device with:
 * `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.touchpilot.app.tools.DragAndDropLiveTest`
 */
@RunWith(AndroidJUnit4::class)
class DragAndDropLiveTest {
    private val tag = "DragAndDropLive"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val serviceComponent =
        "dev.touchpilot.app/dev.touchpilot.app.androidcontrol.TouchPilotAccessibilityService"

    @Before
    fun enableAccessibilityService() {
        runShell("settings put secure accessibility_enabled 1")
        runShell("settings put secure enabled_accessibility_services $serviceComponent")
        waitForServiceConnected(timeoutMs = 15_000L)
    }

    @After
    fun returnHome() {
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    @Test
    fun dragGestureIsDispatchedAndToolRoutesCoordinateMode() {
        val context = instrumentation.targetContext
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        Thread.sleep(2_500L)

        assertTrue(
            "TouchPilot accessibility service did not connect",
            AccessibilityBridge.isConnected(),
        )

        val window = AccessibilityBridge.activeWindowBounds()
        assertTrue("No active window bounds available for the drag", window != null && !window.isEmpty)
        requireNotNull(window)

        // A vertical drag along the center axis, kept well inside the window so
        // both endpoints are valid gesture coordinates.
        val centerX = (window.left + window.right) / 2
        val insetY = ((window.bottom - window.top) * 0.25f).toInt()
        val startY = window.top + insetY
        val endY = window.bottom - insetY

        val dispatched = AccessibilityBridge.drag(
            startX = centerX,
            startY = startY,
            endX = centerX,
            endY = endY,
            holdMs = 550L,
            moveMs = 400L,
        )
        assertTrue("drag gesture was not accepted/completed by the platform", dispatched)

        // Route the same drag through the full tool path (validation → policy →
        // approval → dispatch → verification). A MEDIUM-risk gesture asks for
        // approval, so we supply an auto-approving provider. We assert the tool
        // was not rejected before dispatch; the screen-change verdict depends on
        // the surface, so it is logged rather than asserted.
        val executor = AndroidToolExecutor(
            context = context,
            approvalProvider = { true },
        )
        val result = executor.execute(
            name = "drag_and_drop",
            args = mapOf(
                "start_x" to centerX.toString(),
                "start_y" to startY.toString(),
                "end_x" to centerX.toString(),
                "end_y" to endY.toString(),
            ),
        )
        assertTrue(
            "drag_and_drop was rejected before dispatch: ${result.message}",
            !result.message.contains("Unknown tool", ignoreCase = true) &&
                !result.message.contains("outside the screen bounds", ignoreCase = true) &&
                !result.message.contains("Unable to perform", ignoreCase = true),
        )

        Log.i(
            tag,
            "runtime proof: dragDispatched=$dispatched toolOk=${result.ok} " +
                "message=\"${result.message}\" screenChanged=${result.data["screen_changed"]} " +
                "start=$centerX,$startY end=$centerX,$endY",
        )
    }

    private fun waitForServiceConnected(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (AccessibilityBridge.isConnected()) return
            Thread.sleep(250L)
        }
    }

    private fun runShell(command: String) {
        val automation = instrumentation.uiAutomation
        val descriptor: ParcelFileDescriptor = automation.executeShellCommand(command)
        BufferedReader(InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(descriptor))).use { reader ->
            while (reader.readLine() != null) {
                // Drain shell output so the command completes cleanly.
            }
        }
    }
}
