package dev.touchpilot.app.logging

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.os.PowerManager
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
    private val foregroundAppSummary: () -> String = { "unknown" },
    private val renderToolLog: () -> String = { ToolExecutionLog.renderChronological() },
    private val timestamp: () -> String = {
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    }
) {
    enum class BugReportRedactionLevel(val label: String) {
        NoRedaction("No redaction"),
        Safe("Safe redaction (default)")
    }

    fun exportRunTrace(record: AgentRunRecord): File {
        val timestamp = timestamp()
        val file = File(traceDirectory(), "touchpilot-run-${record.id}-$timestamp.txt")
        file.writeText(AgentRunDetailFormatter.exportRedactedTrace(record))
        return file
    }

    fun exportDebugTrace(): File {
        return exportBugReport(BugReportRedactionLevel.Safe)
    }

    fun exportBugReport(redactionLevel: BugReportRedactionLevel = BugReportRedactionLevel.Safe): File {
        val createdAt = timestamp()
        val file = File(
            traceDirectory("bug-reports"),
            "touchpilot-bugreport-$createdAt.txt"
        )
        file.writeText(
            buildString {
                appendLine("TouchPilot bug report")
                appendLine("timestamp=$createdAt")
                appendLine("redaction_level=${redactionLevel.label}")
                appendLine()
                appendLine("## App and device")
                appendLine("package_name=${context.packageName}")
                appendLine("version_name=${appVersionName()}")
                appendLine("version_code=${appVersionCode()}")
                appendLine("android_release=${Build.VERSION.RELEASE}")
                appendLine("api_level=${Build.VERSION.SDK_INT}")
                appendLine("manufacturer=${Build.MANUFACTURER}")
                appendLine("brand=${Build.BRAND}")
                appendLine("model=${Build.MODEL}")
                appendLine()
                appendLine("## Compatibility checks")
                appendLine("accessibility_connected=${accessibilityConnected()}")
                appendLine("foreground_app=${redactText(foregroundAppSummary(), redactionLevel)}")
                appendLine("battery_optimization_ignored=${isBatteryOptimizationIgnored()}")
                appendLine("foreground_service=not directly observable; capture manually in compatibility notes")
                appendLine()
                appendLine("## Tool log")
                val toolLog = renderToolLog()
                appendLine(if (toolLog.isBlank()) "No tool log entries." else redactText(toolLog, redactionLevel))
                appendLine()
                appendLine("## Current screen")
                val screen = observeScreen()
                appendLine(if (screen.isBlank()) "No screen context captured." else redactText(screen, redactionLevel))
            }
        )
        return file
    }

    private fun appVersionName(): String {
        return runCatching {
            getPackageInfo().versionName.orEmpty().ifBlank { "unknown" }
        }.getOrElse { "unknown" }
    }

    private fun appVersionCode(): String {
        return runCatching {
            val packageInfo = getPackageInfo()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
        }.getOrElse { "unknown" }
    }

    private fun getPackageInfo(): PackageInfo {
        return context.packageManager.getPackageInfo(context.packageName, 0)
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun redactText(text: String, level: BugReportRedactionLevel): String {
        if (level == BugReportRedactionLevel.NoRedaction) return text
        return SensitiveTextRedactor.redact(text)
    }

    private fun traceDirectory(): File {
        return File(context.getExternalFilesDir(null), "debug-traces").apply {
            mkdirs()
        }
    }

    private fun traceDirectory(subdir: String): File {
        return File(context.getExternalFilesDir(null), subdir).apply {
            mkdirs()
        }
    }
}
