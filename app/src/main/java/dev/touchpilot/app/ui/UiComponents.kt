package dev.touchpilot.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

fun Context.sectionTitle(text: String): TextView {
    return TextView(this).apply {
        setText(text)
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        setPadding(0, 2, 0, 10)
    }
}

fun Context.formLabel(text: String): TextView {
    return TextView(this).apply {
        setText(text)
        textSize = 12f
        setTextColor(TouchPilotTheme.MutedText)
        setPadding(0, 12, 0, 6)
    }
}

fun Context.editText(hintText: String): EditText {
    return EditText(this).apply {
        hint = hintText
        contentDescription = hintText
        setSingleLine(true)
        textSize = 14f
        setTextColor(Color.WHITE)
        setHintTextColor(TouchPilotTheme.MutedText)
        background = rounded(TouchPilotTheme.SurfaceRaised, 8, TouchPilotTheme.StrokeDark)
        layoutParams = controlParams()
        minHeight = 52
        setPadding(16, 8, 16, 8)
    }
}

fun Context.primaryButton(
    text: String,
    @DrawableRes iconRes: Int? = null,
    onClick: () -> Unit
): TextView {
    return MaterialButton(this).apply {
        setText(text)
        gravity = Gravity.CENTER
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        setTextColor(TouchPilotTheme.OnAccent)
        backgroundTintList = ColorStateList.valueOf(TouchPilotTheme.Accent)
        strokeColor = ColorStateList.valueOf(TouchPilotTheme.Accent)
        strokeWidth = 1
        cornerRadius = 10
        layoutParams = controlParams()
        minHeight = 50
        setPadding(16, 12, 16, 12)
        if (iconRes != null) {
            icon = ContextCompat.getDrawable(this@primaryButton, iconRes)
            iconTint = ColorStateList.valueOf(TouchPilotTheme.OnAccent)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 8
            iconSize = 36
        }
        setOnClickListener { onClick() }
    }
}

fun Context.secondaryButton(
    text: String,
    @DrawableRes iconRes: Int? = null,
    onClick: () -> Unit
): TextView {
    return MaterialButton(this).apply {
        setText(text)
        gravity = Gravity.CENTER
        textSize = 13f
        isAllCaps = false
        setTextColor(TouchPilotTheme.BodyText)
        backgroundTintList = ColorStateList.valueOf(TouchPilotTheme.SurfaceRaised)
        strokeColor = ColorStateList.valueOf(TouchPilotTheme.StrokeDark)
        strokeWidth = 1
        cornerRadius = 10
        layoutParams = controlParams()
        minHeight = 48
        setPadding(14, 12, 14, 12)
        if (iconRes != null) {
            icon = ContextCompat.getDrawable(this@secondaryButton, iconRes)
            iconTint = ColorStateList.valueOf(TouchPilotTheme.BodyText)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 8
            iconSize = 36
        }
        setOnClickListener { onClick() }
    }
}

fun Context.summaryCard(
    title: String,
    value: String,
    chipText: String,
    chipAccent: Boolean
): View {
    val card = MaterialCardView(this).apply {
        setCardBackgroundColor(TouchPilotTheme.Card)
        strokeColor = if (chipAccent) TouchPilotTheme.Accent else TouchPilotTheme.StrokeDark
        strokeWidth = if (chipAccent) 2 else 1
        radius = 8f
        cardElevation = 0f
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(18, 14, 18, 14)
    }
    val textColumn = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    textColumn.addView(
        TextView(this).apply {
            text = title
            textSize = 11.5f
            setTextColor(TouchPilotTheme.MutedText)
        }
    )
    textColumn.addView(
        TextView(this).apply {
            text = value
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 4, 0, 0)
        }
    )
    content.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    content.addView(statusChip(chipText, chipAccent))
    card.addView(content)
    return card.withMargins(top = 4, bottom = 12)
}

fun Context.statusChip(text: String, accent: Boolean): TextView {
    val fill = if (accent) TouchPilotTheme.Accent else TouchPilotTheme.SurfaceRaised
    val textColor = if (accent) TouchPilotTheme.OnAccent else TouchPilotTheme.MutedText
    val stroke = if (accent) TouchPilotTheme.Accent else TouchPilotTheme.StrokeDark
    return TextView(this).apply {
        setText(text)
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        isAllCaps = true
        background = rounded(fill, 8, stroke)
        setPadding(12, 5, 12, 5)
    }
}

fun Context.detailSectionView(
    title: String,
    body: String,
    muted: Boolean = false
): View {
    val sectionContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(TouchPilotTheme.SurfaceRaised, 12, TouchPilotTheme.StrokeDark)
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }
    sectionContainer.addView(
        TextView(this).apply {
            text = title
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = true
            letterSpacing = 0.06f
            setTextColor(TouchPilotTheme.MutedText)
        }
    )
    sectionContainer.addView(
        TextView(this).apply {
            text = body
            textSize = 12.5f
            setTextColor(if (muted) TouchPilotTheme.MutedText else TouchPilotTheme.BodyText)
            setTextIsSelectable(true)
            setLineSpacing(4f, 1f)
            setPadding(0, dp(8), 0, 0)
        }
    )
    return sectionContainer
}

fun Context.timelineCard(
    title: String,
    body: String,
    actionHint: String? = null,
    onClick: (() -> Unit)? = null
): View {
    val card = MaterialCardView(this).apply {
        setCardBackgroundColor(TouchPilotTheme.Card)
        strokeColor = if (onClick != null) TouchPilotTheme.Accent else TouchPilotTheme.StrokeDark
        strokeWidth = if (onClick != null) 2 else 1
        radius = 8f
        cardElevation = 0f
        if (onClick != null) {
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 14, 18, 14)
    }
    content.addView(
        TextView(this).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
    )
    content.addView(
        TextView(this).apply {
            text = body
            textSize = 12.5f
            setTextColor(TouchPilotTheme.BodyText)
            setPadding(0, 8, 0, 0)
        }
    )
    if (actionHint != null) {
        content.addView(
            TextView(this).apply {
                text = actionHint
                textSize = 11.5f
                setTextColor(TouchPilotTheme.Accent)
                setPadding(0, 8, 0, 0)
            }
        )
    }
    card.addView(content)
    return card.withMargins(top = 8, bottom = 8)
}

fun View.withMargins(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0
): View {
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        setMargins(left, top, right, bottom)
    }
    return this
}

fun rounded(fill: Int, radius: Int, stroke: Int): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = radius.toFloat()
        setStroke(1, stroke)
    }
}
