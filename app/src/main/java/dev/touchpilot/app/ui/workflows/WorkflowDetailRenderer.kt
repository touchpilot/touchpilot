package dev.touchpilot.app.ui.workflows

import android.app.Activity
import android.app.AlertDialog
import android.widget.LinearLayout
import dev.touchpilot.app.R
import dev.touchpilot.app.ui.detailSectionView
import dev.touchpilot.app.ui.dp
import dev.touchpilot.app.ui.editText
import dev.touchpilot.app.ui.primaryButton
import dev.touchpilot.app.ui.sectionTitle
import dev.touchpilot.app.ui.secondaryButton
import dev.touchpilot.app.ui.statusChip
import dev.touchpilot.app.ui.summaryCard
import dev.touchpilot.app.ui.withMargins
import dev.touchpilot.app.workflow.WorkflowLibraryEntry
import dev.touchpilot.app.workflow.WorkflowRunStatus

class WorkflowDetailRenderer(
    private val activity: Activity,
    private val contentRoot: LinearLayout,
    private val workflowId: String?,
    private val findWorkflow: (String) -> WorkflowLibraryEntry?,
    private val closeWorkflowDetail: () -> Unit,
    private val replayWorkflow: (String) -> Unit,
    private val renameWorkflow: (String, String) -> WorkflowLibraryEntry?,
    private val deleteWorkflow: (String) -> Boolean,
    private val refreshProductScreen: () -> Unit,
) {
    fun render() {
        contentRoot.addView(activity.sectionTitle("Workflow details"))
        contentRoot.addView(
            activity.secondaryButton("Go Back") {
                closeWorkflowDetail()
            }.apply {
                id = R.id.workflow_detail_back_button
                minHeight = 46
            }.withMargins(bottom = 12)
        )

        val workflow = workflowId?.let(findWorkflow)
        if (workflow == null) {
            contentRoot.addView(
                activity.detailSectionView(
                    title = "Workflow unavailable",
                    body = "This workflow is no longer available. It may have been deleted or replaced."
                )
            )
            return
        }

        contentRoot.addView(
            activity.summaryCard(
                title = workflow.definition.title,
                value = workflow.definition.description.ifBlank { "No description provided" },
                chipText = when (workflow.lastRun?.status ?: WorkflowRunStatus.NEVER_RUN) {
                    WorkflowRunStatus.NEVER_RUN -> "never run"
                    WorkflowRunStatus.SUCCEEDED -> "last run: success"
                    WorkflowRunStatus.FAILED -> "last run: failed"
                },
                chipAccent = workflow.lastRun?.status == WorkflowRunStatus.SUCCEEDED,
            )
        )

        contentRoot.addView(
            activity.statusChip("${workflow.stepCount} steps", accent = workflow.stepCount > 0)
                .withMargins(bottom = 10)
        )

        val renameField = activity.editText("Rename workflow")
        renameField.setText(workflow.definition.title)
        renameField.id = R.id.workflow_rename_input
        contentRoot.addView(renameField)
        contentRoot.addView(
            activity.primaryButton("Rename workflow") {
                val newTitle = renameField.text?.toString()?.trim().orEmpty()
                if (newTitle.isBlank() || newTitle == workflow.definition.title) return@primaryButton
                renameWorkflow(workflow.definition.id, newTitle)
                refreshProductScreen()
            }.withMargins(top = 6, bottom = 8)
        )

        contentRoot.addView(
            activity.primaryButton("Replay workflow") {
                replayWorkflow(workflow.definition.id)
            }.withMargins(top = 2, bottom = 8)
        )

        contentRoot.addView(
            activity.secondaryButton("Delete workflow") {
                confirmDelete(workflow.definition.id, workflow.definition.title)
            }.withMargins(bottom = 10)
        )

        contentRoot.addView(activity.detailSectionView("Description", workflow.definition.description.ifBlank { "No description provided" }))

        contentRoot.addView(
            activity.detailSectionView(
                title = "Parameters",
                body = workflow.definition.parameters.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n\n") { parameter ->
                    buildString {
                        append(parameter.name)
                        if (parameter.description.isNotBlank()) {
                            append(": ")
                            append(parameter.description)
                        }
                        if (parameter.default != null) {
                            append("\nDefault: ${parameter.default}")
                        }
                        append("\nRequired: ${parameter.required}")
                    }
                } ?: "This workflow does not define replay parameters.",
                muted = workflow.definition.parameters.isEmpty(),
            ).withMargins(top = 6, bottom = 6)
        )

        contentRoot.addView(
            activity.detailSectionView(
                title = "Skill scope",
                body = workflow.definition.skillScope?.let { scope ->
                    buildString {
                        appendLine("Skill: ${scope.skillId ?: "none"}")
                        appendLine("Allowed tools:")
                        append(scope.allowedTools.joinToString(separator = "\n") { tool -> "• $tool" })
                    }
                } ?: "This workflow is not locked to a skill scope.",
                muted = workflow.definition.skillScope == null,
            ).withMargins(top = 6, bottom = 6)
        )

        contentRoot.addView(activity.sectionTitle("Steps"))
        workflow.definition.steps.forEachIndexed { index, step ->
            contentRoot.addView(
                activity.detailSectionView(
                    title = "Step ${index + 1}: ${step.tool}",
                    body = buildString {
                        if (step.description.isNotBlank()) {
                            appendLine(step.description)
                            appendLine()
                        }
                        appendLine("Step id: ${step.id}")
                        if (step.args.isNotEmpty()) {
                            appendLine("Arguments:")
                            append(step.args.entries.joinToString(separator = "\n") { (key, value) ->
                                "• $key = $value"
                            })
                        }
                        if (step.expectedState != null) {
                            appendLine()
                            appendLine("Expected state:")
                            append(step.expectedState.screenTextContains.joinToString(separator = "\n") { text ->
                                "• screen text contains \"$text\""
                            }.ifBlank { "• no additional screen predicates" })
                        }
                    },
                    muted = false,
                ).withMargins(top = 4, bottom = 4)
            )
        }

        if (workflow.lastRun != null) {
            contentRoot.addView(
                activity.detailSectionView(
                    title = "Last replay",
                    body = buildString {
                        appendLine(workflow.lastRun.displayLabel)
                        if (workflow.lastRun.message.isNotBlank()) {
                            appendLine()
                            append(workflow.lastRun.message)
                        }
                    }
                ).withMargins(top = 6, bottom = 4)
            )
        }
    }

    private fun confirmDelete(workflowId: String, title: String) {
        AlertDialog.Builder(activity)
            .setTitle("Delete workflow")
            .setMessage("Delete \"$title\" from the local workflow library?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (deleteWorkflow(workflowId)) {
                    closeWorkflowDetail()
                    refreshProductScreen()
                }
            }
            .show()
    }
}
