package dev.touchpilot.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidToolCatalogTest {
    @Test
    fun registersOpenSettingsPanel() {
        val spec = AndroidToolCatalog.find("open_settings_panel")
        assertNotNull(spec)
        assertEquals(ToolRisk.MEDIUM, spec.risk)
        assertEquals(setOf("panel"), spec.requiredArguments)
    }

    @Test
    fun acceptsEverySupportedPanel() {
        for (panel in SupportedSettingsPanels) {
            assertNull(
                AndroidToolCatalog.validate("open_settings_panel", mapOf("panel" to panel)),
                "expected panel \"$panel\" to validate"
            )
        }
    }

    @Test
    fun rejectsUnsupportedPanelWithExplicitError() {
        val error = AndroidToolCatalog.validate("open_settings_panel", mapOf("panel" to "vpn"))
        assertNotNull(error)
        assertTrue(error.contains("Unsupported settings panel"), error)
        assertTrue(error.contains("vpn"), error)
    }

    @Test
    fun rejectsMissingPanelArgument() {
        val error = AndroidToolCatalog.validate("open_settings_panel", emptyMap())
        assertNotNull(error)
        assertTrue(error.contains("Missing required argument"), error)
    }

    @Test
    fun rejectsBlankPanelArgument() {
        val error = AndroidToolCatalog.validate("open_settings_panel", mapOf("panel" to ""))
        assertNotNull(error)
        assertTrue(error.contains("Missing required argument"), error)
    }

    @Test
    fun rejectsUnknownArgument() {
        val error = AndroidToolCatalog.validate(
            "open_settings_panel",
            mapOf("panel" to "wifi", "extra" to "1")
        )
        assertNotNull(error)
        assertTrue(error.contains("Unknown argument"), error)
    }

    @Test
    fun coversIssue89Allowlist() {
        // The panels named in the issue acceptance criteria must all be supported.
        val required = listOf(
            "wifi",
            "bluetooth",
            "accessibility",
            "app_info",
            "notifications",
            "system_settings"
        )
        assertEquals(required, SupportedSettingsPanels)
    }
}
