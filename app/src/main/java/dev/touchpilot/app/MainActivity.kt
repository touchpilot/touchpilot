package dev.touchpilot.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.touchpilot.app.agent.AgentRunner
import dev.touchpilot.app.agent.ProviderConfig
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.security.ProviderSecretStore
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.tools.ToolSpec
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var outputView: TextView
    private lateinit var executionLogView: TextView
    private lateinit var toolExecutor: AndroidToolExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toolExecutor = AndroidToolExecutor(this)
        val preferences = getSharedPreferences("touchpilot", MODE_PRIVATE)
        val secretStore = ProviderSecretStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 56, 40, 40)
        }

        val titleView = TextView(this).apply {
            text = "TouchPilot"
            textSize = 30f
        }

        statusView = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }

        val enableButton = Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val observeButton = Button(this).apply {
            text = "Observe Current Screen"
            setOnClickListener {
                refreshStatus()
                outputView.text = toolExecutor.execute("observe_screen", emptyMap()).message
                refreshExecutionLog()
            }
        }

        val appInput = EditText(this).apply {
            hint = "App package or launcher label"
            setSingleLine(true)
        }

        val openAppButton = Button(this).apply {
            text = "Open App"
            setOnClickListener {
                val target = appInput.text.toString()
                executeAndRender("open_app", mapOf("target" to target))
            }
        }

        val targetInput = EditText(this).apply {
            hint = "Visible text to tap"
            setSingleLine(true)
        }

        val tapButton = Button(this).apply {
            text = "Tap Text"
            setOnClickListener {
                val target = targetInput.text.toString()
                refreshStatus()
                executeAndRender("tap", mapOf("text" to target))
            }
        }

        val typeInput = EditText(this).apply {
            hint = "Text to type into focused field"
            setSingleLine(true)
        }

        val typeButton = Button(this).apply {
            text = "Type Into Focused Field"
            setOnClickListener {
                val value = typeInput.text.toString()
                refreshStatus()
                executeAndRender("type_text", mapOf("text" to value))
            }
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val backButton = Button(this).apply {
            text = "Back"
            setOnClickListener {
                refreshStatus()
                executeAndRender("press_back", emptyMap())
            }
        }

        val homeButton = Button(this).apply {
            text = "Home"
            setOnClickListener {
                refreshStatus()
                executeAndRender("press_home", emptyMap())
            }
        }

        actionRow.addView(backButton, rowButtonParams())
        actionRow.addView(homeButton, rowButtonParams())

        val scrollRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val scrollForwardButton = Button(this).apply {
            text = "Scroll Down"
            setOnClickListener {
                refreshStatus()
                executeAndRender("scroll", mapOf("direction" to "forward"))
            }
        }

        val scrollBackwardButton = Button(this).apply {
            text = "Scroll Up"
            setOnClickListener {
                refreshStatus()
                executeAndRender("scroll", mapOf("direction" to "backward"))
            }
        }

        scrollRow.addView(scrollForwardButton, rowButtonParams())
        scrollRow.addView(scrollBackwardButton, rowButtonParams())

        val waitInput = EditText(this).apply {
            hint = "Text to wait for"
            setSingleLine(true)
        }

        val waitButton = Button(this).apply {
            text = "Wait For Text"
            setOnClickListener {
                val expectedText = waitInput.text.toString()
                outputView.text = "Waiting for \"$expectedText\"..."
                Thread {
                    val result = toolExecutor.execute(
                        "wait_for_ui",
                        mapOf("text" to expectedText, "timeout_ms" to "5000")
                    )
                    runOnUiThread {
                        refreshStatus()
                        outputView.text = "wait_for_ui -> ${result.ok}: ${result.message}"
                        refreshExecutionLog()
                    }
                }.start()
            }
        }

        val agentTitle = TextView(this).apply {
            text = "Agent MVP"
            textSize = 18f
            setPadding(0, 36, 0, 8)
        }

        val providerUrlInput = EditText(this).apply {
            hint = "OpenAI-compatible chat completions URL"
            setSingleLine(true)
            setText(
                preferences.getString(
                    "provider_url",
                    "https://api.openai.com/v1/chat/completions"
                )
            )
        }

        val modelInput = EditText(this).apply {
            hint = "Model name"
            setSingleLine(true)
            setText(preferences.getString("provider_model", "gpt-5.2-mini"))
        }

        val apiKeyInput = EditText(this).apply {
            hint = if (secretStore.hasApiKey()) {
                "API key stored; leave blank to keep it"
            } else {
                "API key"
            }
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }

        val taskInput = EditText(this).apply {
            hint = "Agent task, e.g. observe the current screen"
            setSingleLine(false)
            minLines = 2
        }

        val runAgentButton = Button(this).apply {
            text = "Run Agent Step Loop"
            setOnClickListener {
                val apiKey = resolveApiKey(apiKeyInput, secretStore)
                val providerConfig = ProviderConfig(
                    baseUrl = providerUrlInput.text.toString(),
                    apiKey = apiKey,
                    model = modelInput.text.toString()
                )
                val task = taskInput.text.toString()

                preferences.edit()
                    .putString("provider_url", providerConfig.baseUrl)
                    .putString("provider_model", providerConfig.model)
                    .apply()

                outputView.text = "Running agent..."
                Thread {
                    val resultText = runCatching {
                        AgentRunner(
                            toolExecutor = toolExecutor,
                            approvalProvider = ToolApprovalProvider { tool, args ->
                                approveAgentTool(tool, args)
                            }
                        ).run(task, providerConfig).transcript
                    }.getOrElse { error ->
                        "Agent failed: ${error.message}"
                    }
                    runOnUiThread {
                        outputView.text = resultText
                        refreshStatus()
                        refreshExecutionLog()
                    }
                }.start()
            }
        }

        outputView = TextView(this).apply {
            text = "Enable TouchPilot Control, then observe a screen."
            textSize = 13f
            setPadding(0, 24, 0, 0)
        }

        val executionLogTitle = TextView(this).apply {
            text = "Tool Execution Log"
            textSize = 18f
            setPadding(0, 32, 0, 8)
        }

        executionLogView = TextView(this).apply {
            text = ToolExecutionLog.render()
            textSize = 13f
        }

        val exportTraceButton = Button(this).apply {
            text = "Export Debug Trace"
            setOnClickListener {
                val file = exportDebugTrace()
                outputView.text = "Debug trace exported: ${file.absolutePath}"
            }
        }

        root.addView(titleView)
        root.addView(statusView)
        root.addView(enableButton)
        root.addView(observeButton)
        root.addView(appInput)
        root.addView(openAppButton)
        root.addView(targetInput)
        root.addView(tapButton)
        root.addView(typeInput)
        root.addView(typeButton)
        root.addView(actionRow)
        root.addView(scrollRow)
        root.addView(waitInput)
        root.addView(waitButton)
        root.addView(agentTitle)
        root.addView(providerUrlInput)
        root.addView(modelInput)
        root.addView(apiKeyInput)
        root.addView(taskInput)
        root.addView(runAgentButton)
        root.addView(outputView)
        root.addView(executionLogTitle)
        root.addView(exportTraceButton)
        root.addView(executionLogView)

        setContentView(ScrollView(this).apply {
            addView(root)
        })

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun executeAndRender(name: String, args: Map<String, String>) {
        val result = toolExecutor.execute(name, args)
        outputView.text = "$name($args) -> ${result.ok}: ${result.message}"
        refreshExecutionLog()
    }

    private fun refreshExecutionLog() {
        executionLogView.text = ToolExecutionLog.render()
    }

    private fun refreshStatus() {
        statusView.text = if (AccessibilityBridge.isConnected()) {
            "Accessibility service: connected"
        } else {
            "Accessibility service: not connected"
        }
    }

    private fun rowButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
    }

    private fun resolveApiKey(
        apiKeyInput: EditText,
        secretStore: ProviderSecretStore
    ): String {
        val enteredApiKey = apiKeyInput.text.toString()
        if (enteredApiKey.isNotBlank()) {
            secretStore.saveApiKey(enteredApiKey)
            apiKeyInput.text.clear()
            apiKeyInput.hint = "API key stored; leave blank to keep it"
            return enteredApiKey
        }

        return runCatching { secretStore.loadApiKey().orEmpty() }
            .getOrElse {
                secretStore.clearApiKey()
                ""
            }
    }

    private fun approveAgentTool(tool: ToolSpec, args: Map<String, String>): Boolean {
        val latch = CountDownLatch(1)
        val approved = AtomicBoolean(false)

        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Approve ${tool.name}?")
                .setMessage(buildApprovalMessage(tool, args))
                .setPositiveButton("Approve") { _, _ ->
                    approved.set(true)
                    latch.countDown()
                }
                .setNegativeButton("Deny") { _, _ ->
                    approved.set(false)
                    latch.countDown()
                }
                .setOnCancelListener {
                    approved.set(false)
                    latch.countDown()
                }
                .show()
        }

        return latch.await(ApprovalTimeoutMs, TimeUnit.MILLISECONDS) && approved.get()
    }

    private fun buildApprovalMessage(tool: ToolSpec, args: Map<String, String>): String {
        val argsText = if (args.isEmpty()) {
            "none"
        } else {
            args.entries.joinToString(separator = "\n") { entry ->
                "${entry.key}: ${entry.value.take(MaxApprovalArgLength)}"
            }
        }

        return """
            Risk: ${tool.risk}
            Description: ${tool.description}

            Arguments:
            $argsText
        """.trimIndent()
    }

    private fun exportDebugTrace(): File {
        val directory = File(getExternalFilesDir(null), "debug-traces").apply {
            mkdirs()
        }
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
                appendLine(toolExecutor.observeScreen())
            }
        )
        return file
    }

    private companion object {
        const val ApprovalTimeoutMs = 5 * 60 * 1000L
        const val MaxApprovalArgLength = 500
    }
}
