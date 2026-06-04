package dev.touchpilot.app.navigation

import dev.touchpilot.app.ui.settings.SettingsPanel

/**
 * Manages all navigation state and transitions for the app.
 * 
 * Responsibilities:
 * - Hold current navigation state
 * - Validate and execute navigation transitions
 * - Notify observers of state changes
 * - Enforce navigation rules (e.g., clear run detail when leaving CHAT/LOGS)
 */
class NavigationController {
    private var state = NavigationState()
    
    private val stateObservers = mutableListOf<(NavigationState) -> Unit>()

    /**
     * Get a copy of the current navigation state.
     */
    fun getCurrentState(): NavigationState {
        return state.copy()
    }

    /**
     * Subscribe to state changes.
     */
    fun observeState(observer: (NavigationState) -> Unit) {
        stateObservers.add(observer)
    }

    /**
     * Unsubscribe from state changes.
     */
    fun removeStateObserver(observer: (NavigationState) -> Unit) {
        stateObservers.remove(observer)
    }

    /**
     * Switch to a different app section.
     */
    fun switchSection(section: AppSection) {
        val newState = state.withSection(section)
        updateState(newState)
    }

    /**
     * Open a run detail view.
     */
    fun openRunDetail(runId: String) {
        val newState = state.withRunDetailId(runId)
        updateState(newState)
    }

    /**
     * Close the run detail view.
     */
    fun closeRunDetail() {
        val newState = state.withRunDetailId(null)
        updateState(newState)
    }

    /**
     * Set the active settings panel.
     */
    fun setActiveSettingsPanel(panel: SettingsPanel?) {
        val newState = state.withSettingsPanel(panel)
        updateState(newState)
    }

    /**
     * Set the pending animation direction for settings transitions.
     * Direction values: -1 for reverse, 0 for none, 1 for forward.
     */
    fun setPendingAnimationDirection(direction: Int) {
        val newState = state.withPendingAnimationDirection(direction)
        updateState(newState)
    }

    /**
     * Clear the pending animation direction after transition is complete.
     */
    fun clearAnimationDirection() {
        val newState = state.clearAnimationDirection()
        updateState(newState)
    }

    /**
     * Update the selected skill and navigate to Settings.
     * Toggles the expanded reference if the same skill is selected again.
     */
    fun commitSelectedSkill(skillId: String?) {
        val newState = state.withSelectedSkill(skillId)
        // Also switch to settings section when committing a skill selection
        val finalState = newState.withSection(AppSection.SETTINGS)
        updateState(finalState)
    }

    /**
     * Get the currently selected skill ID.
     */
    fun getSelectedSkillId(): String? {
        return state.selectedSkillId
    }

    /**
     * Get the currently active app section.
     */
    fun getActiveSection(): AppSection {
        return state.activeSection
    }

    /**
     * Get the currently active settings panel.
     */
    fun getActiveSettingsPanel(): SettingsPanel? {
        return state.activeSettingsPanel
    }

    /**
     * Get the currently open run detail ID.
     */
    fun getActiveRunDetailId(): String? {
        return state.activeRunDetailId
    }

    /**
     * Get the pending animation direction.
     */
    fun getPendingAnimationDirection(): Int {
        return state.pendingSettingsAnimationDirection
    }

    /**
     * Check if we're viewing a run detail.
     */
    fun isRunDetailOpen(): Boolean {
        return state.activeRunDetailId != null
    }

    /**
     * Internal method to update state and notify observers.
     */
    private fun updateState(newState: NavigationState) {
        if (newState != state) {
            state = newState
            notifyObservers()
        }
    }

    /**
     * Notify all observers of the state change.
     */
    private fun notifyObservers() {
        stateObservers.forEach { it(state.copy()) }
    }
}
