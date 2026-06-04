package dev.touchpilot.app.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout

fun rowButtonParams(): LinearLayout.LayoutParams {
    return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(4, 6, 4, 6)
    }
}

fun controlParams(): LinearLayout.LayoutParams {
    return LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        setMargins(0, 7, 0, 7)
    }
}

fun Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}
