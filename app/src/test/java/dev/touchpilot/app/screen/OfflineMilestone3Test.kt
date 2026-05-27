package dev.touchpilot.app.screen

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentEventListener
import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.agent.DefaultLocalReasoningCore
import dev.touchpilot.app.agent.LocalReasoningContext
import dev.touchpilot.app.screen.ocr.ContextQuality
import dev.touchpilot.app.screen.ocr.ContextQualityDetector
import dev.touchpilot.app.screen.ocr.ObservedScreenSignals
import dev.touchpilot.app.screen.ocr.WeakReason
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Milestone 3 offline validation suite.
 *
 * These tests stay on the JVM: screen observations are represented by
 * [AccessibilityNodeSnapshot] fixtures, normalized through [ScreenContextBuilder],
 * summarized by [ScreenContextSummarizer], and answered through
 * [DefaultLocalReasoningCore]. No Android device, network backend, cloud model,
 * or tool executor is required.
 *
 * See docs/OFFLINE_VALIDATION_M3.md for the matching manual live checklist.
 */
class OfflineMilestone3Test {
    private val builder = ScreenContextBuilder()
    private val summarizer = ScreenContextSummarizer()

    @Test
    fun touchpilotScreenBuildsContextAndAnswersLocally() {
        val context = contextFrom(
            appLabel = "TouchPilot",
            packageName = "dev.touchpilot.app",
            root = node(
                "0",
                className = "LinearLayout",
                children = listOf(
                    node("0.0", className = "TextView", text = "TouchPilot"),
                    node("0.1", className = "EditText", text = "Message TouchPilot...", editable = true),
                    node("0.2", className = "TextView", text = "Go", clickable = true),
                    node("0.3", className = "TextView", text = "Settings", clickable = true)
                )
            )
        )

        val result = runScreenInquiry(context)

        assertContains(result.finalAnswer.orEmpty(), "TouchPilot")
        assertContains(result.transcript, "Suggested actions:")
        assertContains(result.transcript, "Tap Go")
        assertContains(result.transcript, "Type into the Message TouchPilot...")
        assertNoToolOrBackendEvents(result.events)
    }

    @Test
    fun androidSettingsScreenBuildsContextSummaryAndSuggestions() {
        val context = contextFrom(
            appLabel = "Settings",
            packageName = "com.android.settings",
            root = node(
                "0",
                className = "FrameLayout",
                children = listOf(
                    node("0.0", className = "TextView", text = "Settings"),
                    node(
                        "0.1",
                        className = "RecyclerView",
                        scrollable = true,
                        children = listOf(
                            node("0.1.0", className = "LinearLayout", text = "Network & internet", clickable = true),
                            node("0.1.1", className = "LinearLayout", text = "Connected devices", clickable = true),
                            node("0.1.2", className = "LinearLayout", text = "Apps", clickable = true),
                            node("0.1.3", className = "LinearLayout", text = "Notifications", clickable = true)
                        )
                    )
                )
            )
        )

        val summary = summarizer.summarize(context)

        assertTrue(context.nodes.isNotEmpty(), "screen context should be built locally")
        assertTrue(context.scrollableNodes.isNotEmpty(), "Settings fixture should expose scroll state")
        assertContains(summary.sentence, "I see the Settings screen.")
        assertContains(summary.sentence, "Network & internet")
        assertContains(summary.sentence, "scroll")
        assertTrue(summary.suggestedActions.any { it.tool == "tap" && it.args["text"] == "Network & internet" })
        assertTrue(summary.suggestedActions.any { it.tool == "scroll" && it.args["direction"] == "forward" })
    }

    @Test
    fun launcherScreenSummarizesTopAppsWithoutCloudRuntime() {
        val context = contextFrom(
            appLabel = "Pixel Launcher",
            packageName = "com.google.android.apps.nexuslauncher",
            root = node(
                "0",
                className = "FrameLayout",
                children = (1..8).map { index ->
                    node(
                        nodeId = "0.$index",
                        className = "TextView",
                        contentDescription = "App $index",
                        clickable = true
                    )
                }
            )
        )

        val result = runScreenInquiry(context)

        assertContains(result.finalAnswer.orEmpty(), "Pixel Launcher")
        assertContains(result.finalAnswer.orEmpty(), "App 1")
        assertContains(result.transcript, "Tap App 1")
        assertFalse(result.transcript.contains("App 8"), "summary should cap named launcher items")
        assertNoToolOrBackendEvents(result.events)
    }

    @Test
    fun inputFieldScreenRedactsSensitiveTextFromSummaryAndEvents() {
        val context = contextFrom(
            appLabel = "Mail",
            packageName = "com.example.mail",
            root = node(
                "0",
                className = "LinearLayout",
                children = listOf(
                    node("0.0", className = "TextView", text = "Sign in"),
                    node("0.1", className = "EditText", text = "user@example.com", editable = true),
                    node("0.2", className = "Button", text = "Continue", clickable = true)
                )
            )
        )

        val result = runScreenInquiry(context)
        val assistant = result.events.filterIsInstance<AgentEvent.AssistantMessage>().single()
        val payload = assistant.toJson().getJSONObject("payload")

        assertTrue(context.containsSensitiveContent)
        assertFalse(result.transcript.contains("user@example.com"))
        assertFalse(result.finalAnswer.orEmpty().contains("user@example.com"))
        assertFalse(payload.getString("text").contains("user@example.com"))
        assertFalse(payload.getString("detail").contains("user@example.com"))
        assertFalse(result.transcript.contains("Type into the user@example.com"))
        assertContains(result.transcript, "Tap Continue")
    }

    @Test
    fun weakAccessibilityContextUsesFallbackMessageAndNoSuggestions() {
        val quality = ContextQualityDetector().classify(
            ObservedScreenSignals(
                totalNodeCount = 24,
                visibleTextCount = 0,
                clickableNodeCount = 2,
                inputFieldCount = 0,
                maxTreeDepth = 5,
                packageName = "com.example.canvas"
            )
        )
        val weak = assertIs<ContextQuality.Weak>(quality)

        val summary = summarizer.summarize(ScreenContext.Empty)

        assertEquals(WeakReason.NO_VISIBLE_TEXT, weak.reason)
        assertEquals(ScreenContextSummarizer.WeakScreenMessage, summary.sentence)
        assertTrue(summary.suggestedActions.isEmpty())
    }

    private fun runScreenInquiry(context: ScreenContext) = DefaultLocalReasoningCore(
        invocation = { _, _, _ -> error("screen inquiry must not invoke the agent runner") },
        sessionContext = {
            LocalReasoningContext(
                skill = null,
                providerMode = AgentProviderMode.LOCAL_ROUTER
            )
        },
        screenContextProvider = { context }
    ).run("what can you do here", AgentEventListener {}, java.util.concurrent.atomic.AtomicBoolean(false))

    private fun contextFrom(
        appLabel: String,
        packageName: String,
        root: AccessibilityNodeSnapshot
    ): ScreenContext {
        return builder.build(
            root = root,
            appLabel = appLabel,
            packageName = packageName
        )
    }

    private fun assertNoToolOrBackendEvents(events: List<AgentEvent>) {
        assertTrue(events.none { it is AgentEvent.ToolRequested })
        assertTrue(events.none { it is AgentEvent.ToolRunning })
        assertTrue(events.none { it is AgentEvent.ToolSucceeded })
        assertTrue(events.none { it is AgentEvent.ToolFailed })
        assertNotNull(events.filterIsInstance<AgentEvent.FinalAnswer>().single())
    }

    private fun node(
        nodeId: String,
        className: String,
        text: String? = null,
        contentDescription: String? = null,
        clickable: Boolean = false,
        scrollable: Boolean = false,
        editable: Boolean = false,
        children: List<AccessibilityNodeSnapshot> = emptyList()
    ): AccessibilityNodeSnapshot {
        return AccessibilityNodeSnapshot(
            nodeId = nodeId,
            className = className,
            text = text,
            contentDescription = contentDescription,
            clickable = clickable,
            scrollable = scrollable,
            editable = editable,
            children = children
        )
    }
}
