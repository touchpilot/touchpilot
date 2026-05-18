package dev.touchpilot.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.agent.AgentRunner
import dev.touchpilot.app.agent.LocalRouterCommandProvider
import dev.touchpilot.app.agent.OpenAiAgentCommandProvider
import dev.touchpilot.app.agent.ProviderConfig
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillStore
import dev.touchpilot.app.mcp.McpHttpClient
import dev.touchpilot.app.security.ProviderSecretStore
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.tools.ToolSpec
import org.json.JSONObject
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
        val skills = SkillStore(this).loadSkills()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 56, 40, 40)
        }

        val titleView = TextView(this).apply {
            id = R.id.touchpilot_title
            text = "TouchPilot"
            textSize = 30f
        }

        statusView = TextView(this).apply {
            id = R.id.touchpilot_status
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }

        val enableButton = Button(this).apply {
            id = R.id.open_accessibility_settings_button
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val observeButton = Button(this).apply {
            id = R.id.observe_screen_button
            text = "Observe Current Screen"
            setOnClickListener {
                hideKeyboard(this)
                refreshStatus()
                outputView.text = toolExecutor.execute("observe_screen", emptyMap()).message
                refreshExecutionLog()
            }
        }

        val appInput = EditText(this).apply {
            id = R.id.open_app_input
            hint = "App package or launcher label"
            setSingleLine(true)
        }

        val openAppButton = Button(this).apply {
            id = R.id.open_app_button
            text = "Open App"
            setOnClickListener {
                hideKeyboard(appInput)
                appInput.clearFocus()
                val target = appInput.text.toString()
                executeAndRender("open_app", mapOf("target" to target))
            }
        }

        val targetInput = EditText(this).apply {
            id = R.id.tap_text_input
            hint = "Visible text to tap"
            setSingleLine(true)
        }

        val tapButton = Button(this).apply {
            id = R.id.tap_text_button
            text = "Tap Text"
            setOnClickListener {
                hideKeyboard(targetInput)
                targetInput.clearFocus()
                val target = targetInput.text.toString()
                refreshStatus()
                executeAndRender("tap", mapOf("text" to target))
            }
        }

        val typeInput = EditText(this).apply {
            id = R.id.type_text_input
            hint = "Text to type into focused field"
            setSingleLine(true)
        }

        val typeButton = Button(this).apply {
            id = R.id.type_text_button
            text = "Type Into Focused Field"
            setOnClickListener {
                val value = typeInput.text.toString()
                hideKeyboard(typeInput)
                typeInput.requestFocus()
                refreshStatus()
                executeAndRender("type_text", mapOf("text" to value))
            }
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val backButton = Button(this).apply {
            id = R.id.back_button
            text = "Back"
            setOnClickListener {
                hideKeyboard(this)
                refreshStatus()
                executeAndRender("press_back", emptyMap())
            }
        }

        val homeButton = Button(this).apply {
            id = R.id.home_button
            text = "Home"
            setOnClickListener {
                hideKeyboard(this)
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
            id = R.id.scroll_down_button
            text = "Scroll Down"
            setOnClickListener {
                hideKeyboard(this)
                refreshStatus()
                executeAndRender("scroll", mapOf("direction" to "forward"))
            }
        }

        val scrollBackwardButton = Button(this).apply {
            id = R.id.scroll_up_button
            text = "Scroll Up"
            setOnClickListener {
                hideKeyboard(this)
                refreshStatus()
                executeAndRender("scroll", mapOf("direction" to "backward"))
            }
        }

        scrollRow.addView(scrollForwardButton, rowButtonParams())
        scrollRow.addView(scrollBackwardButton, rowButtonParams())

        val waitInput = EditText(this).apply {
            id = R.id.wait_for_text_input
            hint = "Text to wait for"
            setSingleLine(true)
        }

        val waitButton = Button(this).apply {
            id = R.id.wait_for_text_button
            text = "Wait For Text"
            setOnClickListener {
                hideKeyboard(waitInput)
                waitInput.clearFocus()
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
            text = "Local Agent"
            textSize = 18f
            setPadding(0, 36, 0, 8)
        }

        val providerModeTitle = TextView(this).apply {
            text = "Agent Runtime"
            textSize = 16f
            setPadding(0, 20, 0, 4)
        }

        val providerModeSpinner = Spinner(this).apply {
            id = R.id.agent_provider_spinner
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                ProviderModeLabels
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            val savedMode = preferences.getString("agent_provider_mode", AgentProviderMode.LOCAL_ROUTER.name)
            setSelection(providerModeIndex(savedMode))
        }

        val taskInput = EditText(this).apply {
            id = R.id.agent_task_input
            hint = "Agent task, e.g. open Settings"
            setSingleLine(false)
            minLines = 2
        }

        val cloudFallbackTitle = TextView(this).apply {
            text = "Experimental Cloud Fallback"
            textSize = 16f
            setPadding(0, 24, 0, 4)
        }

        val providerUrlInput = EditText(this).apply {
            id = R.id.agent_provider_url_input
            hint = "Cloud fallback chat completions URL"
            setSingleLine(true)
            setText(
                preferences.getString(
                    "provider_url",
                    "https://api.openai.com/v1/chat/completions"
                )
            )
        }

        val modelInput = EditText(this).apply {
            id = R.id.agent_model_input
            hint = "Model name"
            setSingleLine(true)
            setText(preferences.getString("provider_model", "gpt-5.2-mini"))
        }

        val apiKeyInput = EditText(this).apply {
            id = R.id.agent_api_key_input
            hint = if (secretStore.hasApiKey()) {
                "Cloud API key stored; leave blank to keep it"
            } else {
                "Cloud API key"
            }
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }

        val skillTitle = TextView(this).apply {
            text = "Active Skill"
            textSize = 16f
            setPadding(0, 20, 0, 4)
        }

        val skillSpinner = Spinner(this).apply {
            id = R.id.skill_spinner
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                listOf("No skill") + skills.map { it.title }
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            val savedSkillId = preferences.getString("active_skill", null)
            val savedIndex = skills.indexOfFirst { it.id == savedSkillId }
            setSelection(if (savedIndex >= 0) savedIndex + 1 else 0)
        }

        val runAgentButton = Button(this).apply {
            id = R.id.run_agent_button
            text = "Run Agent Step Loop"
            setOnClickListener {
                hideKeyboard(taskInput)
                taskInput.clearFocus()
                val providerMode = selectedProviderMode(providerModeSpinner)
                val apiKey = if (providerMode == AgentProviderMode.CLOUD) {
                    resolveApiKey(apiKeyInput, secretStore)
                } else {
                    ""
                }
                val providerConfig = ProviderConfig(
                    baseUrl = providerUrlInput.text.toString(),
                    apiKey = apiKey,
                    model = modelInput.text.toString()
                )
                val task = taskInput.text.toString()
                val selectedSkill = selectedSkill(skillSpinner, skills)

                preferences.edit()
                    .putString("provider_url", providerConfig.baseUrl)
                    .putString("provider_model", providerConfig.model)
                    .putString("active_skill", selectedSkill?.id)
                    .putString("agent_provider_mode", providerMode.name)
                    .apply()

                outputView.text = "Running ${providerMode.label()}${selectedSkill?.let { " with ${it.title}" }.orEmpty()}..."
                Thread {
                    val resultText = runCatching {
                        AgentRunner(
                            toolExecutor = toolExecutor,
                            approvalProvider = ToolApprovalProvider { tool, args ->
                                approveAgentTool(tool, args)
                            },
                            commandProvider = when (providerMode) {
                                AgentProviderMode.CLOUD -> OpenAiAgentCommandProvider(providerConfig)
                                AgentProviderMode.LOCAL_ROUTER -> LocalRouterCommandProvider(task, selectedSkill)
                            },
                            skill = selectedSkill
                        ).run(task).transcript
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

        val mcpTitle = TextView(this).apply {
            text = "MCP Client"
            textSize = 18f
            setPadding(0, 36, 0, 8)
        }

        val mcpEndpointInput = EditText(this).apply {
            id = R.id.mcp_endpoint_input
            hint = "MCP HTTP JSON-RPC endpoint"
            setSingleLine(true)
            setText(preferences.getString("mcp_endpoint", ""))
        }

        val mcpToolInput = EditText(this).apply {
            id = R.id.mcp_tool_input
            hint = "MCP tool name"
            setSingleLine(true)
        }

        val mcpArgsInput = EditText(this).apply {
            id = R.id.mcp_args_input
            hint = "MCP tool arguments JSON"
            setSingleLine(false)
            minLines = 2
            setText("{}")
        }

        val mcpButtonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val listMcpToolsButton = Button(this).apply {
            id = R.id.list_mcp_tools_button
            text = "List MCP Tools"
            setOnClickListener {
                hideKeyboard(mcpEndpointInput)
                mcpEndpointInput.clearFocus()
                val endpoint = mcpEndpointInput.text.toString()
                preferences.edit().putString("mcp_endpoint", endpoint).apply()
                outputView.text = "Listing MCP tools..."
                Thread {
                    val result = runCatching {
                        val client = McpHttpClient(endpoint)
                        val initialized = client.initialize()
                        val tools = client.listTools()
                        buildString {
                            appendLine("MCP initialized:")
                            appendLine(initialized)
                            appendLine()
                            appendLine("Tools:")
                            if (tools.isEmpty()) {
                                appendLine("No tools returned.")
                            } else {
                                tools.forEach { tool ->
                                    appendLine("- ${tool.name}: ${tool.description}")
                                }
                            }
                        }
                    }.getOrElse { error ->
                        "MCP list failed: ${error.message}"
                    }
                    runOnUiThread { outputView.text = result }
                }.start()
            }
        }

        val callMcpToolButton = Button(this).apply {
            id = R.id.call_mcp_tool_button
            text = "Call MCP Tool"
            setOnClickListener {
                hideKeyboard(mcpArgsInput)
                mcpArgsInput.clearFocus()
                val endpoint = mcpEndpointInput.text.toString()
                val toolName = mcpToolInput.text.toString()
                val argsText = mcpArgsInput.text.toString()
                preferences.edit().putString("mcp_endpoint", endpoint).apply()
                outputView.text = "Calling MCP tool..."
                Thread {
                    val result = runCatching {
                        val client = McpHttpClient(endpoint)
                        client.initialize()
                        val callResult = client.callTool(toolName, JSONObject(argsText))
                        "MCP $toolName -> ${callResult.ok}\n${callResult.message}"
                    }.getOrElse { error ->
                        "MCP call failed: ${error.message}"
                    }
                    runOnUiThread { outputView.text = result }
                }.start()
            }
        }

        mcpButtonRow.addView(listMcpToolsButton, rowButtonParams())
        mcpButtonRow.addView(callMcpToolButton, rowButtonParams())

        outputView = TextView(this).apply {
            id = R.id.output_view
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
            id = R.id.execution_log_view
            text = ToolExecutionLog.render()
            textSize = 13f
        }

        val exportTraceButton = Button(this).apply {
            id = R.id.export_debug_trace_button
            text = "Export Debug Trace"
            setOnClickListener {
                hideKeyboard(this)
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
        root.addView(providerModeTitle)
        root.addView(providerModeSpinner)
        root.addView(skillTitle)
        root.addView(skillSpinner)
        root.addView(taskInput)
        root.addView(runAgentButton)
        root.addView(cloudFallbackTitle)
        root.addView(providerUrlInput)
        root.addView(modelInput)
        root.addView(apiKeyInput)
        root.addView(mcpTitle)
        root.addView(mcpEndpointInput)
        root.addView(mcpButtonRow)
        root.addView(mcpToolInput)
        root.addView(mcpArgsInput)
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

    private fun hideKeyboard(anchor: View) {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(anchor.windowToken, 0)
    }

    private fun selectedSkill(skillSpinner: Spinner, skills: List<Skill>): Skill? {
        val index = skillSpinner.selectedItemPosition - 1
        return skills.getOrNull(index)
    }

    private fun selectedProviderMode(providerModeSpinner: Spinner): AgentProviderMode {
        return if (providerModeSpinner.selectedItemPosition == 1) {
            AgentProviderMode.CLOUD
        } else {
            AgentProviderMode.LOCAL_ROUTER
        }
    }

    private fun providerModeIndex(savedMode: String?): Int {
        return if (savedMode == AgentProviderMode.CLOUD.name) 1 else 0
    }

    private fun AgentProviderMode.label(): String {
        return when (this) {
            AgentProviderMode.CLOUD -> "experimental cloud fallback"
            AgentProviderMode.LOCAL_ROUTER -> "local router"
        }
    }

    private fun resolveApiKey(
        apiKeyInput: EditText,
        secretStore: ProviderSecretStore
    ): String {
        val enteredApiKey = apiKeyInput.text.toString()
        if (enteredApiKey.isNotBlank()) {
            secretStore.saveApiKey(enteredApiKey)
            apiKeyInput.text.clear()
            apiKeyInput.hint = "Cloud API key stored; leave blank to keep it"
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
        val ProviderModeLabels = listOf("Local router", "Experimental cloud fallback")
    }
}
