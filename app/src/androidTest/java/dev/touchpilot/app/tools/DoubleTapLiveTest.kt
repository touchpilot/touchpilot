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
 * Live runtime proof for the `double_tap` tool: the two-tap gesture dispatched
 * through [dev.touchpilot.app.androidcontrol.TouchPilotAccessibilityService.doubleTap]
 * must be accepted and completed by the real Android gesture pipeline on a
 * device/emulator, and the `double_tap` tool must route a bounds-mode request
 * all the way through [AndroidToolExecutor] without validation, policy, or
 * dispatch errors.
 *
 * Run on an emulator/device with:
 * `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.touchpilot.app.tools.DoubleTapLiveTest`
 */
@RunWith(AndroidJUnit4::class)
class DoubleTapLiveTest {
    private val tag = "DoubleTapLive"
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
    fun doubleTapGestureIsDispatchedAndToolRoutesBoundsMode() {
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
        assertTrue("No active window bounds available for the double-tap", window != null && !window.isEmpty)
        requireNotNull(window)

        // A small rectangle at the window center; doubleTapByBounds taps its center.
        val cx = (window.left + window.right) / 2
        val cy = (window.top + window.bottom) / 2
        val boundsArg = "${cx - 4},${cy - 4},${cx + 4},${cy + 4}"

        val dispatched = AccessibilityBridge.doubleTapByBounds(boundsArg)
        assertTrue("double-tap gesture was not accepted/completed by the platform", dispatched)

        // Route the same double-tap through the full tool path (validation →
        // policy → approval → dispatch → verification) with an auto-approving
        // provider. Assert it is not rejected before dispatch; the screen-change
        // verdict depends on the surface, so it is logged rather than asserted.
        val executor = AndroidToolExecutor(
            context = context,
            approvalProvider = { true },
        )
        val result = executor.execute(
            name = "double_tap",
            args = mapOf("bounds" to boundsArg),
        )
        assertTrue(
            "double_tap was rejected before dispatch: ${result.message}",
            !result.message.contains("Unknown tool", ignoreCase = true) &&
                !result.message.contains("requires exactly one selector", ignoreCase = true) &&
                !result.message.contains("Unable to perform", ignoreCase = true),
        )

        Log.i(
            tag,
            "runtime proof: doubleTapDispatched=$dispatched toolOk=${result.ok} " +
                "message=\"${result.message}\" screenChanged=${result.data["screen_changed"]} center=$cx,$cy",
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
