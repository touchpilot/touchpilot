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

    @Test
    fun screenContextToJsonAndBack() {
        val context = ScreenContext(
            appLabel = "Gmail",
            packageName = "com.google.android.gm",
            windowTitle = "Inbox",
            nodes = listOf(
                node("compose", role = NodeRole.BUTTON, clickable = true, text = "Compose"),
                node("search", role = NodeRole.INPUT, isInputField = true, text = "Search")
            )
        )

        val json = context.toJson(redacted = false)
        val restored = ScreenContext.fromJson(json)

        assertEquals(context.appLabel, restored.appLabel)
        assertEquals(context.packageName, restored.packageName)
        assertEquals(context.windowTitle, restored.windowTitle)
        assertEquals(context.nodes.size, restored.nodes.size)
    }

    @Test
    fun screenContextRedactedJsonHidesSensitiveText() {
        val context = ScreenContext(
            appLabel = "Banking App",
            packageName = "com.example.bank",
            windowTitle = "Login",
            nodes = listOf(
                node("password", role = NodeRole.INPUT, isInputField = true, text = "password: hunter2")
            )
        )

        val json = context.toRedactedJson()
        assertTrue(json.contains("[REDACTED]"))
        assertFalse(json.contains("hunter2"))
    }

    @Test
    fun screenContextRedactedCopy() {
        val context = ScreenContext(
            nodes = listOf(
                node("pw", role = NodeRole.INPUT, isInputField = true, text = "password: secret123")
            )
        )

        val redacted = context.redactedCopy()
        assertEquals("[REDACTED]", redacted.nodes[0].text.displaySafe)
        assertEquals("password: secret123", redacted.nodes[0].text.raw)
    }

    @Test
    fun screenNodeToJsonAndBack() {
        val node = ScreenNode(
            nodeId = "node123",
            role = NodeRole.BUTTON,
            text = ScreenText.of("Click me"),
            bounds = NodeBounds(left = 10, top = 20, right = 110, bottom = 70),
            clickable = true,
            enabled = true,
            viewIdResourceName = "com.example:id/button"
        )

        val json = node.toJson(redacted = false)
        val restored = ScreenNode.fromJson(json)

        assertEquals(node.nodeId, restored.nodeId)
        assertEquals(node.role, restored.role)
        assertEquals(node.text.raw, restored.text.raw)
        assertEquals(node.bounds, restored.bounds)
    }

    @Test
    fun screenTextToJsonAndBack() {
        val text = ScreenText.of("Hello World")
        val json = text.toJson(redacted = false)
        val restored = ScreenText.fromJson(json)

        assertEquals(text.raw, restored.raw)
        assertEquals(text.displaySafe, restored.displaySafe)
        assertEquals(text.isSensitive, restored.isSensitive)
    }

    @Test
    fun nodeBoundsToJsonAndBack() {
        val bounds = NodeBounds(left = 10, top = 20, right = 110, bottom = 70)
        val json = bounds.toJson()
        val restored = NodeBounds.fromJson(json)

        assertEquals(bounds.left, restored.left)
        assertEquals(bounds.top, restored.top)
        assertEquals(bounds.right, restored.right)
        assertEquals(bounds.bottom, restored.bottom)
        assertEquals(bounds.width, restored.width)
        assertEquals(bounds.height, restored.height)
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
