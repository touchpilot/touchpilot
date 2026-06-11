package dev.touchpilot.app.ui.logs

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import dev.touchpilot.app.R
import dev.touchpilot.app.logging.DeveloperLogEntry
import dev.touchpilot.app.logging.LogDetailSection
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.dp
import dev.touchpilot.app.ui.primaryButton
import dev.touchpilot.app.ui.rounded
import dev.touchpilot.app.ui.sectionTitle
import dev.touchpilot.app.ui.timelineCard
import dev.touchpilot.app.ui.withMargins
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LogsScreenRenderer(
    private val activity: Activity,
    private val contentRoot: LinearLayout,
    private val latestResult: () -> String,
    private val exportDebugTrace: () -> File,
    private val recordLogsResult: (String) -> Unit,
    private val refreshLogsScreen: () -> Unit
) {
    fun render(): LinearLayout {
        contentRoot.addView(activity.sectionTitle("Logs"))
        contentRoot.addView(
            activity.primaryButton("Export Debug Trace") {
                val file = exportDebugTrace()
                recordLogsResult("Debug trace exported: ${file.absolutePath}")
                refreshLogsScreen()
            }.apply { id = R.id.export_debug_trace_button }
        )
        contentRoot.addView(activity.timelineCard("Latest result", latestResult()))
        return LinearLayout(activity).apply {
            id = R.id.execution_log_list
            orientation = LinearLayout.VERTICAL
            contentRoot.addView(this)
            renderLogRows(this)
        }
    }

    fun renderLogRows(container: LinearLayout) {
        container.removeAllViews()
        val entries = ToolExecutionLog.recentEntries()
        if (entries.isEmpty()) {
            container.addView(
                activity.timelineCard(
                    title = "No developer logs yet",
                    body = "Run a chat task or tool action to record local logs."
                )
            )
            return
        }
        entries.forEach { entry ->
            container.addView(developerLogRow(entry))
        }
    }

    private fun developerLogRow(entry: DeveloperLogEntry): View {
        val statusColor = logStatusColor(entry.status)
        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = statusColor
            strokeWidth = 1
            radius = 8f
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            foreground = selectableItemBackground()
            setOnClickListener { showDeveloperLogDetails(entry.id) }
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 11, 16, 11)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(activity).apply {
                text = entry.name.ifBlank { entry.type }
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                maxLines = 1
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            TextView(activity).apply {
                text = DeveloperLogEntry.formatShortTimestamp(entry.timestampMillis)
                textSize = 11f
                setTextColor(Theme.MutedText)
                setPadding(8, 0, 10, 0)
            }
        )
        header.addView(logStatusChip(entry.status.ifBlank { "log" }))
        content.addView(header)
        val preview = entry.result.ifBlank { entry.payloadSummary }.lineSequence().firstOrNull().orEmpty()
        content.addView(
            TextView(activity).apply {
                text = "${entry.type.ifBlank { "log" }} · ${entry.source.ifBlank { "unknown" }} · $preview"
                textSize = 12.5f
                setTextColor(Theme.BodyText)
                maxLines = 1
                setPadding(0, 5, 0, 0)
            }
        )
        card.addView(content)
        return card.withMargins(top = 4, bottom = 4)
    }

    private fun showDeveloperLogDetails(id: Long) {
        val entry = ToolExecutionLog.findEntry(id) ?: return
        lateinit var dialog: AlertDialog
        val detailView = developerLogDetailView(
            entry = entry,
            onClose = { dialog.dismiss() }
        )
        dialog = AlertDialog.Builder(activity)
            .setView(detailView)
            .create()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.setPadding(
                activity.dp(16),
                activity.dp(24),
                activity.dp(16),
                activity.dp(24)
            )
        }
        dialog.show()
    }

    private fun developerLogDetailView(entry: DeveloperLogEntry, onClose: () -> Unit): View {
        val statusColor = logStatusColor(entry.status)
        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = statusColor
            strokeWidth = 2
            radius = 16f
            cardElevation = 0f
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(18), activity.dp(16), activity.dp(18), activity.dp(16))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(12))
        }
        header.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(activity).apply {
                        text = entry.name.ifBlank { "Log details" }
                        textSize = 16f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                        letterSpacing = 0.03f
                        maxLines = 2
                    }
                )
                addView(
                    TextView(activity).apply {
                        text = DeveloperLogEntry.formatTimestamp(entry.timestampMillis)
                        textSize = 11f
                        setTextColor(Theme.MutedText)
                        maxLines = 1
                        setPadding(0, activity.dp(4), 0, 0)
                    }
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(logIconButton(R.drawable.ic_copy, "Copy log") { copyDeveloperLog(entry) })
        header.addView(logIconButton(R.drawable.ic_close, "Close log details", onClose))
        content.addView(header)

        content.addView(logMetaChipRow(entry))

        val sections = entry.detailSections()
        val scrollContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            if (sections.isEmpty()) {
                addView(logDetailSectionView(LogDetailSection("Message", "No log content recorded.")))
            } else {
                sections.forEachIndexed { index, section ->
                    if (index > 0) {
                        addView(
                            View(activity).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    activity.dp(10)
                                )
                            }
                        )
                    }
                    addView(logDetailSectionView(section))
                }
            }
        }
        content.addView(
            ScrollView(activity).apply {
                addView(scrollContent)
                isVerticalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    activity.dp(320)
                ).apply {
                    topMargin = activity.dp(14)
                }
            }
        )
        card.addView(content)
        return card
    }

    private fun logDetailSectionView(section: LogDetailSection): View {
        val sectionContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Theme.SurfaceRaised, 12, Theme.StrokeDark)
            setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
        }
        sectionContainer.addView(
            TextView(activity).apply {
                text = section.title
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = true
                letterSpacing = 0.06f
                setTextColor(Theme.MutedText)
            }
        )
        sectionContainer.addView(
            TextView(activity).apply {
                text = formattedLogText(section.body)
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setTextColor(Theme.BodyText)
                setTextIsSelectable(true)
                setLineSpacing(4f, 1f)
                setPadding(0, activity.dp(8), 0, 0)
            }
        )
        return sectionContainer
    }

    private fun logMetaChipRow(entry: DeveloperLogEntry): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(logMetaChip(entry.type.ifBlank { "log" }, Theme.Accent))
            addView(spacer(activity.dp(6)))
            addView(logMetaChip(entry.source.ifBlank { "unknown" }, Theme.Warning))
            addView(spacer(activity.dp(6)))
            addView(logMetaChip(entry.status.ifBlank { "log" }, logStatusColor(entry.status)))
            if (entry.actor.isNotBlank()) {
                addView(spacer(activity.dp(6)))
                addView(logMetaChip(entry.actor, Theme.SurfaceRaised))
            }
        }
    }

    private fun logMetaChip(text: String, bg: Int): TextView {
        val stroke = if (bg == Theme.SurfaceRaised) Theme.StrokeDark else bg
        val textColor = when (bg) {
            Theme.Accent, Theme.Warning -> Theme.OnAccent
            Theme.SurfaceRaised -> Theme.MutedText
            else -> Color.WHITE
        }
        return TextView(activity).apply {
            this.text = text.uppercase()
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            background = rounded(bg, 8, stroke)
            setPadding(activity.dp(10), activity.dp(4), activity.dp(10), activity.dp(4))
        }
    }

    private fun spacer(width: Int): View {
        return View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(width, 1)
        }
    }

    private fun logIconButton(
        @DrawableRes iconRes: Int,
        description: String,
        onClick: () -> Unit
    ): View {
        return MaterialButton(activity).apply {
            icon = ContextCompat.getDrawable(activity, iconRes)
            iconTint = ColorStateList.valueOf(Theme.BodyText)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 0
            iconSize = activity.dp(24)
            minWidth = activity.dp(44)
            minHeight = activity.dp(44)
            layoutParams = LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)).apply {
                setMargins(activity.dp(2), 0, 0, 0)
            }
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeWidth = 0
            elevation = 0f
            stateListAnimator = null
            rippleColor = ColorStateList.valueOf(Color.argb(40, 255, 255, 255))
            contentDescription = description
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
        }
    }

    private fun formattedLogText(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return raw
        return try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> raw
            }
        } catch (_: Exception) {
            raw
        }
    }

    private fun copyDeveloperLog(entry: DeveloperLogEntry) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                entry.name.ifBlank { "TouchPilot log" },
                entry.fullLogText()
            )
        )
        Toast.makeText(activity, "Log copied", Toast.LENGTH_SHORT).show()
    }

    private fun logStatusChip(status: String): TextView {
        val color = logStatusColor(status)
        return TextView(activity).apply {
            text = status.ifBlank { "log" }
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (color == Theme.Accent || color == Theme.Warning) Theme.OnAccent else Color.WHITE)
            isAllCaps = true
            background = rounded(color, 7, color)
            setPadding(8, 3, 8, 3)
        }
    }

    private fun logStatusColor(status: String): Int {
        return when (status.lowercase()) {
            "ok", "complete" -> Theme.Accent
            "fail", "failed", "error" -> Theme.Danger
            "running", "pending", "info" -> Theme.Warning
            else -> Theme.StrokeDark
        }
    }

    private fun selectableItemBackground(): Drawable? {
        val attrs = intArrayOf(android.R.attr.selectableItemBackground)
        val typedArray = activity.obtainStyledAttributes(attrs)
        return try {
            typedArray.getDrawable(0)
        } finally {
            typedArray.recycle()
        }
    }
}
