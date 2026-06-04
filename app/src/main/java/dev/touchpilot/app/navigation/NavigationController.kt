package dev.touchpilot.app.navigation

class NavigationController(initialSection: Section = Section.CHAT) {

    var state: NavigationState = NavigationState(activeSection = initialSection)
        private set

    val activeSection: Section get() = state.activeSection
    val activeSettingsPanel: SettingsPanel? get() = state.activeSettingsPanel
    val activeRunDetailId: String? get() = state.activeRunDetailId
    val pendingSettingsAnimationDirection: Int get() = state.pendingSettingsAnimationDirection

    fun navigateTo(section: Section) {
        val clearRunDetail = section != Section.CHAT && section != Section.LOGS
        state = state.copy(
            activeSection = section,
            activeRunDetailId = if (clearRunDetail) null else state.activeRunDetailId,
        )
    }

    fun openSettingsPanel(panel: SettingsPanel) {
        state = state.copy(
            activeSettingsPanel = panel,
            pendingSettingsAnimationDirection = 1,
        )
    }

    fun closeSettingsPanel() {
        state = state.copy(
            activeSettingsPanel = null,
            pendingSettingsAnimationDirection = -1,
        )
    }

    fun consumePendingAnimationDirection(): Int {
        val direction = state.pendingSettingsAnimationDirection
        state = state.copy(pendingSettingsAnimationDirection = 0)
        return direction
    }

    fun openRunDetail(runId: String) {
        state = state.copy(activeRunDetailId = runId)
    }

    fun closeRunDetail() {
        state = state.copy(activeRunDetailId = null)
    }
}
