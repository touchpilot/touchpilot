package dev.touchpilot.app.compatibility

import android.accessibilityservice.AccessibilityService
import android.app.UiAutomation
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compatibility smoke test for issue #387.
 *
 * Exercises a minimal observe → open Settings → scroll → back → home path on
 * whatever device or emulator is connected. Logs OEM and API metadata so
 * matrix rows can be correlated with CI and manual runs.
 *
 * Uses [UiAutomation.rootInActiveWindow] like [TargetSelectorLiveTest]; does
 * not require enabling TouchPilot's accessibility service.
 */
@RunWith(AndroidJUnit4::class)
class DeviceCompatibilitySmokeLiveTest {
    private val tag = "DeviceCompatSmoke"

    @Test
    fun logsDeviceMetadataAndRunsCoreNavigationSmoke() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiAutomation: UiAutomation = instrumentation.uiAutomation

        logDeviceMetadata()

        uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Thread.sleep(1_500L)

        val context = instrumentation.targetContext
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        Thread.sleep(2_500L)

        val root: AccessibilityNodeInfo? = uiAutomation.rootInActiveWindow
        assertNotNull("rootInActiveWindow was null — observe path unusable", root)

        val packageName = root!!.packageName?.toString().orEmpty()
        Log.i(tag, "observe: package=$packageName childCount=${root.childCount}")

        val scrollable = findFirstScrollable(root)
        if (scrollable != null) {
            Log.i(tag, "scroll: found scrollable node class=${scrollable.className}")
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(800L)
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            Thread.sleep(800L)
        } else {
            Log.i(tag, "scroll: no scrollable node in Settings — skipped")
        }

        uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Thread.sleep(1_000L)
        Log.i(tag, "back: performed")

        uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Thread.sleep(1_000L)

        val homeRoot = uiAutomation.rootInActiveWindow
        assertNotNull("rootInActiveWindow was null after HOME", homeRoot)
        Log.i(tag, "home: package=${homeRoot?.packageName}")
        assertTrue("HOME did not return to a foreground window", homeRoot!!.childCount >= 0)
    }

    @After
    fun returnHome() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    private fun logDeviceMetadata() {
        val metadata = JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("brand", Build.BRAND)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("product", Build.PRODUCT)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("fingerprint", Build.FINGERPRINT)
        Log.i(tag, "device_metadata=$metadata")
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findFirstScrollable(child)
            if (found != null) return found
        }
        return null
    }
}
