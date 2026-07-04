package dev.touchpilot.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.DefaultLocalReasoningCore
import dev.touchpilot.app.agent.LocalReasoningContext
import dev.touchpilot.app.agent.LocalReasoningCore
import dev.touchpilot.app.agent.defaultAgentRunInvocation
import dev.touchpilot.app.workflow.buildWorkflowReplayEngine
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.localinference.LiteRtCommandModelRuntime
import dev.touchpilot.app.logging.DebugTraceExporter
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillFileStore
import dev.touchpilot.app.memory.SharedPreferencesSkillStore
import dev.touchpilot.app.memory.SkillRegistry
import dev.touchpilot.app.memory.SkillStore
import dev.touchpilot.app.navigation.AppSection
import dev.touchpilot.app.navigation.NavigationController
import dev.touchpilot.app.navigation.SettingsPanel
import dev.touchpilot.app.workflow.WorkflowTraceStore
import dev.touchpilot.app.demonstration.export.DemonstrationWorkflowConverter
import dev.touchpilot.app.runtime.ToolExecutionCallbacks
import dev.touchpilot.app.runtime.ToolExecutionController
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.runtime.AgentRunController
import dev.touchpilot.app.demonstration.formatting.DemonstrationSummaryFormatter
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.ui.AppShellRenderer
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.RuntimeIndicator
import dev.touchpilot.app.ui.label
import dev.touchpilot.app.ui.welcomeDetail
import dev.touchpilot.app.ui.workingDetail
import dev.touchpilot.app.ui.chat.ChatEvent
import dev.touchpilot.app.ui.chat.ChatScreenRenderer
import dev.touchpilot.app.ui.logs.AgentRunDetailRenderer
import dev.touchpilot.app.ui.logs.LogsScreenRenderer
import dev.touchpilot.app.ui.product.ProductScreenRenderer
import dev.touchpilot.app.ui.settings.SettingsScreenRenderer
import dev.touchpilot.app.ui.settings.SkillDetailRenderer
import dev.touchpilot.app.ui.tools.ToolsScreenRenderer
import dev.touchpilot.app.ui.workflows.WorkflowDetailRenderer
import dev.touchpilot.app.ui.workflows.WorkflowEditorRenderer
import dev.touchpilot.app.workflow.WorkflowDefinition
import dev.touchpilot.app.workflow.WorkflowLibraryEntry
import dev.touchpilot.app.workflow.WorkflowLibrary
import dev.touchpilot.app.workflow.WorkflowReplayRepairPlanner
import dev.touchpilot.app.workflow.WorkflowSeedLoader
import dev.touchpilot.app.workflow.WorkflowRunStatus
import java.io.File

class MainActivity : Activity() {
    private lateinit var preferences: SharedPreferences
    private lateinit var skillStore: SkillStore
    private lateinit var skillFileStore: SkillFileStore
    private lateinit var skillRegistry: SkillRegistry
    private lateinit var toolExecutor: AndroidToolExecutor
    private lateinit var debugTraceExporter: DebugTraceExporter
    private lateinit var localModelRuntime: LiteRtCommandModelRuntime
    private lateinit var reasoningCore: LocalReasoningCore
    private lateinit var workflowTraceStore: WorkflowTraceStore
    private lateinit var agentRunController: AgentRunController
    private lateinit var workflowLibrary: WorkflowLibrary
    private lateinit var contentRoot: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var chatInputBar: LinearLayout
    private lateinit var chatTaskInput: EditText
    private lateinit var statusView: TextView
    private lateinit var executionLogList: LinearLayout
    private lateinit var appShellRenderer: AppShellRenderer
    private val navigationController = NavigationController()

    private var lastFocusInputArgs: Map<String, String>? = null
    private var focusSelectorIndex: Int = 0
    private val conversation = mutableListOf<ChatEvent>()
    private lateinit var demonstrationManager: dev.touchpilot.app.demonstration.DemonstrationSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("touchpilot", MODE_PRIVATE)
        ToolExecutionLog.configure(this)
        workflowLibrary = WorkflowLibrary(
            rootDir = File(filesDir, "workflows"),
            seedDefinitions = WorkflowSeedLoader.load(this)
        )
        skillFileStore = SkillFileStore(File(filesDir, "skills"))
        skillStore = SkillStore(this)
        reloadSkills()
        toolExecutor = AndroidToolExecutor(this)
        workflowTraceStore = WorkflowTraceStore(traceDirectory())
        debugTraceExporter = DebugTraceExporter(
            context = this,
            accessibilityConnected = { AccessibilityBridge.isConnected() },
            observeScreen = { toolExecutor.observeScreen() }
        )
        demonstrationManager = dev.touchpilot.app.demonstration.DemonstrationSessionManager(
            config = dev.touchpilot.app.demonstration.DemonstrationPreferences.recordingConfig(preferences),
            exporter = dev.touchpilot.app.demonstration.export.DemonstrationExporter(this),
        )
        toolExecutor.setRecordingListener(demonstrationManager.toolExecutionListener)
        localModelRuntime = LiteRtCommandModelRuntime(this)

        reasoningCore = DefaultLocalReasoningCore(
            invocation = defaultAgentRunInvocation(
                toolExecutor = toolExecutor,
                approvalProvider = ToolApprovalProvider { request ->
                    agentRunController.approveTool(request)
                },
                localModelRuntime = localModelRuntime
            ),
            sessionContext = { currentReasoningContext() },
            workflowReplayEngineFactory = { cancellationSignal ->
                buildWorkflowReplayEngine(
                    toolExecutor = toolExecutor,
                    approvalProvider = ToolApprovalProvider { request ->
                        agentRunController.approveTool(request)
                    },
                    cancellationSignal = cancellationSignal,
                )
            },
            availableSkills = { skillRegistry.enabledSkills() },
            screenContextProvider = { AccessibilityBridge.observeScreenContext() }
        )
        agentRunController = AgentRunController(
            reasoningCore = reasoningCore,
            conversation = conversation,
            currentProviderMode = ::currentProviderMode,
            runOnUiThread = { block -> runOnUiThread(block) },
            showChat = { showSection(AppSection.CHAT) },
            refreshExecutionLog = ::refreshExecutionLog,
            refreshStatus = ::refreshStatus,
            refreshStepTimeline = ::refreshStepTimeline,
            runtimeWorkingDetail = { currentRuntimeIndicator().workingDetail() },
            demonstrationManager = demonstrationManager,
            workflowTraceStore = workflowTraceStore,
        )

        if (conversation.isEmpty()) {
            conversation += ChatEvent.Agent(
                "What would you like me to do?",
                currentRuntimeIndicator().welcomeDetail()
            )
        }

        setContentView(buildRoot())
        showSection(AppSection.CHAT)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (::contentRoot.isInitialized) {
            showSection(navigationController.activeSection)
        }
    }

    private fun buildRoot(): View {
        appShellRenderer = AppShellRenderer(
            activity = this,
            activeSection = { navigationController.activeSection },
            onSectionSelected = ::showSection,
            setChatTaskInput = { chatTaskInput = it },
            submitChatMessage = ::submitChatMessage
        )
        val shellViews = appShellRenderer.render()
        scrollView = shellViews.scrollView
        contentRoot = shellViews.contentRoot
        chatInputBar = shellViews.chatInputBar
        statusView = shellViews.statusView
        return shellViews.root
    }

    private fun showSection(section: AppSection) {
        navigationController.showSection(section)
        updateBottomNav()
        chatInputBar.visibility = if (section == AppSection.CHAT) View.VISIBLE else View.GONE
        contentRoot.removeAllViews()
        when (section) {
            AppSection.CHAT -> renderChatScreen()
            AppSection.PRODUCT -> renderProductScreen()
            AppSection.LOGS -> renderLogsScreen()
            AppSection.SETTINGS -> renderSettingsScreen()
        }
        appShellRenderer.updatePageTitle(pageTitleForCurrentScreen())
        animatePendingSettingsTransition(section)
    }

    private fun pageTitleForCurrentScreen(): String {
        if (navigationController.activeWorkflowEditorRunId != null) {
            return "Save as workflow"
        }
        if (navigationController.activeRunDetailId != null) {
            return "Run details"
        }
        if (navigationController.activeSkillDetailId != null) {
            return "Skill details"
        }
        navigationController.activeWorkflowDetailId?.let { workflowId ->
            return findWorkflow(workflowId)?.definition?.title ?: "Workflow details"
        }
        return when (navigationController.activeSection) {
            AppSection.CHAT -> "Chat"
            AppSection.PRODUCT -> "Use TouchPilot"
            AppSection.LOGS -> "Logs"
            AppSection.SETTINGS -> navigationController.activeSettingsPanel?.label ?: "Settings"
        }
    }

    private fun animatePendingSettingsTransition(section: AppSection) {
        if (section != AppSection.SETTINGS) return

        val direction = navigationController.consumeSettingsAnimationDirection()
        if (direction == 0) return
        val travel = (contentRoot.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels).toFloat()
        contentRoot.translationX = travel * direction
        contentRoot.alpha = 0.96f
        contentRoot.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun updateBottomNav() {
        appShellRenderer.updateBottomNav()
    }

    private fun submitChatMessage() {
        val task = chatTaskInput.text.toString().trim()
        if (task.isEmpty()) return
        hideKeyboard(chatTaskInput)
        chatTaskInput.text.clear()
        agentRunController.startFromChat(task)
    }

    private fun chatScreenRenderer(): ChatScreenRenderer {
        return ChatScreenRenderer(
            activity = this,
            scrollView = scrollView,
            contentRoot = contentRoot,
            conversation = conversation,
            agentRunState = { agentRunController.runState },
            runtimeIndicator = ::currentRuntimeIndicator,
            skillTitle = { selectedSkill()?.title ?: "No skill selected" },
            cancelAgentRun = agentRunController::cancelRun,
            openRunDetail = ::openRunDetail,
            openSkillDetail = ::openSkillDetail,
            openWorkflowEditor = ::openWorkflowEditor,
            refreshChatScreen = { showSection(AppSection.CHAT) },
            isDemonstrationRecording = { agentRunController.isDemonstrationRecording },
            demonstrationRecordingEnabled = {
                dev.touchpilot.app.demonstration.DemonstrationPreferences.isRecordingEnabled(preferences)
            },
        )
    }

    private fun renderChatScreen() {
        if (navigationController.activeWorkflowEditorRunId != null) {
            renderWorkflowEditorScreen()
            return
        }
        if (navigationController.activeRunDetailId != null) {
            renderAgentRunDetailScreen()
            return
        }

        chatScreenRenderer().render()
    }

    private fun scrollChatToBottom() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun refreshStepTimeline(
        event: ChatEvent.StepTimeline,
        steps: List<AgentStep>,
        complete: Boolean = false
    ) {
        event.steps = steps
        event.isComplete = complete || event.isComplete
        chatScreenRenderer().bindStepTimeline(event)
        scrollChatToBottom()
    }

    private fun currentReasoningContext(): LocalReasoningContext {
        val providerMode = currentProviderMode()
        return LocalReasoningContext(
            skill = selectedSkill(),
            providerMode = providerMode
        )
    }

    private fun commitSelectedSkill(id: String?) {
        skillRegistry.setActiveSkill(id)
        showSection(AppSection.SETTINGS)
    }

    private fun renderToolsScreen() {
        ToolsScreenRenderer(
            activity = this,
            contentRoot = contentRoot,
            toolExecutionController = toolExecutionController(),
            openAccessibilitySettings = ::openAccessibilitySettings,
            refreshToolsScreen = { showSection(AppSection.SETTINGS) },
            hideKeyboard = ::hideKeyboard,
            bindKeyboardScrollSpacer = ::bindKeyboardScrollSpacer,
            getFocusSelectorIndex = { focusSelectorIndex },
            setFocusSelectorIndex = { focusSelectorIndex = it },
            getLastFocusInputArgs = { lastFocusInputArgs },
            setLastFocusInputArgs = { lastFocusInputArgs = it }
        ).render()
    }

    private fun renderProductScreen() {
        if (navigationController.activeWorkflowDetailId != null) {
            renderWorkflowDetailScreen()
            return
        }

        ProductScreenRenderer(
            activity = this,
            contentRoot = contentRoot,
            skills = skillRegistry.enabledSkills(),
            workflows = workflowLibrary.all(),
            openAccessibilitySettings = ::openAccessibilitySettings,
            showSection = ::showSection,
            openSettingsTools = {
                navigationController.openSettingsPanel(SettingsPanel.TOOLS)
                showSection(AppSection.SETTINGS)
            },
            runSkill = ::runSkillFromProduct,
            openWorkflowDetail = ::openWorkflowDetail,
        ).render()
    }

    private fun runSkillFromProduct(skillId: String) {
        val skill = skillRegistry.allSkills().firstOrNull { it.id == skillId } ?: return
        skillRegistry.setActiveSkill(skill.id)
        val task = skill.examples.firstOrNull()?.takeIf { it.isNotBlank() } ?: skill.title
        agentRunController.startFromChat(task)
    }

    private fun runSkill(skillId: String) {
        runSkillFromProduct(skillId)
    }

    private fun openWorkflowDetail(workflowId: String) {
        navigationController.openWorkflowDetail(workflowId)
        showSection(AppSection.PRODUCT)
    }

    private fun closeWorkflowDetail() {
        navigationController.closeWorkflowDetail()
        showSection(AppSection.PRODUCT)
    }

    private fun findWorkflow(workflowId: String): WorkflowLibraryEntry? {
        return workflowLibrary.find(workflowId)
    }

    private fun renderWorkflowDetailScreen() {
        WorkflowDetailRenderer(
            activity = this,
            contentRoot = contentRoot,
            workflowId = navigationController.activeWorkflowDetailId,
            findWorkflow = ::findWorkflow,
            closeWorkflowDetail = ::closeWorkflowDetail,
            replayWorkflow = ::runWorkflowFromProduct,
            renameWorkflow = ::renameWorkflow,
            deleteWorkflow = ::deleteWorkflow,
            refreshProductScreen = { showSection(AppSection.PRODUCT) },
        ).render()
    }

    private fun runWorkflowFromProduct(workflowId: String) {
        val workflow = workflowLibrary.find(workflowId)?.definition ?: return
        replayWorkflow(workflow)
    }

    private fun replayWorkflow(
        definition: WorkflowDefinition,
        captureWorkflowTrace: Boolean = false,
    ) {
        agentRunController.startWorkflowReplay(
            definition = definition,
            captureWorkflowTrace = captureWorkflowTrace,
            onFinished = { success, message, result ->
                workflowLibrary.recordRun(
                    workflowId = definition.id,
                    status = if (success) WorkflowRunStatus.SUCCEEDED else WorkflowRunStatus.FAILED,
                    message = message,
                )
                if (!success &&
                    result != null &&
                    WorkflowReplayRepairPlanner.failedStepIndex(result) != null &&
                    result.stopReason != AgentStepStopReason.USER_CANCELLED &&
                    result.stopReason != AgentStepStopReason.CLARIFICATION_NEEDED
                ) {
                    showWorkflowRepairDialog(
                        definition = definition,
                        result = result,
                        failedStepIndex = requireNotNull(WorkflowReplayRepairPlanner.failedStepIndex(result)),
                    )
                }
                if (navigationController.activeSection == AppSection.PRODUCT) {
                    showSection(AppSection.PRODUCT)
                }
            }
        )
    }

    private fun showWorkflowRepairDialog(
        definition: WorkflowDefinition,
        result: dev.touchpilot.app.agent.AgentRunResult,
        failedStepIndex: Int,
    ) {
        val message = buildString {
            append(result.stopMessage.ifBlank { "Workflow replay stopped before completion." })
            appendLine()
            appendLine()
            append("You can retry the failed step, skip it, or abort.")
            appendLine()
            append("Failed step: $failedStepIndex")
        }
        AlertDialog.Builder(this)
            .setTitle("Repair replay")
            .setMessage(message)
            .setPositiveButton("Retry step") { _, _ ->
                val retried = WorkflowReplayRepairPlanner.retryFailedStep(
                    workflow = definition,
                    failedStepIndex = failedStepIndex,
                )
                if (retried == null) {
                    android.widget.Toast.makeText(
                        this,
                        "Unable to build a workflow for retrying from that step.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                replayWorkflow(retried, captureWorkflowTrace = true)
            }
            .setNeutralButton("Skip failed step") { _, _ ->
                val repaired = WorkflowReplayRepairPlanner.skipFailedStep(definition, failedStepIndex)
                if (repaired == null) {
                    android.widget.Toast.makeText(
                        this,
                        "Skipping that step would leave no replayable workflow.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@setNeutralButton
                }
                workflowLibrary.save(repaired)
                replayWorkflow(repaired, captureWorkflowTrace = true)
            }
            .setNegativeButton("Abort replay", null)
            .show()
    }

    private fun renameWorkflow(workflowId: String, newTitle: String): WorkflowLibraryEntry? {
        val updated = workflowLibrary.rename(workflowId, newTitle)
        if (updated != null && navigationController.activeWorkflowDetailId == workflowId) {
            navigationController.openWorkflowDetail(workflowId)
            showSection(AppSection.PRODUCT)
        }
        return updated
    }

    private fun deleteWorkflow(workflowId: String): Boolean {
        val deleted = workflowLibrary.delete(workflowId)
        if (deleted && navigationController.activeWorkflowDetailId == workflowId) {
            closeWorkflowDetail()
        }
        return deleted
    }

    private fun toolExecutionController(): ToolExecutionController {
        return ToolExecutionController(
            activity = this,
            toolExecutor = toolExecutor,
            callbacks = object : ToolExecutionCallbacks {
                override fun refreshDeveloperLogs() {
                    refreshExecutionLog()
                }

                override fun refreshToolsScreen() {
                    navigationController.openSettingsPanel(SettingsPanel.TOOLS)
                    showSection(AppSection.SETTINGS)
                }
            }
        )
    }

    private fun bindKeyboardScrollSpacer(spacer: View) {
        val rootView = window.decorView
        val expandedHeight = (320 * resources.displayMetrics.density).toInt()
        val visibleRect = Rect()
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            rootView.getWindowVisibleDisplayFrame(visibleRect)
            val screenHeight = rootView.height
            if (screenHeight <= 0) return@OnGlobalLayoutListener
            val occluded = screenHeight - visibleRect.bottom
            // Anything above ~15% of screen height is conservatively treated
            // as the soft keyboard rather than a tall status/nav bar.
            val keyboardVisible = occluded > screenHeight * 0.15
            val target = if (keyboardVisible) expandedHeight else 0
            val params = spacer.layoutParams as? LinearLayout.LayoutParams ?: return@OnGlobalLayoutListener
            if (params.height != target) {
                params.height = target
                spacer.layoutParams = params
            }
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        spacer.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                rootView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        })
    }

    private fun renderLogsScreen() {
        if (navigationController.activeWorkflowEditorRunId != null) {
            renderWorkflowEditorScreen()
            return
        }
        if (navigationController.activeRunDetailId != null) {
            renderAgentRunDetailScreen()
            return
        }

        executionLogList = logsScreenRenderer().render()
    }

    private fun renderSettingsScreen() {
        if (navigationController.activeSkillDetailId != null) {
            renderSkillDetailScreen()
            return
        }

        SettingsScreenRenderer(
            activity = this,
            contentRoot = contentRoot,
            preferences = preferences,
            skills = skillRegistry.allSkills(),
            localModelRuntime = localModelRuntime,
            activeSettingsPanel = { navigationController.activeSettingsPanel },
            openSettingsPanel = navigationController::openSettingsPanel,
            closeSettingsPanel = navigationController::closeSettingsPanel,
            isSkillEnabled = skillRegistry::isEnabled,
            setSkillEnabled = skillRegistry::setEnabled,
            selectedSkillId = { skillRegistry.activeSkill()?.id },
            commitSelectedSkill = ::commitSelectedSkill,
            openSkillDetail = ::openSkillDetail,
            currentProviderMode = ::currentProviderMode,
            openAccessibilitySettings = ::openAccessibilitySettings,
            hideKeyboard = ::hideKeyboard,
            bindKeyboardScrollSpacer = ::bindKeyboardScrollSpacer,
            getFocusSelectorIndex = { focusSelectorIndex },
            setFocusSelectorIndex = { focusSelectorIndex = it },
            getLastFocusInputArgs = { lastFocusInputArgs },
            setLastFocusInputArgs = { lastFocusInputArgs = it },
            toolExecutionController = toolExecutionController(),
            recordMcpResult = mcpResultStore::recordMcpResult,
            mcpResult = mcpResultStore::forMcp,
            refreshSettingsScreen = { showSection(AppSection.SETTINGS) },
            demonstrationRecordingEnabled = {
                dev.touchpilot.app.demonstration.DemonstrationPreferences.isRecordingEnabled(preferences)
            },
            demonstrationAutoExportEnabled = {
                dev.touchpilot.app.demonstration.DemonstrationPreferences.isAutoExportEnabled(preferences)
            },
            demonstrationSessionCount = { demonstrationManager.sessions.size },
            demonstrationSummaries = {
                demonstrationManager.sessions
                    .asReversed()
                    .map(DemonstrationSummaryFormatter::format)
            },
            onDemonstrationRecordingToggled = { enabled ->
                dev.touchpilot.app.demonstration.DemonstrationPreferences.setRecordingEnabled(preferences, enabled)
                demonstrationManager.updateConfig(
                    dev.touchpilot.app.demonstration.DemonstrationPreferences.recordingConfig(preferences)
                )
                toolExecutor.setRecordingListener(demonstrationManager.toolExecutionListener)
            },
            onDemonstrationAutoExportToggled = { enabled ->
                dev.touchpilot.app.demonstration.DemonstrationPreferences.setAutoExportEnabled(preferences, enabled)
                demonstrationManager.updateConfig(
                    dev.touchpilot.app.demonstration.DemonstrationPreferences.recordingConfig(preferences)
                )
            },
            demonstrationSessions = { demonstrationManager.sessions },
            onDemonstrationReplayRequested = ::replayDemonstration,
        ).render()
    }

    private val mcpResultStore = McpResultStore()

    private fun openSkillDetail(skillId: String) {
        navigationController.openSkillDetail(skillId)
        showSection(AppSection.SETTINGS)
    }

    private fun closeSkillDetail() {
        navigationController.closeSkillDetail()
        showSection(AppSection.SETTINGS)
    }

    private fun findSkill(skillId: String): Skill? {
        return skillRegistry.allSkills().firstOrNull { it.id == skillId }
    }

    private fun renderSkillDetailScreen() {
        SkillDetailRenderer(
            activity = this,
            contentRoot = contentRoot,
            skillId = navigationController.activeSkillDetailId,
            findSkill = ::findSkill,
            selectedSkillId = { skillRegistry.activeSkill()?.id },
            closeSkillDetail = ::closeSkillDetail,
            commitSelectedSkill = ::commitSelectedSkill,
            runSkill = ::runSkill,
            refreshSettingsScreen = { showSection(AppSection.SETTINGS) }
        ).render()
    }

    private fun refreshExecutionLog() {
        if (::executionLogList.isInitialized) {
            logsScreenRenderer().renderLogRows(executionLogList)
        }
    }

    private fun logsScreenRenderer(): LogsScreenRenderer {
        return LogsScreenRenderer(
            activity = this,
            contentRoot = contentRoot,
            exportDebugTrace = ::exportDebugTrace,
            listWorkflowTraces = workflowTraceStore::all,
            deleteWorkflowTrace = workflowTraceStore::delete,
            refreshLogsScreen = { showSection(AppSection.LOGS) },
        )
    }

    private fun traceDirectory(): File {
        return File(filesDir, "workflow-traces").apply { mkdirs() }
    }

    private fun refreshStatus() {
        if (::statusView.isInitialized) {
            statusView.text = if (AccessibilityBridge.isConnected()) {
                "Accessibility service: connected"
            } else {
                "Accessibility service: not connected"
            }
            statusView.setTextColor(if (AccessibilityBridge.isConnected()) Theme.Accent else Theme.MutedText)
        }
    }

    private fun currentProviderMode(): AgentProviderMode {
        return when (preferences.getString("agent_provider_mode", AgentProviderMode.LOCAL_ROUTER.name)) {
            AgentProviderMode.LOCAL_MODEL.name -> AgentProviderMode.LOCAL_MODEL
            else -> AgentProviderMode.LOCAL_ROUTER
        }
    }

    private fun currentRuntimeIndicator(): RuntimeIndicator {
        return RuntimeIndicator(currentProviderMode(), localModelRuntime.status())
    }

    private fun selectedSkill(): Skill? {
        return skillRegistry.activeSkill()
    }

    private fun hideKeyboard(anchor: View) {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(anchor.windowToken, 0)
    }

    private fun openAccessibilitySettings() {
        startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun openRunDetail(runId: String) {
        navigationController.openRunDetail(runId)
        showSection(navigationController.activeSection)
    }

    private fun closeRunDetail() {
        navigationController.closeRunDetail()
        showSection(navigationController.activeSection)
    }

    private fun findAgentRun(runId: String): AgentRunRecord? {
        return agentRunController.findRun(runId)
    }

    private fun renderAgentRunDetailScreen() {
        AgentRunDetailRenderer(
            activity = this,
            contentRoot = contentRoot,
            runId = navigationController.activeRunDetailId,
            findAgentRun = ::findAgentRun,
            closeRunDetail = ::closeRunDetail,
            exportRunTrace = ::exportRunTrace,
            saveSkillCandidate = ::saveSkillCandidate,
            openWorkflowEditor = ::openWorkflowEditor,
        ).render()
    }

    private fun openWorkflowEditor(runId: String) {
        navigationController.openWorkflowEditor(runId)
        showSection(navigationController.activeSection)
    }

    private fun closeWorkflowEditor() {
        navigationController.closeWorkflowEditor()
        showSection(navigationController.activeSection)
    }

    private fun renderWorkflowEditorScreen() {
        WorkflowEditorRenderer(
            activity = this,
            contentRoot = contentRoot,
            runId = navigationController.activeWorkflowEditorRunId,
            findTrace = workflowTraceStore::forRun,
            uniqueWorkflowId = workflowLibrary::uniqueId,
            closeWorkflowEditor = ::closeWorkflowEditor,
            saveWorkflow = ::saveWorkflowFromEditor,
        ).render()
    }

    private fun saveWorkflowFromEditor(definition: WorkflowDefinition) {
        workflowLibrary.save(definition)
        navigationController.closeWorkflowEditor()
        navigationController.openWorkflowDetail(definition.id)
        showSection(AppSection.PRODUCT)
        Toast.makeText(this, "Workflow saved: ${definition.title}", Toast.LENGTH_SHORT).show()
    }

    private fun exportRunTrace(record: AgentRunRecord): File {
        return debugTraceExporter.exportRunTrace(record)
    }

    private fun exportDebugTrace(): File {
        return debugTraceExporter.exportDebugTrace()
    }

    private fun replayDemonstration(sessionId: String) {
        val session = demonstrationManager.findSession(sessionId)
        val workflow = session?.let(DemonstrationWorkflowConverter::toWorkflowDefinition)
        if (workflow == null) {
            android.widget.Toast.makeText(
                this,
                "That demonstration cannot be replayed.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        agentRunController.startWorkflowReplay(definition = workflow)
    }

    private fun reloadSkills() {
        val skillLoad = skillStore.load()
        skillRegistry = SkillRegistry(skillLoad.skills, SharedPreferencesSkillStore(preferences))
        skillLoad.invalid.forEach { invalid ->
            ToolExecutionLog.record(
                name = "skill_load_failed",
                args = "skill=${invalid.id}",
                ok = false,
                message = invalid.errors.joinToString("; "),
                source = "skills"
            )
        }
    }

    private fun saveSkillCandidate(id: String, markdown: String): Boolean {
        val result = skillFileStore.saveIfValid(id, markdown)
        return when (result) {
            is dev.touchpilot.app.memory.SkillParseResult.Valid -> {
                reloadSkills()
                android.widget.Toast.makeText(
                    this,
                    "Saved skill candidate ${result.skill.id}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                true
            }
            is dev.touchpilot.app.memory.SkillParseResult.Invalid -> {
                android.widget.Toast.makeText(
                    this,
                    result.errors.joinToString("\n"),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                false
            }
        }
    }

}
