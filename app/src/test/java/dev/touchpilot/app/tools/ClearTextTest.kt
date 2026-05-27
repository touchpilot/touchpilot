package dev.touchpilot.app.tools

import dev.touchpilot.app.tools.targets.ClearTextTarget
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClearTextTest {

    @Test
    fun catalogContainsClearText() {
        assertNotNull(AndroidToolCatalog.find("clear_text"))
    }

    @Test
    fun clearTextSpecHasAllSelectorArgs() {
        val spec = AndroidToolCatalog.find("clear_text")!!
        assertTrue(spec.arguments.containsKey(ClearTextTarget.TargetTextArg))
        assertTrue(spec.arguments.containsKey(ClearTextTarget.TargetNodeIdArg))
        assertTrue(spec.arguments.containsKey(ClearTextTarget.TargetBoundsArg))
        assertTrue(spec.arguments.containsKey(ClearTextTarget.TargetViewIdArg))
        assertTrue(spec.arguments.containsKey(ClearTextTarget.TargetContentDescriptionArg))
    }

    @Test
    fun clearTextRiskIsMedium() {
        val spec = AndroidToolCatalog.find("clear_text")!!
        assertTrue(spec.risk == ToolRisk.MEDIUM)
    }

    @Test
    fun validationAcceptsFocusedMode() {
        assertNull(AndroidToolCatalog.validate("clear_text", emptyMap()))
    }

    @Test
    fun validationAcceptsTargetTextSelector() {
        assertNull(AndroidToolCatalog.validate("clear_text", mapOf(ClearTextTarget.TargetTextArg to "Search")))
    }

    @Test
    fun validationAcceptsTargetNodeIdSelector() {
        assertNull(AndroidToolCatalog.validate("clear_text", mapOf(ClearTextTarget.TargetNodeIdArg to "0.1.2")))
    }

    @Test
    fun validationAcceptsTargetBoundsSelector() {
        assertNull(AndroidToolCatalog.validate("clear_text", mapOf(ClearTextTarget.TargetBoundsArg to "0,0,100,50")))
    }

    @Test
    fun validationAcceptsTargetViewIdSelector() {
        assertNull(AndroidToolCatalog.validate("clear_text", mapOf(ClearTextTarget.TargetViewIdArg to "com.example:id/search")))
    }

    @Test
    fun validationAcceptsTargetContentDescriptionSelector() {
        assertNull(AndroidToolCatalog.validate("clear_text", mapOf(ClearTextTarget.TargetContentDescriptionArg to "Search field")))
    }

    @Test
    fun validationRejectsMultipleSelectors() {
        val error = AndroidToolCatalog.validate(
            "clear_text",
            mapOf(
                ClearTextTarget.TargetTextArg to "Search",
                ClearTextTarget.TargetNodeIdArg to "0.1",
            )
        )
        assertNotNull(error)
        assertContains(error, "at most one selector")
    }

    @Test
    fun validationRejectsAllSelectorsCombined() {
        val error = AndroidToolCatalog.validate(
            "clear_text",
            mapOf(
                ClearTextTarget.TargetTextArg to "a",
                ClearTextTarget.TargetNodeIdArg to "0.1",
                ClearTextTarget.TargetBoundsArg to "0,0,1,1",
                ClearTextTarget.TargetViewIdArg to "com.ex:id/f",
                ClearTextTarget.TargetContentDescriptionArg to "field",
            )
        )
        assertNotNull(error)
        assertContains(error, "at most one selector")
    }

    @Test
    fun validationRejectsMalformedBounds() {
        val error = AndroidToolCatalog.validate(
            "clear_text",
            mapOf(ClearTextTarget.TargetBoundsArg to "bad,bounds")
        )
        assertNotNull(error)
        assertContains(error, "target_bounds must be left,top,right,bottom")
    }

    @Test
    fun validationRejectsUnknownArgs() {
        val error = AndroidToolCatalog.validate("clear_text", mapOf("unknown" to "foo"))
        assertNotNull(error)
        assertContains(error, "Unknown argument")
    }

    @Test
    fun existingToolsUnaffected() {
        assertNull(AndroidToolCatalog.validate("tap", mapOf("text" to "OK")))
        assertNull(AndroidToolCatalog.validate("type_text", mapOf("text" to "hello")))
        assertNull(AndroidToolCatalog.validate("focus_input", mapOf("text" to "Search")))
        assertNull(AndroidToolCatalog.validate("scroll", mapOf("direction" to "forward")))
    }
}
