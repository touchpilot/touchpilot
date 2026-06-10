package dev.touchpilot.app.ui.chat

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import dev.touchpilot.app.R
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.rounded

class ChatInputBarRenderer(
    private val activity: Activity,
    private val setChatTaskInput: (EditText) -> Unit,
    private val submitChatMessage: () -> Unit
) {
    fun render(): LinearLayout {
        val bar = LinearLayout(activity).apply {
            id = R.id.chat_input_bar
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Theme.SurfaceRaised)
            setPadding(20, 12, 20, 10)
        }
        bar.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1
                )
                setBackgroundColor(Theme.StrokeDark)
            }
        )

        val inputRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, 12, 0, 0)
        }
        val taskInput = EditText(activity).apply {
            id = R.id.agent_task_input
            hint = "Message TouchPilot..."
            setSingleLine(false)
            minLines = 1
            maxLines = 5
            textSize = 14.5f
            setTextColor(Color.WHITE)
            setHintTextColor(Theme.MutedText)
            setLineSpacing(4f, 1f)
            background = rounded(Theme.Card, 24, Theme.StrokeDark)
            setPadding(22, 14, 22, 14)
            minHeight = 56
            imeOptions = EditorInfo.IME_ACTION_SEND.toInt()
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    submitChatMessage()
                    true
                } else {
                    false
                }
            }
        }
        setChatTaskInput(taskInput)
        inputRow.addView(
            taskInput,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        )
        inputRow.addView(
            MaterialButton(activity).apply {
                id = R.id.run_agent_button
                text = "Send"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
                minHeight = 56
                minWidth = 0
                insetTop = 0
                insetBottom = 0
                gravity = Gravity.CENTER
                setTextColor(Theme.OnAccent)
                backgroundTintList = ColorStateList.valueOf(Theme.Accent)
                cornerRadius = 28
                setPadding(28, 0, 32, 0)
                icon = ContextCompat.getDrawable(activity, R.drawable.ic_send)
                iconTint = ColorStateList.valueOf(Theme.OnAccent)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = 10
                iconSize = 40
                setOnClickListener { submitChatMessage() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                56
            ).apply {
                leftMargin = 10
            }
        )
        bar.addView(inputRow)
        return bar
    }
}
