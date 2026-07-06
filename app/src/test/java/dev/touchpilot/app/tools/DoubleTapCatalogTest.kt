package dev.touchpilot.app.tools

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoubleTapCatalogTest {
    @Test
    fun doubleTapIsRegisteredAsMediumRisk() {
        val spec = assertNotNull(AndroidToolCatalog.find("double_tap"))
        assertEquals(ToolRisk.MEDIUM, spec.risk)
        assertEquals(setOf("text", "node_id", "bounds", "view_id"), spec.arguments.keys)
        assertTrue(spec.requiredArguments.isEmpty())
    }

    @Test
    fun validateAcceptsEachSingleSelector() {
        assertNull(AndroidToolCatalog.validate("double_tap", mapOf("text" to "Photo")))
        assertNull(AndroidToolCatalog.validate("double_tap", mapOf("node_id" to "0.1.2")))
        assertNull(AndroidToolCatalog.validate("double_tap", mapOf("bounds" to "0,0,100,50")))
        assertNull(AndroidToolCatalog.validate("double_tap", mapOf("view_id" to "com.example:id/photo")))
    }

    @Test
    fun validateRejectsMissingOrMultipleSelectors() {
        assertEquals(
            "double_tap requires exactly one selector: text, node_id, bounds, or view_id",
            AndroidToolCatalog.validate("double_tap", emptyMap())
        )
        assertEquals(
            "double_tap requires exactly one selector: text, node_id, bounds, or view_id",
            AndroidToolCatalog.validate("double_tap", mapOf("text" to "Photo", "node_id" to "0.1"))
        )
    }

    @Test
    fun validateRejectsMalformedBounds() {
        assertEquals(
            "bounds must be left,top,right,bottom",
            AndroidToolCatalog.validate("double_tap", mapOf("bounds" to "bad"))
        )
    }

    @Test
    fun validateRejectsUnknownArgument() {
        val error = AndroidToolCatalog.validate("double_tap", mapOf("text" to "Photo", "nonsense" to "1"))
        assertNotNull(error)
        assertContains(error, "Unknown argument")
    }

    @Test
    fun retryPolicyTreatsDoubleTapLikeNavigationAction() {
        val config = AndroidToolRetryPolicy().configFor("double_tap")
        assertEquals(3, config.maxAttempts)
        assertTrue(config.retryable)
        assertTrue(config.waitForIdleAfterSuccess)
    }

    @Test
    fun retryPolicyDoesNotRetryAmbiguousDoubleTapFailures() {
        val decision = AndroidToolRetryPolicy().shouldRetry(
            "double_tap",
            ToolResult(ok = false, message = "Ambiguous double-tap target: multiple matches"),
            attempt = 0
        )
        assertFalse(decision.retry)
        assertEquals(ToolFailureCategory.NON_RETRYABLE, decision.category)
    }

    @Test
    fun verifierPassesWhenScreenChangesAndFailsOtherwise() {
        val verifier = ToolVerifier()
        val passed = verifier.verify(
            toolName = "double_tap",
            args = mapOf("text" to "Photo"),
            result = ToolResult(ok = true, message = "doubleTap"),
            before = screen(listOf(node("0", "Photo"))),
            after = screen(listOf(node("0", "Photo (zoomed)"))),
        )
        assertIs<ToolVerificationResult.Passed>(passed)

        val before = screen(listOf(node("0", "Photo")))
        val failed = verifier.verify(
            toolName = "double_tap",
            args = mapOf("text" to "Photo"),
            result = ToolResult(ok = true, message = "doubleTap"),
            before = before,
            after = before,
        )
        assertIs<ToolVerificationResult.Failed>(failed)
    }

    private fun node(id: String, text: String): ScreenNode = ScreenNode(
        nodeId = id,
        role = NodeRole.BUTTON,
        text = ScreenText.of(text),
        bounds = NodeBounds(0, 0, 100, 100),
        clickable = true,
    )

    private fun screen(nodes: List<ScreenNode>): ScreenContext = ScreenContext(
        appLabel = "TouchPilot",
        packageName = "dev.touchpilot.app",
        nodes = nodes,
    )
}
