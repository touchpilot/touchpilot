package dev.touchpilot.app.memory

/**
 * Formats [Skill] metadata for the Settings skill detail UI. Pure Kotlin so
 * presentation rules stay testable without Android views.
 */
object SkillDetailFormatter {
    data class RiskPresentation(
        val label: String,
        val summary: String,
        val accent: Boolean,
        val caution: Boolean
    )

    data class DetailSection(
        val title: String,
        val body: String,
        val empty: Boolean = false
    )

    fun riskPresentation(risk: SkillRisk): RiskPresentation {
        return when (risk) {
            SkillRisk.LOW -> RiskPresentation(
                label = "Low risk",
                summary = "Read-only or low-impact actions. Standard approval rules still apply.",
                accent = false,
                caution = false
            )
            SkillRisk.MEDIUM -> RiskPresentation(
                label = "Medium risk",
                summary = "May change device state. Review allowed tools before enabling this skill.",
                accent = true,
                caution = true
            )
            SkillRisk.HIGH -> RiskPresentation(
                label = "High risk",
                summary = "Messaging, purchases, or security-sensitive workflows. Extra caution is required.",
                accent = true,
                caution = true
            )
        }
    }

    fun formatLabel(risk: SkillRisk): String = riskPresentation(risk).label

    fun displayDescription(skill: Skill): String {
        if (skill.description.isNotBlank()) return skill.description
        return legacyDescription(skill.markdown)
    }

    fun formatChip(skill: Skill): String {
        return when (skill.format) {
            SkillFormat.V2 -> "Skills v2"
            SkillFormat.LEGACY_V1 -> "Legacy format"
        }
    }

    fun detailSections(skill: Skill): List<DetailSection> {
        return listOf(
            DetailSection(
                title = "Description",
                body = displayDescription(skill).ifBlank { "No description provided." },
                empty = displayDescription(skill).isBlank()
            ),
            DetailSection(
                title = "Risk",
                body = buildString {
                    val risk = riskPresentation(skill.risk)
                    appendLine(risk.label)
                    append(risk.summary)
                }
            ),
            DetailSection(
                title = "Aliases",
                body = formatBulletList(skill.aliases),
                empty = skill.aliases.isEmpty()
            ),
            DetailSection(
                title = "Allowed tools",
                body = formatAllowedTools(skill.allowedTools),
                empty = skill.allowedTools.isEmpty()
            ),
            DetailSection(
                title = "Examples",
                body = formatBulletList(skill.examples),
                empty = skill.examples.isEmpty()
            ),
            DetailSection(
                title = "Success criteria",
                body = formatBulletList(skill.successCriteria),
                empty = skill.successCriteria.isEmpty()
            ),
            DetailSection(
                title = "Instructions",
                body = skill.markdown.trim().ifBlank { "No bundled instructions." },
                empty = skill.markdown.isBlank()
            )
        )
    }

    fun formatBulletList(items: List<String>): String {
        if (items.isEmpty()) return "None declared."
        return items.joinToString(separator = "\n") { item -> "• $item" }
    }

    fun formatAllowedTools(tools: Set<String>): String {
        if (tools.isEmpty()) return "No tools declared."
        return tools.sorted().joinToString(separator = "\n") { tool -> "• $tool" }
    }

    fun activeSkillSummary(skill: Skill, isActive: Boolean): String {
        return buildString {
            append("Skill ID: ${skill.id}")
            appendLine()
            append("Format: ${formatChip(skill)}")
            appendLine()
            append(if (isActive) "Status: Active skill" else "Status: Available")
        }
    }

    private fun legacyDescription(markdown: String): String {
        return markdown.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !line.startsWith("#") &&
                    !line.startsWith("-") &&
                    !line.startsWith("Allowed initial tools", ignoreCase = true)
            }
            .orEmpty()
    }
}
