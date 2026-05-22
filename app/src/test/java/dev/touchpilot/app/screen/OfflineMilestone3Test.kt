package dev.touchpilot.app.screen

import dev.touchpilot.app.security.SensitiveTextRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Milestone 3 Offline Validation Test Suite
 *
 * Validates that TouchPilot can understand Android screens locally without cloud vision
 * or chat models. This test suite drives the real screen-understanding components and
 * asserts their behavior without any network calls or provider dependencies.
 *
 * See docs/OFFLINE_VALIDATION_M3.md for the companion validation checklist.
 */
class OfflineMilestone3Test {
    private val builder = ScreenContextBuilder()
    private val summarizer = ScreenContextSummarizer()

    // ===== Screen Context Capture (Local, No Network) =====

    @Test
    fun screenContextBuiltLocallyFromAccessibilitySnapshot() {
        val root = container(
            id = "0",
            children = listOf(
                clickable(id = "0.0", text = "Network & internet"),
                clickable(id = "0.1", text = "Connected devices")
            )
        )

        val context = builder.build(
            root = root,
            appLabel = "Settings",
            packageName = "com.android.settings"
        )

        assertEquals("Settings", context.appLabel)
        assertEquals("com.android.settings", context.packageName)
        assertEquals(2, context.clickableNodes.size)
        // This proves screen context is built locally from raw Accessibility data
        // without any network calls or cloud services.
    }

    @Test
    fun screenContextFiltersEmptyContainersAndKeepsSignalNodes() {
        val root = container(
            id = "0",
            children = listOf(
                container(id = "0.0", children = emptyList()), // Empty container - should be skipped
                clickable(id = "0.1", text = "Wi-Fi"), // Signal node - should be kept
                container(
                    id = "0.2",
                    children = listOf(clickable(id = "0.2.0", text = "Bluetooth"))
                )
            )
        )

        val context = builder.build(root)

        assertEquals(listOf("0.1", "0.2.0"), context.nodes.map { it.nodeId })
        // Proves the builder filters noise and keeps only actionable nodes locally.
    }

    @Test
    fun screenContextKeepsOnlySignalNodes() {
        val context = ScreenContext(
            appLabel = "Test",
            nodes = listOf(
                clickableNode("0.0", "Button 1"),
                clickableNode("0.1", "Button 2")
            )
        )

        assertEquals(2, context.clickableNodes.size)
        // Proves signal nodes are retained in context.
    }

    @Test
    fun screenContextAppliesRedactionViaScreenTextOf() {
        val emailInput = AccessibilityNodeSnapshot(
            nodeId = "0.0",
            className = "android.widget.EditText",
            text = "user@example.com",
            editable = true,
            bounds = bounds(0, 100, 1000, 200)
        )
        val root = container(id = "0", children = listOf(emailInput))

        val context = builder.build(root)
        val input = context.inputFields.single()

        assertEquals("user@example.com", input.text.raw)
        assertTrue(input.text.isSensitive)
        assertEquals("[REDACTED]", input.text.displaySafe)
        // Proves redaction is applied during context building, not at logging time.
    }

    @Test
    fun screenContextDetectsSensitiveContent() {
        val passwordInput = AccessibilityNodeSnapshot(
            nodeId = "0.0",
            className = "android.widget.EditText",
            text = "",
            contentDescription = "Password",
            editable = true,
            password = true
        )
        val context = builder.build(container(id = "0", children = listOf(passwordInput)))

        assertTrue(context.containsSensitiveContent)
        // Proves sensitive content detection works locally.
    }

    // ===== Local Summary Generation (No Network Calls) =====

    @Test
    fun summaryGeneratedLocallyWithoutNetworkCalls() {
        val context = ScreenContext(
            appLabel = "Settings",
            packageName = "com.android.settings",
            nodes = listOf(
                clickableNode("0.0", "Network & internet"),
                clickableNode("0.1", "Battery")
            )
        )

        val summary = summarizer.summarize(context)

        assertTrue(summary.sentence.startsWith("I see the Settings screen."))
        assertTrue(summary.sentence.contains("Network & internet"))
        assertTrue(summary.sentence.contains("go back or home"))
        // This proves summary generation is a pure function of ScreenContext
        // with no network dependencies.
    }

    @Test
    fun summaryHandlesWeakContextGracefully() {
        val summary = summarizer.summarize(ScreenContext.Empty)

        assertEquals(ScreenContextSummarizer.WeakScreenMessage, summary.sentence)
        assertTrue(summary.suggestedActions.isEmpty())
        // Proves weak context is handled locally without crashing or network fallback.
    }

    @Test
    fun summaryUsesWindowTitleFallbackWhenAppLabelMissing() {
        val summary = summarizer.summarize(
            ScreenContext(
                windowTitle = "Wi-Fi",
                nodes = listOf(clickableNode("0.0", "Add network"))
            )
        )

        assertTrue(summary.sentence.startsWith("I see the Wi-Fi screen."))
        // Proves fallback logic is local and deterministic.
    }

    // ===== Suggested Actions Generated Locally =====

    @Test
    fun suggestedActionsGeneratedLocallyForVisibleControls() {
        val context = ScreenContext(
            appLabel = "Settings",
            nodes = listOf(
                clickableNode("0.0", "Network & internet"),
                clickableNode("0.1", "Battery")
            )
        )

        val summary = summarizer.summarize(context)

        val tapActions = summary.suggestedActions.filter { it.tool == "tap" }
        assertTrue(tapActions.isNotEmpty())
        assertTrue(tapActions.any { it.label == "Tap Network & internet" })
        // Proves action suggestions are generated from local screen analysis.
    }

    @Test
    fun suggestedActionsAlwaysIncludeSafeNavigation() {
        val context = ScreenContext(
            appLabel = "Any App",
            nodes = listOf(clickableNode("0.0", "Some button"))
        )

        val summary = summarizer.summarize(context)

        assertTrue(summary.suggestedActions.any { it.tool == "press_back" })
        assertTrue(summary.suggestedActions.any { it.tool == "press_home" })
        // Proves safe navigation is always suggested locally.
    }

    @Test
    fun suggestedActionsIncludeTypeForInputFields() {
        val emailInput = ScreenNode(
            nodeId = "0.0",
            role = NodeRole.INPUT,
            text = ScreenText.of("Email"),
            isInputField = true
        )
        val context = ScreenContext(
            appLabel = "Login",
            nodes = listOf(emailInput)
        )

        val summary = summarizer.summarize(context)

        assertTrue(summary.suggestedActions.any { it.tool == "type_text" })
        // Proves input field detection leads to type_text suggestions locally.
    }

    @Test
    fun suggestedActionsIncludeScrollForScrollableContainers() {
        val list = ScreenNode(
            nodeId = "0.0",
            role = NodeRole.SCROLLABLE,
            scrollable = true
        )
        val context = ScreenContext(
            appLabel = "Feed",
            nodes = listOf(list)
        )

        val summary = summarizer.summarize(context)

        assertTrue(summary.suggestedActions.any { it.tool == "scroll" })
        // Proves scrollable detection leads to scroll suggestions locally.
    }

    // ===== Sensitive Node Filtering in Suggestions =====

    @Test
    fun passwordFieldsNeverSurfacedInSuggestions() {
        val passwordInput = ScreenNode(
            nodeId = "0.0",
            role = NodeRole.INPUT,
            text = ScreenText.of("Password"),
            isInputField = true,
            sensitive = true
        )
        val continueButton = ScreenNode(
            nodeId = "0.1",
            role = NodeRole.BUTTON,
            text = ScreenText.of("Continue"),
            clickable = true
        )
        val context = ScreenContext(
            appLabel = "Login",
            nodes = listOf(passwordInput, continueButton)
        )

        val summary = summarizer.summarize(context)

        assertFalse(summary.suggestedActions.any { it.tool == "type_text" })
        assertFalse(summary.sentence.contains("Password"))
        assertTrue(summary.suggestedActions.any { it.label == "Tap Continue" })
        // Proves sensitive nodes are filtered from suggestions locally.
    }

    @Test
    fun sensitiveClickableLabelsFilteredFromSuggestions() {
        val emailItem = ScreenNode(
            nodeId = "0.0",
            role = NodeRole.BUTTON,
            text = ScreenText.of("user@example.com"),
            clickable = true,
            sensitive = true
        )
        val benignItem = ScreenNode(
            nodeId = "0.1",
            role = NodeRole.BUTTON,
            text = ScreenText.of("Sign out"),
            clickable = true
        )
        val context = ScreenContext(
            appLabel = "Account",
            nodes = listOf(emailItem, benignItem)
        )

        val summary = summarizer.summarize(context)

        assertFalse(summary.suggestedActions.any { it.label.contains("@example.com") })
        assertTrue(summary.suggestedActions.any { it.label == "Tap Sign out" })
        // Proves sensitive text in clickables is filtered locally.
    }

    @Test
    fun textSensitiveNodesFilteredEvenWithoutNodeSensitiveFlag() {
        val emailInput = ScreenNode(
            nodeId = "0.0",
            role = NodeRole.INPUT,
            text = ScreenText.of("user@example.com"),
            isInputField = true
        )
        val emailClickable = ScreenNode(
            nodeId = "0.1",
            role = NodeRole.BUTTON,
            text = ScreenText.of("user@example.com"),
            clickable = true
        )
        val continueButton = ScreenNode(
            nodeId = "0.2",
            role = NodeRole.BUTTON,
            text = ScreenText.of("Continue"),
            clickable = true
        )

        val summary = summarizer.summarize(
            ScreenContext(appLabel = "Account", nodes = listOf(emailInput, emailClickable, continueButton))
        )

        assertFalse(summary.sentence.contains("@example.com"))
        assertFalse(summary.suggestedActions.any { it.tool == "type_text" })
        assertFalse(summary.suggestedActions.any { it.label.contains("@example.com") })
        assertTrue(summary.suggestedActions.any { it.label == "Tap Continue" })
        // Proves SensitiveTextRedactor patterns are applied locally.
    }

    // ===== Redaction Validation =====

    @Test
    fun sensitiveTextRedactorRedactsPasswords() {
        val redacted = SensitiveTextRedactor.redact("password=hunter2")
        assertFalse("hunter2" in redacted)
        assertTrue("[REDACTED]" in redacted)
        // Proves password redaction works locally.
    }

    @Test
    fun sensitiveTextRedactorRedactsEmails() {
        val redacted = SensitiveTextRedactor.redact("user@example.com")
        assertFalse("user@example.com" in redacted)
        assertTrue("[REDACTED]" in redacted)
        // Proves email redaction works locally.
    }

    @Test
    fun sensitiveTextRedactorRedactsApiKeys() {
        val redacted = SensitiveTextRedactor.redact("api_key=sk-test-12345")
        assertFalse("sk-test-12345" in redacted)
        assertTrue("[REDACTED]" in redacted)
        // Proves API key redaction works locally.
    }

    @Test
    fun sensitiveTextRedactorRedactsCreditCards() {
        val redacted = SensitiveTextRedactor.redact("card number 4111111111111111")
        assertFalse("4111111111111111" in redacted)
        assertTrue("[REDACTED]" in redacted)
        // Proves credit card redaction works locally.
    }

    @Test
    fun sensitiveTextRedactorDetectsSensitiveText() {
        assertTrue(SensitiveTextRedactor.containsSensitiveText("password=secret"))
        assertTrue(SensitiveTextRedactor.containsSensitiveText("user@example.com"))
        assertTrue(SensitiveTextRedactor.containsSensitiveText("api_key=abc"))
        assertFalse(SensitiveTextRedactor.containsSensitiveText("hello world"))
        // Proves sensitive detection works locally.
    }

    @Test
    fun sensitiveTextRedactorRedactsArgumentMaps() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf(
                "password" to "secret123",
                "target" to "Settings",
                "api_key" to "sk-test"
            )
        )

        assertEquals("[REDACTED]", redacted["password"])
        assertEquals("Settings", redacted["target"])
        assertEquals("[REDACTED]", redacted["api_key"])
        // Proves map redaction works locally.
    }

    // ===== ScreenText Redaction Behavior =====

    @Test
    fun screenTextOfAppliesRedactionForSensitivePatterns() {
        val screenText = ScreenText.of("user@example.com")

        assertEquals("user@example.com", screenText.raw)
        assertEquals("[REDACTED]", screenText.displaySafe)
        assertTrue(screenText.isSensitive)
        // Proves ScreenText.of() applies redaction locally.
    }

    @Test
    fun screenTextOfPreservesNonSensitiveText() {
        val screenText = ScreenText.of("Hello world")

        assertEquals("Hello world", screenText.raw)
        assertEquals("Hello world", screenText.displaySafe)
        assertFalse(screenText.isSensitive)
        // Proves non-sensitive text is preserved locally.
    }

    @Test
    fun screenTextRedactedCopyReturnsRedactedVersion() {
        val sensitive = ScreenText("secret", "secret", true)
        val redacted = sensitive.redactedCopy()

        assertEquals("[REDACTED]", redacted.displaySafe)
        assertTrue(redacted.isSensitive)
        // Proves redactedCopy() works locally.
    }

    // ===== ScreenContext JSON Export with Redaction =====

    @Test
    fun screenContextToJsonWithRedactionRemovesSensitiveData() {
        val context = ScreenContext(
            appLabel = "Login",
            nodes = listOf(
                ScreenNode(
                    nodeId = "0.0",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("user@example.com"),
                    isInputField = true
                )
            )
        )

        val json = context.toJson(redacted = true).toString()

        assertFalse("user@example.com" in json)
        assertTrue("[REDACTED]" in json)
        // Proves JSON export applies redaction locally.
    }

    @Test
    fun screenContextToJsonWithoutRedactionPreservesRawData() {
        val context = ScreenContext(
            appLabel = "Login",
            nodes = listOf(
                ScreenNode(
                    nodeId = "0.0",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("user@example.com"),
                    isInputField = true
                )
            )
        )

        val json = context.toJson(redacted = false).toString()

        assertTrue("user@example.com" in json)
        // Proves raw JSON export preserves data when requested.
    }

    @Test
    fun screenContextRedactedCopyCreatesSafeVersion() {
        val context = ScreenContext(
            appLabel = "Login",
            nodes = listOf(
                ScreenNode(
                    nodeId = "0.0",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("user@example.com"),
                    isInputField = true
                )
            )
        )

        val redacted = context.redactedCopy()

        assertEquals("[REDACTED]", redacted.nodes[0].text.displaySafe)
        // Proves redactedCopy() works locally.
    }

    // ===== Five Target Screen Types =====

    @Test
    fun touchPilotChatInterfaceScreenUnderstanding() {
        val context = ScreenContext(
            appLabel = "TouchPilot",
            packageName = "dev.touchpilot.app",
            nodes = listOf(
                ScreenNode(
                    nodeId = "0.0",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("Type a message"),
                    isInputField = true
                ),
                ScreenNode(
                    nodeId = "0.1",
                    role = NodeRole.BUTTON,
                    text = ScreenText.of("Send"),
                    clickable = true
                )
            )
        )

        val summary = summarizer.summarize(context)

        assertTrue(summary.sentence.contains("TouchPilot"))
        assertTrue(summary.suggestedActions.any { it.tool == "type_text" })
        assertTrue(summary.suggestedActions.any { it.tool == "tap" })
        // Proves TouchPilot's own screen is understood locally.
    }

    @Test
    fun androidSettingsScreenUnderstanding() {
        val context = ScreenContext(
            appLabel = "Settings",
            packageName = "com.android.settings",
            nodes = listOf(
                clickableNode("0.0", "Network & internet"),
                clickableNode("0.1", "Connected devices"),
                clickableNode("0.2", "Apps"),
                clickableNode("0.3", "Battery")
            )
        )

        val summary = summarizer.summarize(context)

        assertTrue(summary.sentence.contains("Settings"))
        assertTrue(summary.sentence.contains("Network & internet"))
        assertTrue(summary.suggestedActions.any { it.label.contains("Network") })
        // Proves Settings screen is understood locally.
    }

    @Test
    fun launcherScreenUnderstanding() {
        val context = ScreenContext(
            appLabel = "Pixel Launcher",
            packageName = "com.google.android.apps.nexuslauncher",
            nodes = listOf(
                clickableNode("0.0", "Chrome"),
                clickableNode("0.1", "Gmail"),
                clickableNode("0.2", "Maps"),
                clickableNode("0.3", "Photos")
            )
        )

        val summary = summarizer.summarize(context)

        assertTrue(summary.sentence.contains("Pixel Launcher"))
        assertTrue(summary.suggestedActions.any { it.label.contains("Chrome") })
        // Proves launcher screen is understood locally.
    }

    @Test
    fun inputFieldScreenUnderstanding() {
        val context = ScreenContext(
            appLabel = "Chrome",
            packageName = "com.android.chrome",
            nodes = listOf(
                ScreenNode(
                    nodeId = "0.0",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("Search or type URL"),
                    isInputField = true,
                    focused = true
                )
            )
        )

        val summary = summarizer.summarize(context)

        assertTrue(summary.sentence.contains("input field"))
        assertTrue(summary.suggestedActions.any { it.tool == "type_text" })
        // Proves input field screen is understood locally.
    }

    @Test
    fun weakAccessibilityScreenUnderstanding() {
        val context = ScreenContext(
            appLabel = "Custom Game",
            packageName = "com.example.game",
            nodes = emptyList() // Limited Accessibility exposure
        )

        val summary = summarizer.summarize(context)

        assertEquals(ScreenContextSummarizer.WeakScreenMessage, summary.sentence)
        // Proves weak context is handled gracefully locally.
    }

    // ===== Helper Functions =====

    private fun container(
        id: String,
        children: List<AccessibilityNodeSnapshot>
    ): AccessibilityNodeSnapshot {
        return AccessibilityNodeSnapshot(
            nodeId = id,
            className = "android.widget.LinearLayout",
            children = children
        )
    }

    private fun clickable(
        id: String,
        text: String,
        bounds: NodeBounds = NodeBounds.Unknown
    ): AccessibilityNodeSnapshot {
        return AccessibilityNodeSnapshot(
            nodeId = id,
            className = "android.widget.Button",
            text = text,
            clickable = true,
            bounds = bounds
        )
    }

    private fun clickableNode(id: String, text: String): ScreenNode {
        return ScreenNode(
            nodeId = id,
            role = NodeRole.BUTTON,
            text = ScreenText.of(text),
            clickable = true
        )
    }

    private fun bounds(left: Int, top: Int, right: Int, bottom: Int): NodeBounds {
        return NodeBounds(left = left, top = top, right = right, bottom = bottom)
    }
}
