package dev.touchpilot.app.navigation

data class NavigationState(
    val activeSection: AppSection = AppSection.CHAT,
    val activeSettingsPanel: SettingsPanel? = null,
    val pendingSettingsAnimationDirection: Int = 0,
    val activeRunDetailId: String? = null,
    val activeSkillDetailId: String? = null
)
