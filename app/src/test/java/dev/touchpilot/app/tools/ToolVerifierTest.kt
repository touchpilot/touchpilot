package dev.touchpilot.app.tools

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ToolVerifierTest {
    private val verifier = ToolVerifier()

    @Test
    fun openAppPassesWhenForegroundPackageMatches() {
        val result = verifier.verify(
            toolName = "open_app",
            args = mapOf("target" to "com.android.settings"),
            result = ToolResult(ok = true, message = "openApp"),
            before = screen(packageName = "dev.touchpilot.app"),
            after = screen(packageName = "com.android.settings", appLabel = "Settings"),
        )

        val passed = assertIs<ToolVerificationResult.Passed>(result)
        assertEquals("passed", passed.status.wireName)
    }

    @Test
    fun openAppFailsWhenForegroundPackageDoesNotMatch() {
        val result = verifier.verify(
            toolName = "open_app",
            args = mapOf("target" to "Settings"),
            result = ToolResult(ok = true, message = "openApp"),
            before = screen(packageName = "dev.touchpilot.app"),
            after = screen(packageName = "com.example.other", appLabel = "Other"),
        )

        val failed = assertIs<ToolVerificationResult.Failed>(result)
        assertTrue(failed.reason.contains("foreground app did not match"))
    }

    @Test
    fun tapPassesWhenScreenChanges() {
        val result = verifier.verify(
            toolName = "tap",
            args = mapOf("text" to "Settings"),
            result = ToolResult(ok = true, message = "tap"),
            before = screen(nodes = listOf(button("0", "Settings"))),
            after = screen(nodes = listOf(button("0", "Network"))),
        )

        assertIs<ToolVerificationResult.Passed>(result)
    }

    @Test
    fun tapFailsWhenScreenAndFocusDoNotChange() {
        val before = screen(nodes = listOf(button("0", "Settings")))
        val result = verifier.verify(
            toolName = "tap",
            args = mapOf("text" to "Settings"),
            result = ToolResult(ok = true, message = "tap"),
            before = before,
            after = before,
        )

        assertIs<ToolVerificationResult.Failed>(result)
    }

    @Test
    fun typeTextPassesWhenInputContainsTypedText() {
        val result = verifier.verify(
            toolName = "type_text",
            args = mapOf("text" to "hello"),
            result = ToolResult(ok = true, message = "typeIntoFocusedField"),
            before = screen(nodes = listOf(input("0", ""))),
            after = screen(nodes = listOf(input("0", "hello", focused = true))),
        )

        val passed = assertIs<ToolVerificationResult.Passed>(result)
        assertEquals("5", passed.data["text_length"])
    }

    @Test
    fun typeTextFailsWithoutRawTextInVerificationData() {
        val result = verifier.verify(
            toolName = "type_text",
            args = mapOf("text" to "user@example.com"),
            result = ToolResult(ok = true, message = "typeIntoFocusedField"),
            before = screen(nodes = listOf(input("0", ""))),
            after = screen(nodes = listOf(input("0", "", focused = true))),
        )

        val failed = assertIs<ToolVerificationResult.Failed>(result)
        assertEquals("16", failed.data["text_length"])
        assertFalse(failed.data.values.any { "user@example.com" in it })
    }

    @Test
    fun scrollUsesExistingScreenChangedData() {
        val result = verifier.verify(
            toolName = "scroll",
            args = mapOf("direction" to "forward"),
            result = ToolResult(ok = true, message = "scroll", data = mapOf("screen_changed" to "true")),
            before = screen(),
            after = screen(),
        )

        assertIs<ToolVerificationResult.Passed>(result)
    }

    @Test
    fun waitForUiPassesWhenExpectedTextIsPresent() {
        val result = verifier.verify(
            toolName = "wait_for_ui",
            args = mapOf("text" to "Connected devices"),
            result = ToolResult(ok = true, message = "waitForText"),
            before = screen(),
            after = screen(nodes = listOf(button("0", "Connected devices"))),
        )

        assertIs<ToolVerificationResult.Passed>(result)
    }

    @Test
    fun getForegroundAppPassesWhenServiceConnectedAndPackagePresent() {
        val result = verifier.verify(
            toolName = "get_foreground_app",
            args = emptyMap(),
            result = ToolResult(ok = true, message = "getForegroundApp", data = mapOf(
                "package_name" to "com.android.settings",
                "app_label" to "Settings",
                "window_title" to "Settings",
                "service_connected" to "true",
            )),
            before = screen(),
            after = screen(),
        )

        val passed = assertIs<ToolVerificationResult.Passed>(result)
        assertEquals("passed", passed.status.wireName)
    }

    @Test
    fun getForegroundAppFailsWhenServiceNotConnected() {
        val result = verifier.verify(
            toolName = "get_foreground_app",
            args = emptyMap(),
            result = ToolResult(ok = false, message = "getForegroundApp", data = mapOf(
                "package_name" to "",
                "service_connected" to "false",
            )),
            before = screen(),
            after = screen(),
        )

        val failed = assertIs<ToolVerificationResult.Failed>(result)
        assertTrue(failed.reason.contains("not connected"))
    }

    @Test
    fun skippedWhenToolAlreadyFailed() {
        val result = verifier.verify(
            toolName = "tap",
            args = mapOf("text" to "Settings"),
            result = ToolResult(ok = false, message = "tap failed"),
            before = screen(),
            after = screen(),
        )

        assertIs<ToolVerificationResult.Skipped>(result)
    }

    private fun screen(
        packageName: String = "dev.touchpilot.app",
        appLabel: String = "TouchPilot",
        nodes: List<ScreenNode> = emptyList(),
    ): ScreenContext {
        return ScreenContext(
            appLabel = appLabel,
            packageName = packageName,
            nodes = nodes,
        )
    }

    private fun button(id: String, text: String): ScreenNode {
        return ScreenNode(
            nodeId = id,
            role = NodeRole.BUTTON,
            text = ScreenText.of(text),
            bounds = NodeBounds(0, 0, 100, 100),
            clickable = true,
        )
    }

    private fun input(id: String, text: String, focused: Boolean = false): ScreenNode {
        return ScreenNode(
            nodeId = id,
            role = NodeRole.INPUT,
            text = ScreenText.of(text),
            bounds = NodeBounds(0, 0, 100, 100),
            focused = focused,
            isInputField = true,
        )
    }
}
