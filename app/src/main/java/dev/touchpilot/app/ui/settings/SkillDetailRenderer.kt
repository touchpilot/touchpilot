package dev.touchpilot.app.ui.settings

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.touchpilot.app.R
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillDetailFormatter
import dev.touchpilot.app.memory.SkillFormat
import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.detailSectionView
import dev.touchpilot.app.ui.dp
import dev.touchpilot.app.ui.primaryButton
import dev.touchpilot.app.ui.secondaryButton
import dev.touchpilot.app.ui.sectionTitle
import dev.touchpilot.app.ui.statusChip
import dev.touchpilot.app.ui.summaryCard
import dev.touchpilot.app.ui.withMargins

class SkillDetailRenderer(
    private val activity: Activity,
    private val contentRoot: LinearLayout,
    private val skillId: String?,
    private val findSkill: (String) -> Skill?,
    private val selectedSkillId: () -> String?,
    private val closeSkillDetail: () -> Unit,
    private val commitSelectedSkill: (String?) -> Unit,
    private val runSkill: (String) -> Unit,
    private val refreshSettingsScreen: () -> Unit
) {
    fun render() {
        contentRoot.addView(activity.sectionTitle("Skill details"))
        contentRoot.addView(
            activity.secondaryButton("Go Back") {
                closeSkillDetail()
            }.apply {
                id = R.id.skill_detail_back_button
                minHeight = 46
            }.withMargins(bottom = 12)
        )

        val skill = skillId?.let(findSkill)
        if (skill == null) {
            contentRoot.addView(
                activity.detailSectionView(
                    title = "Skill unavailable",
                    body = "This skill is no longer available. It may have failed validation when the app started."
                )
            )
            return
        }

        val risk = SkillDetailFormatter.riskPresentation(skill.risk)
        val isActive = selectedSkillId() == skill.id

        contentRoot.addView(
            activity.summaryCard(
                title = skill.title,
                value = SkillDetailFormatter.displayDescription(skill)
                    .ifBlank { "No description provided" },
                chipText = risk.label,
                chipAccent = risk.accent
            )
        )

        contentRoot.addView(skillMetaChipRow(skill, isActive))

        SkillDetailFormatter.detailSections(skill).forEach { section ->
            contentRoot.addView(
                activity.detailSectionView(
                    title = section.title,
                    body = section.body,
                    muted = section.empty
                ).withMargins(top = 6, bottom = 6)
            )
        }

        contentRoot.addView(
            activity.primaryButton("Run skill") {
                runSkill(skill.id)
            }.apply { id = R.id.skill_detail_run_button }
                .withMargins(top = 8, bottom = 4)
        )

        if (!isActive) {
            contentRoot.addView(
                activity.primaryButton("Use as active skill") {
                    commitSelectedSkill(skill.id)
                    refreshSettingsScreen()
                }.apply { id = R.id.skill_detail_activate_button }
                    .withMargins(top = 8, bottom = 4)
            )
        } else {
            contentRoot.addView(
                activity.secondaryButton("Clear active skill") {
                    commitSelectedSkill(null)
                    refreshSettingsScreen()
                }.apply { id = R.id.skill_detail_clear_button }
                    .withMargins(top = 8, bottom = 4)
            )
        }
    }

    private fun skillMetaChipRow(skill: Skill, isActive: Boolean): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(8))
            addView(activity.statusChip(SkillDetailFormatter.formatChip(skill), accent = skill.format == SkillFormat.V2))
            addView(
                activity.statusChip(
                    "${skill.allowedTools.size} tools",
                    accent = skill.allowedTools.isNotEmpty()
                ).withMargins(left = activity.dp(8))
            )
            if (isActive) {
                addView(
                    activity.statusChip("active", accent = true)
                        .withMargins(left = activity.dp(8))
                )
            }
            if (skill.risk == SkillRisk.HIGH) {
                addView(
                    riskWarningChip()
                        .withMargins(left = activity.dp(8))
                )
            }
        }
    }

    private fun riskWarningChip(): View {
        return TextView(activity).apply {
            text = "Review carefully"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = true
            setTextColor(Theme.OnAccent)
            setBackgroundColor(Theme.Danger)
            setPadding(activity.dp(12), activity.dp(5), activity.dp(12), activity.dp(5))
        }
    }
}
