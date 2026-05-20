package dev.touchpilot.app.screen.ocr

import android.accessibilityservice.AccessibilityService
import android.app.UiAutomation
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live behavior proof for issue #42. Runs on a real Android device/emulator.
 *
 * The test:
 * 1. Sends the device to the launcher via the UiAutomation global "home" action.
 * 2. Walks the live `AccessibilityNodeInfo` root with the same counting rules the
 *    new `ContextQualityDetector` expects.
 * 3. Classifies and prints the result so the gradle `connectedDebugAndroidTest`
 *    log captures real on-device evidence for the PR.
 *
 * The test does not depend on TouchPilot's own `AccessibilityService` being
 * enabled — `UiAutomation` is granted the same view of the tree by the
 * instrumentation framework.
 */
@RunWith(AndroidJUnit4::class)
class OcrFallbackBoundaryLiveTest {
    private val tag = "OcrFallbackBoundary"

    @Test
    fun launcherSignalsClassifyAsStrongOrWeakNotEmpty() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiAutomation: UiAutomation = instrumentation.uiAutomation

        uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Thread.sleep(1_500L)

        val root: AccessibilityNodeInfo? = uiAutomation.rootInActiveWindow
        assertNotNull("rootInActiveWindow was null on the live device", root)

        val signals = countSignals(root!!)
        val packageName = root.packageName?.toString()
        val annotated = signals.copy(packageName = packageName)

        val detector = ContextQualityDetector()
        val quality = detector.classify(annotated)

        Log.i(tag, "live signals: $annotated")
        Log.i(tag, "live quality: $quality")

        when (quality) {
            ContextQuality.Strong -> {
                Log.i(tag, "Strong context — summary/suggestions would run directly.")
            }

            is ContextQuality.Weak -> {
                val fallback = NoOpOcrFallback().attempt(
                    OcrRequest(reason = quality.reason, packageName = packageName)
                )
                val response = WeakContextResponse.forWeak(quality, fallback)
                Log.i(tag, "Weak context (${quality.reason}) — fallback=$fallback")
                Log.i(tag, "agent response: $response")
            }

            ContextQuality.Empty -> {
                Log.i(tag, "agent response: ${WeakContextResponse.forEmpty()}")
            }
        }

        // The launcher always renders something; an Empty classification would
        // indicate the live tree was not observable at all, which is the
        // failure mode we are guarding against.
        assertTrue(
            "Live launcher screen classified as Empty: $annotated",
            quality !is ContextQuality.Empty,
        )
    }

    private fun countSignals(root: AccessibilityNodeInfo): ObservedScreenSignals {
        var total = 0
        var visibleText = 0
        var clickable = 0
        var inputs = 0
        var maxDepth = 0

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            total += 1
            if (depth > maxDepth) maxDepth = depth

            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            if (text.isNotBlank() || desc.isNotBlank()) visibleText += 1

            if (node.isClickable) clickable += 1

            val className = node.className?.toString().orEmpty()
            if (
                className.contains("EditText", ignoreCase = true) ||
                className.contains("AutoCompleteTextView", ignoreCase = true)
            ) {
                inputs += 1
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                walk(child, depth + 1)
            }
        }

        walk(root, depth = 0)

        return ObservedScreenSignals(
            totalNodeCount = total,
            visibleTextCount = visibleText,
            clickableNodeCount = clickable,
            inputFieldCount = inputs,
            maxTreeDepth = maxDepth,
        )
    }
}
