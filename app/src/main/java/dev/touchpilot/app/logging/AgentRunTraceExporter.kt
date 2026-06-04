package dev.touchpilot.app.logging

import android.content.Context
import dev.touchpilot.app.agent.AgentRunDetailFormatter
import dev.touchpilot.app.agent.AgentRunRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AgentRunTraceExporter(private val context: Context) {

    fun export(record: AgentRunRecord): File {
        val directory = File(context.getExternalFilesDir(null), "debug-traces").apply {
            mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(directory, "touchpilot-run-${record.id}-$timestamp.txt")
        file.writeText(buildContent(record))
        return file
    }

    companion object {
        fun buildContent(record: AgentRunRecord): String {
            return AgentRunDetailFormatter.exportRedactedTrace(record)
        }
    }
}
