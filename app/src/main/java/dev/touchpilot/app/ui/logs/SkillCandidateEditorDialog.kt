package dev.touchpilot.app.ui.logs

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.ui.detailSectionView
import dev.touchpilot.app.ui.dp
import dev.touchpilot.app.ui.editText
import dev.touchpilot.app.ui.formLabel
import dev.touchpilot.app.ui.summaryCard
import dev.touchpilot.app.ui.withMargins
import dev.touchpilot.app.workflow.WorkflowSkillCandidate
import dev.touchpilot.app.workflow.WorkflowSkillCandidateMarkdown

class SkillCandidateEditorDialog(
    private val activity: Activity,
    private val candidate: WorkflowSkillCandidate,
    private val onSave: (String, String) -> Boolean,
) {
    fun show() {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(12))
        }

        root.addView(
            activity.summaryCard(
                title = candidate.title,
                value = candidate.description.ifBlank { "No description provided." },
                chipText = candidate.risk.name.lowercase(),
                chipAccent = candidate.risk != SkillRisk.LOW
            )
        )
        root.addView(
            activity.detailSectionView(
                title = "Instructions preview",
                body = candidate.toMarkdown()
            ).withMargins(top = 4, bottom = 10)
        )

        val idField = activity.editText("Skill ID").apply {
            setText(candidate.id)
        }
        val titleField = activity.editText("Title").apply {
            setText(candidate.title)
        }
        val descriptionField = activity.editText("Description").apply {
            setText(candidate.description)
        }
        val allowedToolsField = multilineField(candidate.allowedTools)
        val aliasesField = multilineField(emptyList())
        val examplesField = multilineField(candidate.examples)
        val successCriteriaField = multilineField(candidate.successCriteria)
        val riskGroup = buildRiskGroup(candidate.risk)

        root.addView(activity.formLabel("Skill ID"))
        root.addView(idField)
        root.addView(activity.formLabel("Title"))
        root.addView(titleField)
        root.addView(activity.formLabel("Description"))
        root.addView(descriptionField)
        root.addView(activity.formLabel("Risk"))
        root.addView(riskGroup)
        root.addView(activity.formLabel("Allowed tools"))
        root.addView(allowedToolsField)
        root.addView(activity.formLabel("Aliases"))
        root.addView(aliasesField)
        root.addView(activity.formLabel("Examples"))
        root.addView(examplesField)
        root.addView(activity.formLabel("Success criteria"))
        root.addView(successCriteriaField)

        val scrollView = ScrollView(activity).apply {
            isFillViewport = true
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Review skill candidate")
            .setView(scrollView)
            .setNegativeButton("Discard", null)
            .setPositiveButton("Save skill", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val id = idField.text.toString().trim()
                val title = titleField.text.toString().trim()
                val description = descriptionField.text.toString().trim()
                val selectedRisk = riskFromGroup(riskGroup)
                val aliases = parseMultiline(aliasesField)
                val allowedTools = parseMultiline(allowedToolsField)
                val examples = parseMultiline(examplesField)
                val successCriteria = parseMultiline(successCriteriaField)
                val body = candidate.toMarkdown(
                    title = title,
                    description = description,
                    risk = selectedRisk,
                    examples = examples,
                    allowedTools = allowedTools,
                    successCriteria = successCriteria,
                )
                val markdown = WorkflowSkillCandidateMarkdown.build(
                    id = id,
                    title = title,
                    description = description,
                    risk = selectedRisk,
                    aliases = aliases,
                    allowedTools = allowedTools,
                    examples = examples,
                    successCriteria = successCriteria,
                    body = body,
                )
                if (onSave(id, markdown)) {
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun multilineField(values: List<String>): android.widget.EditText {
        return activity.editText("One item per line").apply {
            setText(values.joinToString("\n"))
            setSingleLine(false)
            minLines = 3
            gravity = Gravity.TOP
        }
    }

    private fun buildRiskGroup(selected: SkillRisk): MaterialButtonToggleGroup {
        return MaterialButtonToggleGroup(activity).apply {
            isSingleSelection = true
            isSelectionRequired = true
            var checkedButtonId = View.NO_ID
            SkillRisk.values().forEach { risk ->
                val button = MaterialButton(activity).apply {
                    id = View.generateViewId()
                    tag = risk
                    text = risk.name.lowercase().replaceFirstChar { it.uppercase() }
                    isAllCaps = false
                    textSize = 12f
                    setPadding(activity.dp(14), activity.dp(8), activity.dp(14), activity.dp(8))
                }
                if (risk == selected) {
                    checkedButtonId = button.id
                }
                addView(
                    button,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        rightMargin = if (risk == SkillRisk.HIGH) 0 else activity.dp(6)
                    }
                )
            }
            check(checkedButtonId)
        }
    }

    private fun riskFromGroup(group: MaterialButtonToggleGroup): SkillRisk {
        return (group.findViewById<View>(group.checkedButtonId)?.tag as? SkillRisk) ?: SkillRisk.LOW
    }

    private fun parseMultiline(field: android.widget.EditText): List<String> {
        return field.text
            .lineSequence()
            .map { it.trim().removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotBlank() }
            .toList()
    }
}
