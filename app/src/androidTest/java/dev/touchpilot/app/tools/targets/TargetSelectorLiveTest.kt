package dev.touchpilot.app.tools.targets

import android.accessibilityservice.AccessibilityService
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live behavior proof for issue #76. Runs on a real Android device/emulator.
 *
 * The test:
 * 1. Sends the device to the launcher to start from a known state.
 * 2. Launches the Android Settings app via Intent so the recorded run has a
 *    visible app transition.
 * 3. Walks the live `AccessibilityNodeInfo` tree and builds [TargetSelector]
 *    instances directly from the live nodes — the same conversion path the
 *    resolver in issue #77 will use later.
 * 4. Demonstrates two surfaces the new model adds:
 *      - **Real-tree selectors with provenance**: `toRedactedJson()` for a
 *        handful of top-level rows, showing nodeId / bounds / viewId /
 *        role / source = OBSERVATION.
 *      - **Sensitivity propagation**: a synthesized "Enter your password"
 *        label is wrapped through [SelectorText.of] and serialized so the
 *        recording shows `[REDACTED]` in the display-safe view of the
 *        selector, while non-sensitive labels round-trip unchanged.
 * 5. Returns to HOME so subsequent runs start clean.
 *
 * `UiAutomation.rootInActiveWindow` grants the same view of the tree the
 * AccessibilityService would see, so this test does not require enabling
 * TouchPilot's own service.
 */
@RunWith(AndroidJUnit4::class)
class TargetSelectorLiveTest {
    private val tag = "TargetSelectorLive"

    @Test
    fun buildsSelectorsFromLiveSettingsTreeAndRedactsSensitive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiAutomation: UiAutomation = instrumentation.uiAutomation

        uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Thread.sleep(1_500L)

        val context = instrumentation.targetContext
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        Thread.sleep(2_500L)

        val root: AccessibilityNodeInfo? = uiAutomation.rootInActiveWindow
        assertNotNull("rootInActiveWindow was null on the live device", root)

        val packageName = root!!.packageName?.toString()
        Log.i(tag, "package=$packageName")

        val selectors = buildLiveSelectors(root, packageName)
        Log.i(tag, "live selectors gathered: ${selectors.size}")

        // Log the first few selectors as redacted JSON so the recording
        // captures real on-device target data for the PR body.
        for (selector in selectors.take(5)) {
            Log.i(tag, "selector: " + selector.toJson(redacted = true).toString())
        }

        // Demonstrate sensitivity propagation. The synthesized label is built
        // through the same SelectorText.of() factory production code uses,
        // so the JSON below is exactly what would land in a log if a model
        // proposed a selector that wraps a password label.
        val sensitiveSelector = TargetSelector(
            text = SelectorText.of("Enter your password"),
            nodeId = "synthetic.password.field",
            packageName = packageName,
            source = SelectorSource.MODEL,
        )
        Log.i(tag, "sensitive selector (redacted): " + sensitiveSelector.toRedactedJson())
        Log.i(tag, "sensitive selector containsSensitiveText=${sensitiveSelector.containsSensitiveText}")

        // Sanity: at least one observation-sourced selector must come back
        // as valid; an empty list means the live tree was not usable.
        assertTrue(
            "No valid selectors built from live Settings tree",
            selectors.any { it.isValid() },
        )
        assertTrue(
            "Sensitive selector did not propagate the sensitive flag",
            sensitiveSelector.containsSensitiveText,
        )
    }

    @After
    fun returnHome() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    /**
     * Walks the live tree and emits one [TargetSelector] per node that has at
     * least one identifying dimension (text, contentDescription, viewId, or
     * non-empty bounds). Each selector's source is recorded as
     * [SelectorSource.OBSERVATION] to mirror what the future resolver will do.
     */
    private fun buildLiveSelectors(
        root: AccessibilityNodeInfo,
        packageName: String?,
    ): List<TargetSelector> {
        val out = mutableListOf<TargetSelector>()
        forEachNode(root) { node ->
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val viewId = node.viewIdResourceName
            val rect = Rect().also { node.getBoundsInScreen(it) }
            val bounds = if (rect.isEmpty) null else TargetBounds(
                left = rect.left, top = rect.top, right = rect.right, bottom = rect.bottom,
            )
            if (text.isEmpty() && desc.isEmpty() && viewId.isNullOrBlank() && bounds == null) {
                return@forEachNode
            }
            out += TargetSelector(
                text = text.takeIf { it.isNotEmpty() }?.let { SelectorText.of(it) },
                contentDescription = desc.takeIf { it.isNotEmpty() }?.let { SelectorText.of(it) },
                nodeId = null,
                bounds = bounds,
                viewIdResourceName = viewId,
                role = null,
                packageName = packageName,
                windowTitle = node.window?.title?.toString(),
                confidence = null,
                source = SelectorSource.OBSERVATION,
            )
        }
        return out
    }

    private fun forEachNode(
        node: AccessibilityNodeInfo,
        visit: (AccessibilityNodeInfo) -> Unit,
    ) {
        visit(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            forEachNode(child, visit)
        }
    }
}
