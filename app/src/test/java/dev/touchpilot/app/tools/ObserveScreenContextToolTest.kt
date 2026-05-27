package dev.touchpilot.app.tools

import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers the contract behind the `observe_screen_context` tool: catalog
 * registration plus the exact serialization path the executor returns
 * (`ScreenContext.toRedactedJson`). The executor case itself needs a live
 * Context/AccessibilityService and is exercised via manual device testing.
 */
class ObserveScreenContextToolTest {
    @Test
    fun registersObserveScreenContextAsLowRiskObservation() {
        val spec = AndroidToolCatalog.find("observe_screen_context")
        assertNotNull(spec)
        assertEquals(ToolRisk.LOW, spec.risk)
        assertTrue(spec.arguments.isEmpty())
        assertTrue(spec.requiredArguments.isEmpty())
    }

    @Test
    fun keepsObserveScreenForDebugCompatibility() {
        // Acceptance criterion: maintain observe_screen alongside the structured tool.
        assertNotNull(AndroidToolCatalog.find("observe_screen"))
        assertNotNull(AndroidToolCatalog.find("observe_screen_context"))
    }

    @Test
    fun serializesStableStructuredJson() {
        val context = ScreenContext(
            appLabel = "Gmail",
            packageName = "com.google.android.gm",
            windowTitle = "Inbox",
            nodes = listOf(
                ScreenNode(
                    nodeId = "0.0",
                    role = NodeRole.BUTTON,
                    text = ScreenText.of("Compose"),
                    clickable = true
                )
            )
        )

        val json = JSONObject(context.toRedactedJson())

        assertEquals("Gmail", json.getString("appLabel"))
        assertEquals("com.google.android.gm", json.getString("packageName"))
        assertEquals("Inbox", json.getString("windowTitle"))
        assertEquals(1, json.getJSONArray("nodes").length())
        assertFalse(json.getBoolean("containsSensitiveContent"))

        val node = json.getJSONArray("nodes").getJSONObject(0)
        assertEquals("0.0", node.getString("nodeId"))
        assertEquals("BUTTON", node.getString("role"))
        assertTrue(node.getBoolean("clickable"))
        assertTrue(node.has("bounds"))
    }

    @Test
    fun redactsSensitiveTextByDefault() {
        val context = ScreenContext(
            packageName = "com.example.bank",
            windowTitle = "Login",
            nodes = listOf(
                ScreenNode(
                    nodeId = "0.1",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("password: hunter2"),
                    isInputField = true
                )
            )
        )

        val json = context.toRedactedJson()

        assertTrue(json.contains("[REDACTED]"), json)
        assertFalse(json.contains("hunter2"), json)
        assertTrue(JSONObject(json).getBoolean("containsSensitiveContent"))
    }

    @Test
    fun serializesEmptyContext() {
        val json = JSONObject(ScreenContext.Empty.toRedactedJson())

        assertTrue(json.isNull("appLabel"))
        assertTrue(json.isNull("packageName"))
        assertTrue(json.isNull("windowTitle"))
        assertEquals(0, json.getJSONArray("nodes").length())
        assertFalse(json.getBoolean("containsSensitiveContent"))
    }
}
