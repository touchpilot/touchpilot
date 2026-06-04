package dev.touchpilot.app.runtime

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.tools.ToolResult

class ToolExecutionController(
    private val activity: Activity,
    private val toolExecutor: AndroidToolExecutor,
    private val callbacks: ToolExecutionCallbacks,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {
    fun executeAndRender(name: String, args: Map<String, String>): ToolResult {
        val result = toolExecutor.execute(name, args, ToolSource.DIRECT_DEBUG)
        callbacks.recordToolsResult(
            SensitiveTextRedactor.redact("$name($args) -> ${result.ok}: ${result.message}")
        )
        callbacks.refreshDeveloperLogs()
        return result
    }

    fun executeAndRenderDelayed(
        delayMillis: Long,
        name: String,
        args: Map<String, String>,
        toastLabel: String? = null
    ) {
        mainHandler.postDelayed({
            val result = executeAndRender(name, args)
            if (toastLabel != null) {
                showToolResultToast(toastLabel, result)
            }
            callbacks.refreshToolsScreen()
        }, delayMillis)
    }

    fun executeAsyncAndRender(name: String, args: Map<String, String>) {
        Thread {
            val result = toolExecutor.execute(name, args, ToolSource.DIRECT_DEBUG)
            activity.runOnUiThread {
                callbacks.recordToolsResult(
                    SensitiveTextRedactor.redact("$name -> ${result.ok}: ${result.message}")
                )
                callbacks.refreshDeveloperLogs()
                callbacks.refreshToolsScreen()
            }
        }.start()
    }

    private fun showToolResultToast(label: String, result: ToolResult) {
        Toast.makeText(
            activity,
            if (result.ok) {
                "$label succeeded"
            } else {
                "$label failed: ${result.message}"
            },
            Toast.LENGTH_LONG
        ).show()
    }
}
