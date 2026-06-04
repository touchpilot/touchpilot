package dev.touchpilot.app.logging

import android.content.Context
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.agent.AgentRunDetailFormatter
import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.security.SensitiveTextRedactor
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
                appendLine("Accessibility connected=${accessibilityConnected()}")
                appendLine()
                appendLine("Tool executions")
                appendLine(renderToolLog())
                appendLine()
                appendLine("Current screen")
                appendLine(SensitiveTextRedactor.redact(observeScreen()))
            }
        )
        return file
    }

    private fun traceDirectory(): File {
        return File(context.getExternalFilesDir(null), "debug-traces").apply {
            mkdirs()
        }
    }
}
