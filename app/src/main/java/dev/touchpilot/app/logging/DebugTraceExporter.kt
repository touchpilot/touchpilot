package dev.touchpilot.app.logging

import android.content.Context
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.tools.ToolExecutionLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugTraceExporter {
    fun export(context: Context, toolExecutor: AndroidToolExecutor): File {
        val directory = File(context.getExternalFilesDir(null), "debug-traces").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(directory, "touchpilot-trace-$timestamp.txt")
        file.writeText(
            buildString {
                appendLine("TouchPilot debug trace")
                appendLine("timestamp=$timestamp")
                appendLine()
                appendLine("Accessibility connected=${AccessibilityBridge.isConnected()}")
                appendLine()
                appendLine("Tool executions")
                appendLine(ToolExecutionLog.renderChronological())
                appendLine()
                appendLine("Current screen")
                appendLine(SensitiveTextRedactor.redact(toolExecutor.observeScreen()))
            }
        )
        return file
    }
}
