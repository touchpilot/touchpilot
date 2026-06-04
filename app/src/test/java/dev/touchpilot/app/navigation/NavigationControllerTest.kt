package dev.touchpilot.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigationControllerTest {
    @Test
    fun defaultsToChatWithNoNestedNavigation() {
        val controller = NavigationController()

        assertEquals(AppSection.CHAT, controller.activeSection)
        assertNull(controller.activeSettingsPanel)
        assertNull(controller.activeRunDetailId)
        assertEquals(0, controller.consumeSettingsAnimationDirection())
    }

    @Test
    fun switchingSectionsPreservesCurrentSection() {
        val controller = NavigationController()

        controller.showSection(AppSection.TOOLS)

        assertEquals(AppSection.TOOLS, controller.activeSection)
    }

    @Test
    fun settingsPanelTransitionsSetAndConsumeAnimationDirection() {
        val controller = NavigationController()

        controller.openSettingsPanel(SettingsPanel.MCP)

        assertEquals(SettingsPanel.MCP, controller.activeSettingsPanel)
        assertEquals(1, controller.consumeSettingsAnimationDirection())
        assertEquals(0, controller.consumeSettingsAnimationDirection())

        controller.closeSettingsPanel()

        assertNull(controller.activeSettingsPanel)
        assertEquals(-1, controller.consumeSettingsAnimationDirection())
    }

    @Test
    fun openingSameSettingsPanelDoesNotQueueAnotherAnimation() {
        val controller = NavigationController()

        controller.openSettingsPanel(SettingsPanel.RUNTIME)
        controller.consumeSettingsAnimationDirection()
        controller.openSettingsPanel(SettingsPanel.RUNTIME)

        assertEquals(SettingsPanel.RUNTIME, controller.activeSettingsPanel)
        assertEquals(0, controller.consumeSettingsAnimationDirection())
    }

    @Test
    fun runDetailSurvivesChatAndLogsButClosesForOtherSections() {
        val controller = NavigationController()

        controller.openRunDetail("run-1")
        controller.showSection(AppSection.LOGS)

        assertEquals("run-1", controller.activeRunDetailId)

        controller.showSection(AppSection.SETTINGS)

        assertNull(controller.activeRunDetailId)
    }

    @Test
    fun closingRunDetailKeepsCurrentSection() {
        val controller = NavigationController()

        controller.showSection(AppSection.LOGS)
        controller.openRunDetail("run-1")
        controller.closeRunDetail()

        assertEquals(AppSection.LOGS, controller.activeSection)
        assertNull(controller.activeRunDetailId)
    }
}
