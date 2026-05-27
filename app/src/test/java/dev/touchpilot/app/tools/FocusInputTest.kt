package dev.touchpilot.app.tools

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FocusInputTest {

    @Test
    fun catalogContainsFocusInput() {
        assertNotNull(AndroidToolCatalog.find("focus_input"))
    }

    @Test
    fun focusInputSpecHasAllSelectors() {
        val spec = AndroidToolCatalog.find("focus_input")!!
        assertTrue(spec.arguments.containsKey("text"))
        assertTrue(spec.arguments.containsKey("node_id"))
        assertTrue(spec.arguments.containsKey("bounds"))
        assertTrue(spec.arguments.containsKey("view_id"))
    }

    @Test
    fun validationAcceptsTextSelector() {
        assertNull(AndroidToolCatalog.validate("focus_input", mapOf("text" to "Search")))
    }

    @Test
    fun validationAcceptsNodeIdSelector() {
        assertNull(AndroidToolCatalog.validate("focus_input", mapOf("node_id" to "0.1.2")))
    }

    @Test
    fun validationAcceptsBoundsSelector() {
        assertNull(AndroidToolCatalog.validate("focus_input", mapOf("bounds" to "0,0,100,50")))
    }

    @Test
    fun validationAcceptsViewIdSelector() {
        assertNull(AndroidToolCatalog.validate("focus_input", mapOf("view_id" to "com.example:id/search")))
    }

    @Test
    fun validationRejectsNoSelector() {
        val error = AndroidToolCatalog.validate("focus_input", emptyMap())
        assertNotNull(error)
        assertContains(error, "exactly one selector")
    }

    @Test
    fun validationRejectsMultipleSelectors() {
        val error = AndroidToolCatalog.validate("focus_input", mapOf("text" to "Search", "node_id" to "0.1"))
        assertNotNull(error)
        assertContains(error, "exactly one selector")
    }

    @Test
    fun validationRejectsAllFourSelectors() {
        val error = AndroidToolCatalog.validate(
            "focus_input",
            mapOf("text" to "a", "node_id" to "0.1", "bounds" to "0,0,1,1", "view_id" to "com.ex:id/f")
        )
        assertNotNull(error)
        assertContains(error, "exactly one selector")
    }

    @Test
    fun validationRejectsUnknownArgs() {
        val error = AndroidToolCatalog.validate("focus_input", mapOf("text" to "Search", "unknown" to "foo"))
        assertNotNull(error)
        assertContains(error, "Unknown argument")
    }

    @Test
    fun validationRejectsBlankTextSelector() {
        val error = AndroidToolCatalog.validate("focus_input", mapOf("text" to "  "))
        assertNotNull(error)
        assertContains(error, "exactly one selector")
    }

    @Test
    fun validationRejectsBlankNodeIdSelector() {
        val error = AndroidToolCatalog.validate("focus_input", mapOf("node_id" to ""))
        assertNotNull(error)
        assertContains(error, "exactly one selector")
    }

    @Test
    fun existingToolsUnaffected() {
        assertNull(AndroidToolCatalog.validate("tap", mapOf("text" to "OK")))
        assertNull(AndroidToolCatalog.validate("type_text", mapOf("text" to "hello")))
        assertNull(AndroidToolCatalog.validate("scroll", mapOf("direction" to "forward")))
    }
}
