package dev.touchpilot.app.logging

import android.content.Context
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.tools.ToolExecutionLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugTraceExporter(private val context: Context) {

    fun export(screenSnapshot: String): File {
        val directory = File(context.getExternalFilesDir(null), "debug-traces").apply {
            mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(directory, "touchpilot-trace-$timestamp.txt")
        file.writeText(
            buildContent(
                timestamp = timestamp,
                isAccessibilityConnected = AccessibilityBridge.isConnected(),
                toolLog = ToolExecutionLog.renderChronological(),
                screenSnapshot = screenSnapshot
            )
        )
        return file
    }

    companion object {
        fun buildContent(
            timestamp: String,
            isAccessibilityConnected: Boolean,
            toolLog: String,
            screenSnapshot: String
        ): String = buildString {
            appendLine("TouchPilot debug trace")
            appendLine("timestamp=$timestamp")
            appendLine()
            appendLine("Accessibility connected=$isAccessibilityConnected")
            appendLine()
            appendLine("Tool executions")
            appendLine(toolLog)
            appendLine()
            appendLine("Current screen")
            appendLine(SensitiveTextRedactor.redact(screenSnapshot))
        }
    }
}
