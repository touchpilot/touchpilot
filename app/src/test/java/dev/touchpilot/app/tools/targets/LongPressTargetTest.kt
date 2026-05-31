package dev.touchpilot.app.tools.targets

import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LongPressTargetTest {
    @Test
    fun textBuildsSelectorWithoutRoleConstraint() {
        val selector = LongPressTarget.selectorFromArgs(
            mapOf(LongPressTarget.TextArg to "Photo")
        )

        assertEquals("Photo", selector.text?.raw)
        // Long-press can target any element, so no role is imposed.
        assertNull(selector.role)
        assertEquals(SelectorSource.AGENT, selector.source)
        assertTrue(selector.isValid())
    }

    @Test
    fun viewIdBuildsValidSelector() {
        val selector = LongPressTarget.selectorFromArgs(
            mapOf(LongPressTarget.ViewIdArg to "com.example:id/thumbnail")
        )

        assertEquals("com.example:id/thumbnail", selector.viewIdResourceName)
        assertTrue(selector.isValid())
    }

    @Test
    fun boundsBuildSelectorFromLooseFormat() {
        val selector = LongPressTarget.selectorFromArgs(
            mapOf(LongPressTarget.BoundsArg to "10,20,110,80")
        )

        assertEquals(TargetBounds(10, 20, 110, 80), selector.bounds)
        assertTrue(selector.isValid())
    }

    @Test
    fun hasTargetReportsWhetherAnySelectorIsPresent() {
        assertFalse(LongPressTarget.hasTarget(emptyMap()))
        assertFalse(LongPressTarget.hasTarget(mapOf(LongPressTarget.TextArg to " ")))
        assertTrue(LongPressTarget.hasTarget(mapOf(LongPressTarget.NodeIdArg to "0.1")))
    }

    @Test
    fun catalogExposesLongPressAsMediumRiskWithoutRequiredArgs() {
        val spec = AndroidToolCatalog.find("long_press")
        assertTrue(spec != null)
        assertEquals(
            setOf("text", "node_id", "view_id", "bounds"),
            spec!!.arguments.keys,
        )
        assertTrue(spec.requiredArguments.isEmpty())
    }

    @Test
    fun validationRequiresAtLeastOneTarget() {
        val error = AndroidToolCatalog.validate("long_press", emptyMap())
        assertEquals("long_press requires a target: text, node_id, view_id, or bounds", error)
    }

    @Test
    fun validationRejectsUnknownArguments() {
        val error = AndroidToolCatalog.validate(
            "long_press",
            mapOf("target_text" to "Photo"),
        )
        assertTrue(error != null && error.startsWith("Unknown argument"))
    }

    @Test
    fun validationRejectsMalformedBounds() {
        val error = AndroidToolCatalog.validate(
            "long_press",
            mapOf(LongPressTarget.BoundsArg to "bad,bounds"),
        )
        assertEquals("bounds must be left,top,right,bottom", error)
    }

    @Test
    fun validationAcceptsASingleSelector() {
        assertNull(AndroidToolCatalog.validate("long_press", mapOf(LongPressTarget.TextArg to "Photo")))
        assertNull(AndroidToolCatalog.validate("long_press", mapOf(LongPressTarget.NodeIdArg to "0.2")))
    }

    @Test
    fun mediumRiskRequiresApprovalForModelSource() {
        val spec = AndroidToolCatalog.find("long_press")!!
        val decision = DefaultActionPolicy().evaluate(
            ToolPolicyRequest(
                tool = spec,
                args = mapOf(LongPressTarget.TextArg to "Album"),
                source = ToolSource.LOCAL_MODEL,
            )
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }
}
