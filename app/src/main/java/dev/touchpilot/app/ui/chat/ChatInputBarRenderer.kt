package dev.touchpilot.app.ui.chat

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import dev.touchpilot.app.R
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.dp
import dev.touchpilot.app.ui.rounded

class ChatInputBarRenderer(
    private val activity: Activity,
    private val setChatTaskInput: (EditText) -> Unit,
    private val submitChatMessage: () -> Unit
) {
    fun render(): LinearLayout {
        val sendSize = activity.dp(36)
        val iconSize = activity.dp(16)

        val bar = LinearLayout(activity).apply {
            id = R.id.chat_input_bar
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Theme.SurfaceRaised)
            setPadding(16, 10, 16, 8)
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

        val inputContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Theme.Card, 24, Theme.StrokeDark)
            setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4))
            minimumHeight = activity.dp(48)
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
            background = null
            setPadding(activity.dp(14), activity.dp(8), activity.dp(6), activity.dp(8))
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
        inputContainer.addView(
            taskInput,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val sendButton = FrameLayout(activity).apply {
            id = R.id.run_agent_button
            background = rounded(Theme.Accent, sendSize / 2, Theme.Accent)
            foreground = selectableItemBackgroundBorderless()
            contentDescription = "Send"
            isClickable = true
            isFocusable = true
            setOnClickListener { submitChatMessage() }
            addView(
                ImageView(activity).apply {
                    setImageResource(R.drawable.ic_send)
                    imageTintList = ColorStateList.valueOf(Theme.OnAccent)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            )
        }
        inputContainer.addView(sendButton, LinearLayout.LayoutParams(sendSize, sendSize))

        bar.addView(
            inputContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = activity.dp(10)
            }
        )
        return bar
    }

    private fun selectableItemBackgroundBorderless(): Drawable? {
        val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
        val typedArray = activity.obtainStyledAttributes(attrs)
        return try {
            typedArray.getDrawable(0)
        } finally {
            typedArray.recycle()
        }
    }
}
