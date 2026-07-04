package dev.touchpilot.app.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenContextBuilderTest {
    private val builder = ScreenContextBuilder()

    @Test
    fun settingsLikeScreenExposesClickableEntriesWithStableNodeIds() {
        val root = container(
            id = "0",
            children = listOf(
                clickable(id = "0.0", text = "Network & internet", bounds = bounds(0, 100, 1000, 200)),
                clickable(id = "0.1", text = "Connected devices", bounds = bounds(0, 200, 1000, 300)),
                clickable(id = "0.2", text = "Apps", bounds = bounds(0, 300, 1000, 400)),
                clickable(id = "0.3", text = "Notifications", bounds = bounds(0, 400, 1000, 500)),
                clickable(id = "0.4", text = "Battery", bounds = bounds(0, 500, 1000, 600))
            )
        )

        val context = builder.build(
            root = root,
            appLabel = "Settings",
            packageName = "com.android.settings",
            windowTitle = "Settings"
        )

        assertEquals("Settings", context.appLabel)
        assertEquals("com.android.settings", context.packageName)
        assertEquals(5, context.clickableNodes.size)
        assertEquals(
            listOf("0.0", "0.1", "0.2", "0.3", "0.4"),
            context.clickableNodes.map { it.nodeId }
        )
        assertEquals(
            listOf("Network & internet", "Connected devices", "Apps", "Notifications", "Battery"),
            context.clickableNodes.map { it.text.raw }
        )
        assertTrue(context.clickableNodes.all { it.role == NodeRole.BUTTON })
        assertEquals(100, context.clickableNodes[0].bounds.top)
    }

    @Test
    fun emptyContainersBetweenSignalNodesAreSkipped() {
        val root = container(
            id = "0",
            children = listOf(
                container(id = "0.0", children = emptyList()),
                clickable(id = "0.1", text = "Wi-Fi"),
                container(
                    id = "0.2",
                    children = listOf(clickable(id = "0.2.0", text = "Bluetooth"))
                )
            )
        )

        val context = builder.build(root)

        assertEquals(
            listOf("0.1", "0.2.0"),
            context.nodes.map { it.nodeId }
        )
    }

    @Test
    fun inputFieldsAreExtractedWithInputRole() {
        val emailInput = AccessibilityNodeSnapshot(
            nodeId = "0.0",
            className = "android.widget.EditText",
            text = "user@example.com",
            editable = true,
            bounds = bounds(0, 100, 1000, 200)
        )
        val root = container(id = "0", children = listOf(emailInput))

        val context = builder.build(root)
        val input = assertNotNull(context.inputFields.singleOrNull())
        assertEquals(NodeRole.INPUT, input.role)
        assertTrue(input.isInputField)
        assertEquals("user@example.com", input.text.raw)
        // Email trips the SensitiveTextRedactor value patterns.
        assertTrue(input.text.isSensitive)
        assertEquals("[REDACTED]", input.text.displaySafe)
    }

    @Test
    fun passwordFieldsAreFlaggedSensitiveEvenWhenEmpty() {
        val passwordInput = AccessibilityNodeSnapshot(
            nodeId = "0.0",
            className = "android.widget.EditText",
            text = "",
            contentDescription = "Password",
            editable = true,
            password = true,
            focused = true
        )
        val context = builder.build(container(id = "0", children = listOf(passwordInput)))

        val node = assertNotNull(context.inputFields.singleOrNull())
        assertTrue(node.sensitive)
        assertTrue(context.containsSensitiveContent)
        // contentDescription is used as fallback when text is blank.
        assertEquals("Password", node.text.raw)
    }

    @Test
    fun passwordFieldTypedContentIsRedactedInExportEvenWhenBenign() {
        val passwordInput = AccessibilityNodeSnapshot(
            nodeId = "0.0",
            className = "android.widget.EditText",
            text = "MySecret1",
            editable = true,
            password = true,
            focused = true,
            bounds = bounds(0, 100, 1000, 200)
        )
        val context = builder.build(container(id = "0", children = listOf(passwordInput)))
        val node = assertNotNull(context.inputFields.singleOrNull())

        assertTrue(node.sensitive)
        assertTrue(node.text.isSensitive)
        assertEquals("MySecret1", node.text.raw)
        assertEquals("[REDACTED]", node.text.displaySafe)

        val json = context.toRedactedJson()
        assertTrue(json.contains("[REDACTED]"), json)
        assertFalse(json.contains("MySecret1"), "password field content leaked: $json")
    }

    @Test
    fun scrollableContainerIsRetainedWithScrollableRole() {
        val list = AccessibilityNodeSnapshot(
            nodeId = "0",
            className = "androidx.recyclerview.widget.RecyclerView",
            scrollable = true,
            children = listOf(
                clickable(id = "0.0", text = "Item 1"),
                clickable(id = "0.1", text = "Item 2")
            )
        )

        val context = builder.build(list)
        val scrollable = assertNotNull(context.scrollableNodes.singleOrNull())
        assertEquals(NodeRole.SCROLLABLE, scrollable.role)
        assertEquals(2, context.clickableNodes.size)
    }

    @Test
    fun checkableNodeReflectsCheckedState() {
        val toggleOn = AccessibilityNodeSnapshot(
            nodeId = "0.0",
            className = "android.widget.Switch",
            text = "Airplane mode",
            clickable = true,
            checkable = true,
            checked = true
        )
        val toggleOff = AccessibilityNodeSnapshot(
            nodeId = "0.1",
            className = "android.widget.Switch",
            text = "Bluetooth",
            clickable = true,
            checkable = true,
            checked = false
        )
        val plainButton = clickable(id = "0.2", text = "Save")

        val context = builder.build(
            container(id = "0", children = listOf(toggleOn, toggleOff, plainButton))
        )

        assertEquals(true, context.nodes[0].checked)
        assertEquals(false, context.nodes[1].checked)
        // Non-checkable nodes don't report a checked state.
        assertNull(context.nodes[2].checked)
    }

    @Test
    fun textOnlyNodesAreExtractedAsTextRole() {
        val heading = AccessibilityNodeSnapshot(
            nodeId = "0.0",
            className = "android.widget.TextView",
            text = "Networks"
        )
        val context = builder.build(container(id = "0", children = listOf(heading)))
        val node = context.nodes.single()
        assertEquals(NodeRole.TEXT, node.role)
        assertFalse(node.clickable)
        assertEquals("Networks", node.text.raw)
    }

    @Test
    fun maxDepthLimitStopsTraversal() {
        val deep = container(
            id = "0",
            children = listOf(
                container(
                    id = "0.0",
                    children = listOf(
                        container(
                            id = "0.0.0",
                            children = listOf(clickable(id = "0.0.0.0", text = "Too deep"))
                        )
                    )
                )
            )
        )

        val shallowBuilder = ScreenContextBuilder(maxDepth = 2)
        val context = shallowBuilder.build(deep)

        // depth 0 = root container (empty -> skipped),
        // depth 1 = container (empty -> skipped),
        // depth 2 = container with clickable child,
        // depth 3 would be the clickable but is beyond maxDepth.
        assertTrue(context.nodes.isEmpty())
    }

    @Test
    fun emptyRootProducesContextWithoutNodes() {
        val context = builder.build(container(id = "0", children = emptyList()))
        assertTrue(context.nodes.isEmpty())
        assertFalse(context.containsSensitiveContent)
    }

    @Test
    fun contentDescriptionIsFallbackTextWhenNodeHasNoTextLabel() {
        val iconButton = AccessibilityNodeSnapshot(
            nodeId = "0.0",
            className = "android.widget.ImageButton",
            contentDescription = "Back",
            clickable = true
        )
        val context = builder.build(container(id = "0", children = listOf(iconButton)))
        val node = context.nodes.single()
        assertEquals("Back", node.text.raw)
        assertEquals(NodeRole.BUTTON, node.role)
    }

    @Test
    fun focusedNodeIsRetainedEvenWithoutTextOrClickability() {
        val focused = AccessibilityNodeSnapshot(
            nodeId = "0.0",
            className = "android.view.View",
            focused = true
        )
        val context = builder.build(container(id = "0", children = listOf(focused)))
        assertEquals(1, context.nodes.size)
        assertTrue(context.nodes[0].focused)
    }

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

    private fun bounds(left: Int, top: Int, right: Int, bottom: Int): NodeBounds {
        return NodeBounds(left = left, top = top, right = right, bottom = bottom)
    }
}
