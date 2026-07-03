package dev.touchpilot.app.ui.workflows

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.touchpilot.app.R
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.detailSectionView
import dev.touchpilot.app.ui.dp
import dev.touchpilot.app.ui.editText
import dev.touchpilot.app.ui.formLabel
import dev.touchpilot.app.ui.primaryButton
import dev.touchpilot.app.ui.rounded
import dev.touchpilot.app.ui.sectionTitle
import dev.touchpilot.app.ui.secondaryButton
import dev.touchpilot.app.ui.statusChip
import dev.touchpilot.app.ui.withMargins
import dev.touchpilot.app.workflow.WorkflowDefinition
import dev.touchpilot.app.workflow.WorkflowExpectedState
import dev.touchpilot.app.workflow.WorkflowParameter
import dev.touchpilot.app.workflow.WorkflowSensitivity
import dev.touchpilot.app.workflow.WorkflowSkillScope
import dev.touchpilot.app.workflow.WorkflowStep
import dev.touchpilot.app.workflow.WorkflowTrace
import dev.touchpilot.app.workflow.WorkflowTraceSerializer

/**
 * Lets the user review, edit, and save a captured [WorkflowTrace] as a
 * reusable [WorkflowDefinition] (issue #381).
 *
 * The suggested definition comes from [WorkflowTraceSerializer], which already
 * infers parameters and per-step policy from the trace. This screen only
 * exposes edits that cannot weaken safety: parameter defaults/required flags,
 * expected screen text, and step order/removal. Per-step approval policy is
 * always carried through unchanged (see [WorkflowSensitivity]) so a captured
 * workflow can never be saved with a weaker approval requirement than the
 * tool risk that produced it.
 */
class WorkflowEditorRenderer(
    private val activity: Activity,
    private val contentRoot: LinearLayout,
    private val runId: String?,
    private val findTrace: (String) -> WorkflowTrace?,
    private val uniqueWorkflowId: (String) -> String,
    private val closeWorkflowEditor: () -> Unit,
    private val saveWorkflow: (WorkflowDefinition) -> Unit,
) {
    private lateinit var titleInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var stepsContainer: LinearLayout

    private var parameters: List<WorkflowParameter> = emptyList()
    private var skillScope: WorkflowSkillScope? = null
    private var expectedForegroundPackage: String? = null
    private var workflowId: String = ""

    private val parameterDefaultInputs = mutableListOf<EditText>()
    private val parameterRequiredButtons = mutableListOf<TextView>()
    private val parameterRequiredFlags = mutableListOf<Boolean>()
    private val workingSteps = mutableListOf<WorkflowStep>()
    private val stepExpectedStateInputs = mutableListOf<EditText>()

    fun render() {
        contentRoot.addView(
            activity.secondaryButton("Go Back") {
                closeWorkflowEditor()
            }.apply {
                id = R.id.workflow_editor_back_button
                minHeight = 46
            }.withMargins(bottom = 12)
        )

        val trace = runId?.let(findTrace)
        if (trace == null) {
            contentRoot.addView(
                activity.detailSectionView(
                    title = "Workflow capture unavailable",
                    body = "This captured run is no longer available. It may have been cleared when the app restarted."
                )
            )
            return
        }

        val suggested = WorkflowTraceSerializer.toDefinition(trace)
        workflowId = uniqueWorkflowId(suggested.id)
        parameters = suggested.parameters
        skillScope = suggested.skillScope
        expectedForegroundPackage = suggested.expectedForegroundPackage
        workingSteps.clear()
        workingSteps += suggested.steps

        contentRoot.addView(activity.sectionTitle("Save as workflow"))
        contentRoot.addView(
            activity.detailSectionView(
                title = "About this screen",
                body = "Review what TouchPilot captured from this run, then edit parameters and " +
                    "expected screen states before saving it as a reusable local workflow.",
                muted = true,
            ).withMargins(bottom = 10)
        )

        val sensitiveCount = WorkflowSensitivity.sensitiveStepCount(workingSteps)
        if (sensitiveCount > 0) {
            contentRoot.addView(sensitivityWarningCard(sensitiveCount))
        }

        contentRoot.addView(activity.formLabel("Title"))
        titleInput = activity.editText("Workflow title")
        titleInput.setText(suggested.title)
        titleInput.id = R.id.workflow_editor_title_input
        contentRoot.addView(titleInput)

        contentRoot.addView(activity.formLabel("Description"))
        descriptionInput = activity.editText("What this workflow does")
        descriptionInput.setText(suggested.description)
        descriptionInput.id = R.id.workflow_editor_description_input
        contentRoot.addView(descriptionInput)

        contentRoot.addView(
            activity.detailSectionView(
                title = "Workflow ID",
                body = workflowId,
                muted = true,
            ).withMargins(top = 6, bottom = 10)
        )

        contentRoot.addView(activity.sectionTitle("Parameters"))
        parameterDefaultInputs.clear()
        parameterRequiredButtons.clear()
        parameterRequiredFlags.clear()
        if (parameters.isEmpty()) {
            contentRoot.addView(
                activity.detailSectionView(
                    title = "No parameters inferred",
                    body = "TouchPilot did not find any reusable values in this run's task text.",
                    muted = true,
                ).withMargins(bottom = 10)
            )
        } else {
            parameters.forEachIndexed { index, parameter ->
                contentRoot.addView(parameterCard(index, parameter))
            }
        }

        contentRoot.addView(activity.sectionTitle("Steps"))
        stepsContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        contentRoot.addView(stepsContainer)
        renderSteps()

        contentRoot.addView(
            activity.primaryButton("Save Workflow") {
                onSaveClicked()
            }.apply { id = R.id.workflow_editor_save_button }.withMargins(top = 14, bottom = 8)
        )
    }

    private fun sensitivityWarningCard(count: Int): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Theme.SurfaceRaised, 12, Theme.Warning)
            setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
        }
        container.addView(
            TextView(activity).apply {
                val label = if (count == 1) "step requires" else "steps require"
                text = "This workflow has $count $label approval"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Theme.Warning)
            }
        )
        container.addView(
            TextView(activity).apply {
                text = "Sensitive steps stay approval-gated at replay time no matter how this " +
                    "workflow is edited or saved here."
                textSize = 11.5f
                setTextColor(Theme.MutedText)
                setPadding(0, activity.dp(6), 0, 0)
            }
        )
        return container.withMargins(bottom = 12)
    }

    private fun parameterCard(index: Int, parameter: WorkflowParameter): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Theme.SurfaceRaised, 12, Theme.StrokeDark)
            setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
        }
        container.addView(
            TextView(activity).apply {
                text = parameter.name
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }
        )
        if (parameter.description.isNotBlank()) {
            container.addView(
                TextView(activity).apply {
                    text = parameter.description
                    textSize = 11.5f
                    setTextColor(Theme.MutedText)
                    setPadding(0, activity.dp(2), 0, 0)
                }
            )
        }

        val defaultInput = activity.editText("Default value")
        defaultInput.setText(parameter.default.orEmpty())
        parameterDefaultInputs += defaultInput
        container.addView(defaultInput.withMargins(top = 8))

        parameterRequiredFlags += parameter.required
        val requiredButton = activity.secondaryButton(requiredLabel(parameter.required)) {
            val newValue = !parameterRequiredFlags[index]
            parameterRequiredFlags[index] = newValue
            parameterRequiredButtons[index].text = requiredLabel(newValue)
        }
        parameterRequiredButtons += requiredButton
        container.addView(requiredButton.withMargins(top = 8))

        return container.withMargins(bottom = 10)
    }

    private fun requiredLabel(required: Boolean): String {
        return if (required) {
            "Required: Yes (tap to make optional)"
        } else {
            "Required: No (tap to make required)"
        }
    }

    private fun renderSteps() {
        stepsContainer.removeAllViews()
        stepExpectedStateInputs.clear()
        workingSteps.forEachIndexed { index, _ ->
            stepsContainer.addView(stepCard(index))
        }
    }

    private fun stepCard(index: Int): View {
        val step = workingSteps[index]
        val sensitive = WorkflowSensitivity.isSensitive(step)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Theme.SurfaceRaised, 12, if (sensitive) Theme.Warning else Theme.StrokeDark)
            setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(activity).apply {
                text = "Step ${index + 1}: ${step.tool}"
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        if (sensitive) {
            header.addView(activity.statusChip("approval required", accent = true))
        }
        container.addView(header)

        if (step.args.isNotEmpty()) {
            container.addView(
                TextView(activity).apply {
                    text = step.args.entries.joinToString(separator = "\n") { (key, value) -> "• $key = $value" }
                    textSize = 11.5f
                    setTextColor(Theme.BodyText)
                    setPadding(0, activity.dp(6), 0, 0)
                }
            )
        }

        container.addView(
            activity.formLabel("Expected screen text (one per line)").apply {
                setPadding(0, activity.dp(8), 0, activity.dp(4))
            }
        )
        val expectedInput = EditText(activity).apply {
            setText(step.expectedState?.screenTextContains?.joinToString("\n").orEmpty())
            hint = "e.g. Wi-Fi"
            textSize = 13f
            setTextColor(Color.WHITE)
            setHintTextColor(Theme.MutedText)
            background = rounded(Theme.Card, 8, Theme.StrokeDark)
            setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8))
            minLines = 2
        }
        stepExpectedStateInputs += expectedInput
        container.addView(expectedInput.withMargins(bottom = 8))

        val controlsRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        controlsRow.addView(activity.secondaryButton("Move Up") { moveStep(index, -1) }, rowParams())
        controlsRow.addView(activity.secondaryButton("Move Down") { moveStep(index, 1) }, rowParams())
        controlsRow.addView(activity.secondaryButton("Remove") { removeStep(index) }, rowParams())
        container.addView(controlsRow)

        return container.withMargins(bottom = 10)
    }

    private fun rowParams() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(0, 0, 6, 0)
    }

    private fun moveStep(index: Int, delta: Int) {
        syncStepExpectedStateEdits()
        val target = index + delta
        if (target !in workingSteps.indices) return
        val step = workingSteps.removeAt(index)
        workingSteps.add(target, step)
        renderSteps()
    }

    private fun removeStep(index: Int) {
        if (workingSteps.size <= 1) {
            Toast.makeText(activity, "A workflow needs at least one step.", Toast.LENGTH_SHORT).show()
            return
        }
        syncStepExpectedStateEdits()
        workingSteps.removeAt(index)
        renderSteps()
    }

    /** Folds each step's current expected-state text field back into [workingSteps]. */
    private fun syncStepExpectedStateEdits() {
        workingSteps.forEachIndexed { index, step ->
            val input = stepExpectedStateInputs.getOrNull(index) ?: return@forEachIndexed
            val lines = input.text.toString()
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            val existingElementPredicates = step.expectedState?.elementPresent.orEmpty()
            val newExpectedState = if (lines.isEmpty() && existingElementPredicates.isEmpty()) {
                null
            } else {
                WorkflowExpectedState(
                    packageName = step.expectedState?.packageName,
                    windowTitle = step.expectedState?.windowTitle,
                    screenTextContains = lines,
                    elementPresent = existingElementPredicates,
                )
            }
            workingSteps[index] = step.copy(expectedState = newExpectedState)
        }
    }

    private fun onSaveClicked() {
        syncStepExpectedStateEdits()
        val title = titleInput.text?.toString()?.trim().orEmpty()
        val description = descriptionInput.text?.toString()?.trim().orEmpty()

        if (title.isBlank()) {
            Toast.makeText(activity, "Give this workflow a title before saving.", Toast.LENGTH_SHORT).show()
            return
        }

        val editedParameters = parameters.mapIndexed { index, parameter ->
            val default = parameterDefaultInputs.getOrNull(index)?.text?.toString()?.trim().orEmpty()
            parameter.copy(
                default = default.ifBlank { null },
                required = parameterRequiredFlags.getOrElse(index) { parameter.required },
            )
        }

        val definition = try {
            WorkflowDefinition(
                id = workflowId,
                title = title,
                description = description,
                parameters = editedParameters,
                skillScope = skillScope,
                steps = workingSteps.toList(),
                expectedForegroundPackage = expectedForegroundPackage,
            )
        } catch (error: IllegalArgumentException) {
            AlertDialog.Builder(activity)
                .setTitle("Can't save this workflow")
                .setMessage(error.message ?: "This workflow is missing required fields.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        saveWorkflow(definition)
    }
}
