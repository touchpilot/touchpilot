package dev.touchpilot.app.ui.tools

import android.app.Activity
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import dev.touchpilot.app.R
import dev.touchpilot.app.runtime.ToolExecutionController
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.editText
import dev.touchpilot.app.ui.formLabel
import dev.touchpilot.app.ui.primaryButton
import dev.touchpilot.app.ui.rowButtonParams
import dev.touchpilot.app.ui.secondaryButton

class ToolsScreenRenderer(
    private val activity: Activity,
    private val contentRoot: LinearLayout,
    private val toolExecutionController: ToolExecutionController,
    private val openAccessibilitySettings: () -> Unit,
    private val refreshToolsScreen: () -> Unit,
    private val hideKeyboard: (View) -> Unit,
    private val bindKeyboardScrollSpacer: (View) -> Unit,
    private val getFocusSelectorIndex: () -> Int,
    private val setFocusSelectorIndex: (Int) -> Unit,
    private val getLastFocusInputArgs: () -> Map<String, String>?,
    private val setLastFocusInputArgs: (Map<String, String>?) -> Unit
) {
    fun render() {
        contentRoot.addView(
            activity.secondaryButton("Open Accessibility Settings") {
                openAccessibilitySettings()
            }.apply { id = R.id.open_accessibility_settings_button }
        )

        contentRoot.addView(
            activity.primaryButton("Observe Current Screen") {
                toolExecutionController.executeAndRender("observe_screen", emptyMap())
                refreshToolsScreen()
            }.apply { id = R.id.observe_screen_button }
        )

        contentRoot.addView(
            activity.secondaryButton("Observe Screen Context") {
                toolExecutionController.executeAndRender("observe_screen_context", emptyMap())
                refreshToolsScreen()
            }.apply { id = R.id.observe_screen_context_button }
        )

        contentRoot.addView(
            activity.secondaryButton("Get Foreground App") {
                toolExecutionController.executeAndRender("get_foreground_app", emptyMap())
                refreshToolsScreen()
            }.apply { id = R.id.get_foreground_app_button }
        )

        val appInput = activity.editText("App package or launcher label").apply { id = R.id.open_app_input }
        contentRoot.addView(appInput)
        contentRoot.addView(
            activity.secondaryButton("Open App") {
                hideKeyboard(appInput)
                toolExecutionController.executeAndRender("open_app", mapOf("target" to appInput.text.toString()))
                refreshToolsScreen()
            }.apply { id = R.id.open_app_button }
        )

        val settingsPanelInput = activity.editText(
            "Settings panel: wifi, bluetooth, accessibility, app_info, notifications, system_settings"
        ).apply { id = R.id.open_settings_panel_input }
        contentRoot.addView(settingsPanelInput)
        contentRoot.addView(
            activity.secondaryButton("Open Settings Panel") {
                hideKeyboard(settingsPanelInput)
                toolExecutionController.executeAndRender(
                    "open_settings_panel",
                    mapOf("panel" to settingsPanelInput.text.toString())
                )
                refreshToolsScreen()
            }.apply { id = R.id.open_settings_panel_button }
        )

        val waitAppInput = activity.editText("App package or launcher label to wait for").apply {
            id = R.id.wait_for_app_input
        }
        contentRoot.addView(waitAppInput)
        contentRoot.addView(
            activity.secondaryButton("Wait For App") {
                val expected = waitAppInput.text.toString()
                val args = if (expected.contains(".")) {
                    mapOf("package" to expected, "timeout_ms" to "5000")
                } else {
                    mapOf("label" to expected, "timeout_ms" to "5000")
                }
                hideKeyboard(waitAppInput)
                toolExecutionController.executeAsyncAndRender("wait_for_app", args)
            }.apply { id = R.id.wait_for_app_button }
        )

        val tapInput = activity.editText("Visible text to tap").apply { id = R.id.tap_text_input }
        contentRoot.addView(tapInput)
        contentRoot.addView(
            activity.secondaryButton("Tap Text") {
                val targetText = tapInput.text.toString()
                hideKeyboard(tapInput)
                toolExecutionController.executeAndRenderDelayed(
                    delayMillis = TOOL_ACTION_KEYBOARD_SETTLE_MS,
                    name = "tap",
                    args = mapOf("text" to targetText),
                    toastLabel = "Tap Text"
                )
            }.apply { id = R.id.tap_text_button }
        )

        val longPressInput = activity.editText("Visible text to long-press").apply {
            id = R.id.long_press_text_input
        }
        contentRoot.addView(longPressInput)
        contentRoot.addView(
            activity.secondaryButton("Long-Press Text") {
                val targetText = longPressInput.text.toString()
                hideKeyboard(longPressInput)
                toolExecutionController.executeAndRenderDelayed(
                    delayMillis = TOOL_ACTION_KEYBOARD_SETTLE_MS,
                    name = "long_press",
                    args = mapOf("text" to targetText),
                    toastLabel = "Long-Press Text"
                )
            }.apply { id = R.id.long_press_text_button }
        )

        val typeInput = activity.editText("Text to type into focused field").apply { id = R.id.type_text_input }
        contentRoot.addView(typeInput)
        contentRoot.addView(
            activity.secondaryButton("Type Into Focused Field") {
                val value = typeInput.text.toString()
                hideKeyboard(typeInput)
                val focusResult = getLastFocusInputArgs()?.let { focusArgs ->
                    toolExecutionController.executeAndRender("focus_input", focusArgs)
                }
                if (focusResult == null || focusResult.ok) {
                    toolExecutionController.executeAndRender("type_text", mapOf("text" to value))
                }
            }.apply { id = R.id.type_text_button }
        )

        contentRoot.addView(activity.formLabel("Focus selector"))

        val focusInputField = activity.editText(FOCUS_INPUT_SELECTOR_HINTS[getFocusSelectorIndex()]).apply {
            id = R.id.focus_input_input
        }
        val segmentButtons = mutableListOf<MaterialButton>()

        fun refreshSegments() {
            segmentButtons.forEachIndexed { i, btn ->
                val active = i == getFocusSelectorIndex()
                btn.backgroundTintList = ColorStateList.valueOf(
                    if (active) Theme.Accent else Theme.SurfaceRaised
                )
                btn.setTextColor(if (active) Theme.OnAccent else Theme.MutedText)
                btn.strokeWidth = if (active) 0 else 1
            }
            focusInputField.hint = FOCUS_INPUT_SELECTOR_HINTS[getFocusSelectorIndex()]
        }

        val selectorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 4) }
        }
        FOCUS_INPUT_SELECTOR_LABELS.forEachIndexed { i, label ->
            val btn = MaterialButton(activity).apply {
                text = label
                textSize = 11f
                isAllCaps = false
                cornerRadius = 16
                minHeight = 44
                insetTop = 0
                insetBottom = 0
                strokeColor = ColorStateList.valueOf(Theme.StrokeDark)
                setOnClickListener {
                    setFocusSelectorIndex(i)
                    refreshSegments()
                }
            }
            segmentButtons.add(btn)
            selectorRow.addView(
                btn,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(3, 0, 3, 0)
                }
            )
        }
        refreshSegments()

        contentRoot.addView(selectorRow)
        contentRoot.addView(focusInputField)
        contentRoot.addView(
            activity.secondaryButton("Focus Input Field") {
                hideKeyboard(focusInputField)
                val args = mapOf(focusInputSelectorKey(getFocusSelectorIndex()) to focusInputField.text.toString())
                val result = toolExecutionController.executeAndRender("focus_input", args)
                if (result.ok) {
                    setLastFocusInputArgs(args)
                } else {
                    setLastFocusInputArgs(null)
                }
            }.apply { id = R.id.focus_input_button }
        )

        contentRoot.addView(
            activity.secondaryButton("Clear Focused Field") {
                toolExecutionController.executeAndRender("clear_text", emptyMap())
            }.apply { id = R.id.clear_text_button }
        )

        val actionRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(
            activity.secondaryButton("Back") {
                toolExecutionController.executeAndRender("press_back", emptyMap())
            }.apply { id = R.id.back_button },
            rowButtonParams()
        )
        actionRow.addView(
            activity.secondaryButton("Home") {
                toolExecutionController.executeAndRender("press_home", emptyMap())
            }.apply { id = R.id.home_button },
            rowButtonParams()
        )
        contentRoot.addView(actionRow)

        val scrollRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        scrollRow.addView(
            activity.secondaryButton("Scroll Down") {
                toolExecutionController.executeAndRender("scroll", mapOf("direction" to "forward"))
                refreshToolsScreen()
            }.apply { id = R.id.scroll_down_button },
            rowButtonParams()
        )
        scrollRow.addView(
            activity.secondaryButton("Scroll Up") {
                toolExecutionController.executeAndRender("scroll", mapOf("direction" to "backward"))
                refreshToolsScreen()
            }.apply { id = R.id.scroll_up_button },
            rowButtonParams()
        )
        contentRoot.addView(scrollRow)

        val swipeHorizontalRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        swipeHorizontalRow.addView(
            activity.secondaryButton("Swipe Left") {
                toolExecutionController.executeAndRender("swipe", mapOf("direction" to "left"))
                refreshToolsScreen()
            }.apply { id = R.id.swipe_left_button },
            rowButtonParams()
        )
        swipeHorizontalRow.addView(
            activity.secondaryButton("Swipe Right") {
                toolExecutionController.executeAndRender("swipe", mapOf("direction" to "right"))
                refreshToolsScreen()
            }.apply { id = R.id.swipe_right_button },
            rowButtonParams()
        )
        contentRoot.addView(swipeHorizontalRow)

        val swipeVerticalRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        swipeVerticalRow.addView(
            activity.secondaryButton("Swipe Up") {
                toolExecutionController.executeAndRender("swipe", mapOf("direction" to "up"))
                refreshToolsScreen()
            }.apply { id = R.id.swipe_up_button },
            rowButtonParams()
        )
        swipeVerticalRow.addView(
            activity.secondaryButton("Swipe Down") {
                toolExecutionController.executeAndRender("swipe", mapOf("direction" to "down"))
                refreshToolsScreen()
            }.apply { id = R.id.swipe_down_button },
            rowButtonParams()
        )
        contentRoot.addView(swipeVerticalRow)

        val waitInput = activity.editText("Text to wait for").apply { id = R.id.wait_for_text_input }
        contentRoot.addView(waitInput)
        contentRoot.addView(
            activity.secondaryButton("Wait For Text") {
                val expected = waitInput.text.toString()
                toolExecutionController.executeAsyncAndRender(
                    "wait_for_ui",
                    mapOf("text" to expected, "timeout_ms" to "5000")
                )
            }.apply { id = R.id.wait_for_text_button }
        )

        contentRoot.addView(
            activity.secondaryButton("Wait For Idle") {
                toolExecutionController.executeAsyncAndRender("wait_for_idle", emptyMap())
            }.apply { id = R.id.wait_for_idle_button }
        )

        val keyboardScrollSpacer = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            )
        }
        contentRoot.addView(keyboardScrollSpacer)
        bindKeyboardScrollSpacer(keyboardScrollSpacer)
    }

    private fun focusInputSelectorKey(index: Int): String {
        return when (index) {
            1 -> "node_id"
            2 -> "view_id"
            else -> "text"
        }
    }

    private companion object {
        const val TOOL_ACTION_KEYBOARD_SETTLE_MS = 250L
        val FOCUS_INPUT_SELECTOR_LABELS = listOf("Text", "Node ID", "View ID")
        val FOCUS_INPUT_SELECTOR_HINTS = listOf(
            "Text or content description",
            "Node path  ·  e.g. 0.1.2",
            "Resource ID  ·  e.g. com.app:id/field"
        )
    }
}
