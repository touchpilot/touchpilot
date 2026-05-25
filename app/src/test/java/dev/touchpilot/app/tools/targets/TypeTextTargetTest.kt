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

class TypeTextTargetTest {
    @Test
    fun focusedInputArgsRemainCompatible() {
        val args = mapOf(TypeTextTarget.TextArg to "hello")

        assertFalse(TypeTextTarget.hasTarget(args))
        assertNull(AndroidToolCatalog.validate("type_text", args))
    }

    @Test
    fun targetTextBuildsInputSelector() {
        val selector = TypeTextTarget.selectorFromArgs(
            mapOf(
                TypeTextTarget.TextArg to "hello",
                TypeTextTarget.TargetTextArg to "Search",
            )
        )

        assertEquals("Search", selector.text?.raw)
        assertEquals(TargetRole.INPUT, selector.role)
        assertEquals(SelectorSource.AGENT, selector.source)
        assertTrue(selector.isValid())
    }

    @Test
    fun targetNodeIdBuildsInputSelector() {
        val selector = TypeTextTarget.selectorFromArgs(
            mapOf(
                TypeTextTarget.TextArg to "hello",
                TypeTextTarget.TargetNodeIdArg to "0.1.2",
            )
        )

        assertEquals("0.1.2", selector.nodeId)
        assertEquals(TargetRole.INPUT, selector.role)
        assertTrue(selector.isValid())
    }

    @Test
    fun malformedTargetBoundsFailValidation() {
        val error = AndroidToolCatalog.validate(
            "type_text",
            mapOf(
                TypeTextTarget.TextArg to "hello",
                TypeTextTarget.TargetBoundsArg to "bad,bounds",
            )
        )

        assertEquals("target_bounds must be left,top,right,bottom", error)
    }

    @Test
    fun sensitivePayloadStillBlockedByPolicy() {
        val tool = AndroidToolCatalog.find("type_text")!!
        val decision = DefaultActionPolicy().evaluate(
            ToolPolicyRequest(
                tool = tool,
                args = mapOf(TypeTextTarget.TextArg to "password=correct horse battery staple"),
                source = ToolSource.LOCAL_MODEL,
            )
        )

        assertIs<PolicyDecision.Block>(decision)
    }
}
