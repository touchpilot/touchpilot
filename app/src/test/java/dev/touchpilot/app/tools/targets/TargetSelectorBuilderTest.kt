package dev.touchpilot.app.tools.targets

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetSelectorBuilderTest {
    // --- fromLegacyArgs ---

    @Test
    fun fromLegacyArgs_textOnly_buildsTextSelector() {
        val selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("text" to "Settings"))
        assertEquals("Settings", selector.text?.raw)
        assertFalse(selector.text!!.isSensitive)
        assertNull(selector.nodeId)
        assertNull(selector.bounds)
        assertEquals(SelectorSource.USER, selector.source)
        assertTrue(selector.isValid())
    }

    @Test
    fun fromLegacyArgs_sensitiveTextIsFlagged() {
        val selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("text" to "Enter your password"))
        assertNotNull(selector.text)
        assertTrue(selector.text!!.isSensitive)
        assertTrue(selector.containsSensitiveText)
    }

    @Test
    fun fromLegacyArgs_nodeIdOnly_buildsNodeIdSelector() {
        val selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("node_id" to "0.2.1"))
        assertEquals("0.2.1", selector.nodeId)
        assertNull(selector.text)
        assertNull(selector.bounds)
        assertTrue(selector.isValid())
    }

    @Test
    fun fromLegacyArgs_boundsOnly_buildsBoundsSelector() {
        val selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("bounds" to "10,20,110,220"))
        assertEquals(TargetBounds(10, 20, 110, 220), selector.bounds)
        assertTrue(selector.isValid())
    }

    @Test
    fun fromLegacyArgs_boundsBracketFormat_isParsed() {
        val selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("bounds" to "[0,0][100,200]"))
        assertEquals(TargetBounds(0, 0, 100, 200), selector.bounds)
    }

    @Test
    fun fromLegacyArgs_malformedBounds_droppedSilently() {
        val selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("bounds" to "garbage,1,2"))
        // Result is an empty selector — caller can detect via isValid().
        assertNull(selector.bounds)
        assertFalse(selector.isValid())
    }

    @Test
    fun fromLegacyArgs_blankValues_ignored() {
        val selector = TargetSelectorBuilder.fromLegacyArgs(
            mapOf("text" to "  ", "node_id" to "", "bounds" to "   ")
        )
        assertFalse(selector.isValid())
    }

    @Test
    fun fromLegacyArgs_emptyMap_buildsEmptySelector() {
        val selector = TargetSelectorBuilder.fromLegacyArgs(emptyMap())
        assertFalse(selector.isValid())
        assertEquals(SelectorSource.USER, selector.source)
    }

    @Test
    fun fromLegacyArgs_combined_carriesAllProvidedDimensions() {
        val selector = TargetSelectorBuilder.fromLegacyArgs(
            mapOf(
                "text" to "Settings",
                "node_id" to "0.3.2",
                "bounds" to "5,5,55,55",
            )
        )
        assertEquals("Settings", selector.text?.raw)
        assertEquals("0.3.2", selector.nodeId)
        assertEquals(TargetBounds(5, 5, 55, 55), selector.bounds)
    }

    // --- fromScreenNode ---

    @Test
    fun fromScreenNode_basicNode_buildsObservationSelector() {
        val node = ScreenNode(
            nodeId = "0.1",
            role = NodeRole.BUTTON,
            text = ScreenText.of("Battery"),
            bounds = NodeBounds(10, 20, 110, 220),
            clickable = true,
            viewIdResourceName = "com.android.settings:id/battery_row",
            className = "android.widget.LinearLayout",
        )
        val selector = TargetSelectorBuilder.fromScreenNode(
            node = node,
            packageName = "com.android.settings",
            windowTitle = "Settings",
            confidence = 0.85f,
        )
        assertEquals("Battery", selector.text?.raw)
        assertEquals("0.1", selector.nodeId)
        assertEquals(TargetBounds(10, 20, 110, 220), selector.bounds)
        assertEquals("com.android.settings:id/battery_row", selector.viewIdResourceName)
        assertEquals(TargetRole.BUTTON, selector.role)
        assertEquals("com.android.settings", selector.packageName)
        assertEquals("Settings", selector.windowTitle)
        assertEquals(0.85f, selector.confidence)
        assertEquals(SelectorSource.OBSERVATION, selector.source)
        assertTrue(selector.isValid())
    }

    @Test
    fun fromScreenNode_blankText_isOmitted() {
        val node = ScreenNode(
            nodeId = "0.1",
            text = ScreenText.of(""),
            bounds = NodeBounds(0, 0, 100, 100),
        )
        val selector = TargetSelectorBuilder.fromScreenNode(node)
        assertNull(selector.text)
        // The selector is still valid via nodeId + bounds.
        assertTrue(selector.isValid())
    }

    @Test
    fun fromScreenNode_zeroBounds_isOmitted() {
        val node = ScreenNode(
            nodeId = "0.2",
            text = ScreenText.of("Save"),
            bounds = NodeBounds(0, 0, 0, 0),
        )
        val selector = TargetSelectorBuilder.fromScreenNode(node)
        assertNull(selector.bounds)
        assertTrue(selector.isValid())
    }

    @Test
    fun fromScreenNode_sensitiveText_propagatesFlag() {
        val node = ScreenNode(
            nodeId = "0.3",
            text = ScreenText.of("Enter your password"),
            bounds = NodeBounds(0, 0, 100, 100),
        )
        val selector = TargetSelectorBuilder.fromScreenNode(node)
        assertTrue(selector.text!!.isSensitive)
        assertTrue(selector.containsSensitiveText)
    }

    @Test
    fun fromScreenNode_roleMappingCoversAllValues() {
        // Mapping must be total, otherwise a future NodeRole gets silently
        // dropped to null on selector emission.
        for (role in NodeRole.values()) {
            val node = ScreenNode(
                nodeId = "0",
                role = role,
                text = ScreenText.of("Label"),
                bounds = NodeBounds(0, 0, 1, 1),
            )
            val mapped = TargetSelectorBuilder.fromScreenNode(node).role
            assertNotNull(mapped, "NodeRole.$role must map to a TargetRole")
        }
    }

    @Test
    fun fromScreenNode_customSourceIsHonored() {
        val node = ScreenNode(
            nodeId = "0",
            text = ScreenText.of("Settings"),
            bounds = NodeBounds(0, 0, 10, 10),
        )
        val selector = TargetSelectorBuilder.fromScreenNode(
            node = node,
            source = SelectorSource.MODEL,
        )
        assertEquals(SelectorSource.MODEL, selector.source)
    }
}
