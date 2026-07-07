package dev.touchpilot.app.memory

/**
 * Formats [Skill] metadata for the discovery-focused "Use" page, where each
 * skill is rendered as a runnable card. The card intentionally surfaces the
 * risk level and required tool permissions so users can judge "what can I do
 * here?" before tapping to run. Pure Kotlin so the card contents stay testable
 * without Android views, mirroring [SkillDetailFormatter].
 */
object SkillCatalogFormatter {
    const val EXAMPLE_LIMIT = 3

    data class UseCard(
        val id: String,
        val title: String,
        val riskLabel: String,
        val riskAccent: Boolean,
        val scope: String,
        val examples: List<String>,
        val permissions: String,
        val runLabel: String
    )

    fun useCard(skill: Skill, exampleLimit: Int = EXAMPLE_LIMIT): UseCard {
        val risk = SkillDetailFormatter.riskPresentation(skill.risk)
        return UseCard(
            id = skill.id,
            title = skill.title.ifBlank { skill.id },
            riskLabel = risk.label,
            riskAccent = risk.accent,
            scope = SkillDetailFormatter.displayDescription(skill).ifBlank { "No description provided." },
            examples = skill.examples.filter { it.isNotBlank() }.take(exampleLimit),
            permissions = formatPermissions(skill.allowedTools),
            runLabel = "Run skill"
        )
    }

    /** Compact, single-line summary of the tool allowlist for a skill card. */
    fun formatPermissions(tools: Set<String>): String {
        if (tools.isEmpty()) return "No tools required."
        return tools.sorted().joinToString(separator = ", ")
    }
}
