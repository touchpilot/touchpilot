package dev.touchpilot.app.androidcontrol

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.touchpilot.app.screen.ScreenContext
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Live runtime proof for PR #412: repeated accessibility tree walks through
 * [TouchPilotAccessibilityService] must not disconnect the service.
 *
 * Run on an emulator/device with:
 * `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.touchpilot.app.androidcontrol.AccessibilityNodeLifecycleLiveTest`
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityNodeLifecycleLiveTest {
    private val tag = "AccessibilityLifecycleLive"
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
    fun repeatedScreenWalksKeepAccessibilityServiceConnected() {
        val context = instrumentation.targetContext
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        Thread.sleep(2_500L)

        assertTrue(
            "TouchPilot accessibility service did not connect",
            AccessibilityBridge.isConnected(),
        )

        repeat(OBSERVATION_CYCLES) { cycle ->
            val snapshot = AccessibilityBridge.observeScreen()
            assertTrue(
                "observeScreen returned empty output on cycle $cycle",
                snapshot.isNotBlank() && !snapshot.contains("not enabled", ignoreCase = true),
            )

            val contextSnapshot = AccessibilityBridge.observeScreenContext()
            assertTrue(
                "observeScreenContext was empty on cycle $cycle",
                contextSnapshot != ScreenContext.Empty,
            )

            AccessibilityBridge.scrollForward()
            AccessibilityBridge.waitForIdle(500L)
            AccessibilityBridge.scrollBackward()
            AccessibilityBridge.waitForIdle(500L)

            assertTrue(
                "Accessibility service disconnected during cycle $cycle",
                AccessibilityBridge.isConnected(),
            )
        }

        val foreground = AccessibilityBridge.getForegroundApp()
        assertTrue(
            "Foreground app info unavailable after stress pass",
            foreground.accessibilityConnected,
        )
        assertNotEquals(
            "Service reported disconnected after $OBSERVATION_CYCLES observation cycles",
            "TouchPilot Control is not enabled.",
            AccessibilityBridge.observeScreen(),
        )

        Log.i(
            tag,
            "runtime proof: $OBSERVATION_CYCLES observe/scroll/wait_for_idle cycles completed; " +
                "serviceConnected=${AccessibilityBridge.isConnected()} foreground=${foreground.packageName}",
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

    private companion object {
        const val OBSERVATION_CYCLES = 100
    }
}
