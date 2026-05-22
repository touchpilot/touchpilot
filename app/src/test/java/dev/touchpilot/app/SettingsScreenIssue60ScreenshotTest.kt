package dev.touchpilot.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenIssue60ScreenshotTest {
    @Test
    fun capturesSettingsPanelsForIssue60() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val tabs = findViewByType(activity.window.decorView, TabLayout::class.java)
            ?: error("TabLayout not found")

        val settingsIndex = tabs.tabCount - 1
        tabs.getTabAt(settingsIndex)?.select() ?: error("Settings tab missing")
        shadowOf(activity.mainLooper).idle()
        capture(activity, "settings-menu")

        clickLabel(activity.window.decorView, "Skills")
        shadowOf(activity.mainLooper).idle()
        capture(activity, "settings-skills")
        clickLabelContaining(activity.window.decorView, "Back to Settings")
        shadowOf(activity.mainLooper).idle()

        clickLabel(activity.window.decorView, "MCP")
        shadowOf(activity.mainLooper).idle()
        capture(activity, "settings-mcp")
        clickLabelContaining(activity.window.decorView, "Back to Settings")
        shadowOf(activity.mainLooper).idle()

        clickLabel(activity.window.decorView, "API")
        shadowOf(activity.mainLooper).idle()
        capture(activity, "settings-cloud-api")
        clickLabelContaining(activity.window.decorView, "Back to Settings")
        shadowOf(activity.mainLooper).idle()

        clickLabel(activity.window.decorView, "Runtime")
        shadowOf(activity.mainLooper).idle()
        capture(activity, "settings-runtime")
    }

    private fun capture(activity: MainActivity, name: String) {
        val root = activity.window.decorView.rootView
        if (root.width == 0 || root.height == 0) {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2160, View.MeasureSpec.AT_MOST)
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }

        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        root.draw(canvas)

        val outputDir = resolveOutputDir()
        outputDir.mkdirs()
        FileOutputStream(File(outputDir, "$name.png")).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    private fun resolveOutputDir(): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        val repoRoot = if (userDir.name == "app") userDir.parentFile ?: userDir else userDir
        return File(repoRoot, "docs/screenshots/issue-60")
    }

    private fun clickLabel(root: View, label: String) {
        val target = findViewsByType(root, TextView::class.java)
            .firstOrNull { it.text.toString() == label && it.isClickable }
            ?: error("Clickable label '$label' not found")
        target.performClick()
    }

    private fun clickLabelContaining(root: View, label: String) {
        val target = findViewsByType(root, TextView::class.java)
            .firstOrNull { label in it.text.toString() && it.isClickable }
            ?: error("Clickable label containing '$label' not found")
        target.performClick()
    }

    private fun <T : View> findViewByType(root: View, type: Class<T>): T? {
        if (type.isInstance(root)) return type.cast(root)
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            val found = findViewByType(child, type)
            if (found != null) return found
        }
        return null
    }

    private fun <T : View> findViewsByType(root: View, type: Class<T>): List<T> {
        val results = mutableListOf<T>()
        if (type.isInstance(root)) {
            type.cast(root)?.let { results += it }
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                results += findViewsByType(root.getChildAt(index), type)
            }
        }
        return results
    }
}
