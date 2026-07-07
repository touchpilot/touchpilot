package dev.touchpilot.app.logging

import android.content.Context
import android.os.Build
import android.os.PowerManager
import dev.touchpilot.app.BuildConfig
import dev.touchpilot.app.agent.AgentRunDetailFormatter
import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.tools.ToolExecutionLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugTraceExporter(
    private val context: Context,
    private val accessibilityConnected: () -> Boolean,
    private val observeScreen: () -> String,
    private val renderToolLog: () -> String = { ToolExecutionLog.renderChronological() },
    private val timestamp: () -> String = {
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    }
) {
    fun exportRunTrace(record: AgentRunRecord): File {
        val timestamp = timestamp()
        val file = File(traceDirectory(), "touchpilot-run-${record.id}-$timestamp.txt")
        file.writeText(AgentRunDetailFormatter.exportRedactedTrace(record))
        return file
    }

    fun exportDebugTrace(): File {
        val timestamp = timestamp()
        val file = File(traceDirectory(), "touchpilot-trace-$timestamp.txt")
        file.writeText(
            buildString {
                appendLine("TouchPilot bug report")
                appendLine("timestamp=$timestamp")
                appendLine("app_version=${BuildConfig.VERSION_NAME}")
                appendLine("app_package=${context.packageName}")
                appendLine("android_version=${Build.VERSION.RELEASE}")
                appendLine("sdk=${Build.VERSION.SDK_INT}")
                appendLine("device=${Build.BRAND}/${Build.MANUFACTURER}/${Build.MODEL}")
                appendLine("battery_optimization=${batteryOptimizationStatus()}")
                appendLine()
                appendLine("Accessibility connected=${accessibilityConnected()}")
                appendLine()
                appendLine("Tool executions")
                appendLine(renderToolLog())
                appendLine()
                appendLine("Current screen")
                appendLine(SensitiveTextRedactor.redact(observeScreen()))
                appendLine()
                appendLine("Bug report intent: user-initiated local export only")
            }
        )
        return file
    }

    private fun traceDirectory(): File {
        return File(context.getExternalFilesDir(null), "debug-traces").apply {
            mkdirs()
        }
    }

    private fun batteryOptimizationStatus(): String {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return "unavailable"

        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            "not_applicable"
        } else if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            "ignored_by_system"
        } else {
            "managed_by_system"
        }
    }
}
