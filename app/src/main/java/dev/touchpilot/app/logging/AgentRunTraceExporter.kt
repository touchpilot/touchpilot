package dev.touchpilot.app.logging

import android.content.Context
import dev.touchpilot.app.agent.AgentRunDetailFormatter
import dev.touchpilot.app.agent.AgentRunRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AgentRunTraceExporter {
    fun export(context: Context, record: AgentRunRecord): File {
        val directory = File(context.getExternalFilesDir(null), "debug-traces").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(directory, "touchpilot-run-${record.id}-$timestamp.txt")
        file.writeText(AgentRunDetailFormatter.exportRedactedTrace(record))
        return file
    }
}
