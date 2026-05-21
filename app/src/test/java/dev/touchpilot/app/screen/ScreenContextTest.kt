package dev.touchpilot.app.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenContextTest {
    @Test
    fun emptyContextHasNoNodesAndNoMetadata() {
        val context = ScreenContext.Empty
        assertTrue(context.nodes.isEmpty())
        assertEquals(null, context.appLabel)
        assertEquals(null, context.packageName)
        assertEquals(null, context.windowTitle)
        assertFalse(context.containsSensitiveContent)
    }

    @Test
    fun clickableInputAndScrollableFiltersReturnMatchingNodesOnly() {
        val button = node("button", clickable = true, role = NodeRole.BUTTON, text = "Open")
        val input = node("input", isInputField = true, role = NodeRole.INPUT, text = "Search")
        val list = node("list", scrollable = true, role = NodeRole.SCROLLABLE)
        val label = node("label", role = NodeRole.TEXT, text = "Settings")

        val context = ScreenContext(nodes = listOf(button, input, list, label))

        assertEquals(listOf(button), context.clickableNodes)
        assertEquals(listOf(input), context.inputFields)
        assertEquals(listOf(list), context.scrollableNodes)
    }

    @Test
    fun containsSensitiveContentReflectsTextDetectionAndExplicitFlag() {
        val passwordText = node("pw", text = "password: hunter2")
        val explicitlySensitive = node("secret", sensitive = true)
        val benign = node("ok", text = "Save")

        assertTrue(ScreenContext(nodes = listOf(passwordText)).containsSensitiveContent)
        assertTrue(ScreenContext(nodes = listOf(explicitlySensitive)).containsSensitiveContent)
        assertFalse(ScreenContext(nodes = listOf(benign)).containsSensitiveContent)
    }

    @Test
    fun screenTextOfReturnsEmptyForEmptyInput() {
        assertEquals(ScreenText.Empty, ScreenText.of(""))
    }

    @Test
    fun screenTextOfLeavesBenignTextAloneInDisplaySafe() {
        val text = ScreenText.of("Open Settings")
        assertEquals("Open Settings", text.raw)
        assertEquals("Open Settings", text.displaySafe)
        assertFalse(text.isSensitive)
    }

    @Test
    fun screenTextOfMarksAndRedactsSensitiveText() {
        val text = ScreenText.of("password: hunter2")
        assertEquals("password: hunter2", text.raw)
        assertEquals("[REDACTED]", text.displaySafe)
        assertTrue(text.isSensitive)
    }

    @Test
    fun screenTextOfFlagsEmbeddedEmailAsSensitive() {
        // SensitiveTextRedactor.containsSensitiveText treats embedded emails
        // (and other value-pattern matches like long digit runs or OTP-shaped
        // numbers) as sensitive, so the displaySafe view collapses the whole
        // text to "[REDACTED]". Raw is preserved for tool-execution paths.
        val text = ScreenText.of("Contact us at help@example.com for support")
        assertTrue(text.isSensitive)
        assertEquals("[REDACTED]", text.displaySafe)
        assertEquals("Contact us at help@example.com for support", text.raw)
    }

    @Test
    fun nodeBoundsComputesWidthHeightCenterAndEmptiness() {
        val bounds = NodeBounds(left = 10, top = 20, right = 110, bottom = 220)
        assertEquals(100, bounds.width)
        assertEquals(200, bounds.height)
        assertEquals(60, bounds.centerX)
        assertEquals(120, bounds.centerY)
        assertFalse(bounds.isEmpty)
    }

    @Test
    fun nodeBoundsUnknownIsEmpty() {
        assertTrue(NodeBounds.Unknown.isEmpty)
        assertEquals(0, NodeBounds.Unknown.width)
        assertEquals(0, NodeBounds.Unknown.height)
    }

    @Test
    fun zeroDimensionBoundsAreEmpty() {
        assertTrue(NodeBounds(left = 5, top = 5, right = 5, bottom = 25).isEmpty)
        assertTrue(NodeBounds(left = 5, top = 5, right = 25, bottom = 5).isEmpty)
    }

    @Test
    fun screenNodeDefaultsAreSafe() {
        val n = ScreenNode()
        assertEquals(null, n.nodeId)
        assertEquals(NodeRole.OTHER, n.role)
        assertEquals(ScreenText.Empty, n.text)
        assertEquals(NodeBounds.Unknown, n.bounds)
        assertFalse(n.clickable)
        assertFalse(n.isInputField)
        assertFalse(n.sensitive)
        assertTrue(n.enabled)
    }

    @Test
    fun contextSurfacesAppAndWindowMetadata() {
        val context = ScreenContext(
            appLabel = "Settings",
            packageName = "com.android.settings",
            windowTitle = "Network & internet",
            nodes = listOf(node("wifi", clickable = true, text = "Wi-Fi"))
        )
        assertEquals("Settings", context.appLabel)
        assertEquals("com.android.settings", context.packageName)
        assertEquals("Network & internet", context.windowTitle)
        assertEquals(1, context.clickableNodes.size)
    }

    private fun node(
        id: String,
        role: NodeRole = NodeRole.OTHER,
        text: String = "",
        clickable: Boolean = false,
        isInputField: Boolean = false,
        scrollable: Boolean = false,
        sensitive: Boolean = false
    ): ScreenNode {
        return ScreenNode(
            nodeId = id,
            role = role,
            text = ScreenText.of(text),
            clickable = clickable,
            isInputField = isInputField,
            scrollable = scrollable,
            sensitive = sensitive
        )
    }
}
