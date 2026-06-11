package dev.touchpilot.app.navigation

class NavigationController(initialState: NavigationState = NavigationState()) {
    var state: NavigationState = initialState
        private set

    val activeSection: AppSection
        get() = state.activeSection

    val activeSettingsPanel: SettingsPanel?
        get() = state.activeSettingsPanel

    val activeRunDetailId: String?
        get() = state.activeRunDetailId

    val activeSkillDetailId: String?
        get() = state.activeSkillDetailId

    fun showSection(section: AppSection): NavigationState {
        state = state.copy(
            activeSection = section,
            activeRunDetailId = state.activeRunDetailId.takeIf { section.supportsRunDetail },
            activeSkillDetailId = state.activeSkillDetailId.takeIf { section == AppSection.SETTINGS }
        )
        return state
    }

    fun openSettingsPanel(panel: SettingsPanel): NavigationState {
        if (state.activeSettingsPanel != panel) {
            state = state.copy(
                activeSettingsPanel = panel,
                pendingSettingsAnimationDirection = ForwardSettingsAnimation
            )
        }
        return state
    }

    fun closeSettingsPanel(): NavigationState {
        if (state.activeSettingsPanel != null) {
            state = state.copy(
                activeSettingsPanel = null,
                activeSkillDetailId = null,
                pendingSettingsAnimationDirection = BackSettingsAnimation
            )
        }
        return state
    }

    fun consumeSettingsAnimationDirection(): Int {
        val direction = state.pendingSettingsAnimationDirection
        if (direction != 0) {
            state = state.copy(pendingSettingsAnimationDirection = 0)
        }
        return direction
    }

    fun openRunDetail(runId: String): NavigationState {
        state = state.copy(activeRunDetailId = runId)
        return state
    }

    fun closeRunDetail(): NavigationState {
        state = state.copy(activeRunDetailId = null)
        return state
    }

    fun openSkillDetail(skillId: String): NavigationState {
        state = state.copy(activeSkillDetailId = skillId)
        return state
    }

    fun closeSkillDetail(): NavigationState {
        state = state.copy(activeSkillDetailId = null)
        return state
    }

    private val AppSection.supportsRunDetail: Boolean
        get() = this == AppSection.CHAT || this == AppSection.LOGS

    private companion object {
        const val ForwardSettingsAnimation = 1
        const val BackSettingsAnimation = -1
    }
}
