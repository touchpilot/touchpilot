# Workflows

TouchPilot workflows are local JSON files that describe repeatable Android
tasks as an ordered sequence of tool calls, optional expected screen states,
parameter slots, and per-step policy hints. They are the portable counterpart
to ephemeral agent run traces.

Workflow files are designed to be:

- **Local-first** — stored on device, editable without network access
- **Inspectable** — every step, argument, and expected state is human-readable
- **Replayable** — consumed by the workflow replay engine (Milestone 10)

## File Location

Bundled example workflows may live under:

```text
app/src/main/assets/workflows/<workflow-id>/workflow.json
```

Captured or hand-authored workflows on a device use app-private storage paths
managed by the replay/capture features (out of scope for the schema PR).

## Workflow JSON Schema (version 1)

```json
{
  "version": 1,
  "id": "open-wifi-settings",
  "title": "Open Wi-Fi Settings",
  "description": "Open the Android Wi-Fi settings panel and verify it is visible.",
  "parameters": [
    {
      "name": "panel_label",
      "description": "Visible label used to confirm the panel opened.",
      "default": "Wi-Fi",
      "required": false
    }
  ],
  "skill_scope": {
    "skill_id": "settings",
    "allowed_tools": [
      "observe_screen_context",
      "open_settings_panel",
      "tap",
      "wait_for_idle"
    ]
  },
  "expected_foreground_package": "com.android.settings",
  "steps": [
    {
      "id": "open-panel",
      "tool": "open_settings_panel",
      "args": {
        "panel": "wifi"
      },
      "description": "Open the Wi-Fi settings panel.",
      "expected_state": {
        "package_name": "com.android.settings",
        "window_title": "Wi-Fi",
        "screen_text_contains": ["Wi-Fi", "Network"],
        "element_present": [
          {
            "text": "Wi-Fi",
            "match": "contains"
          }
        ]
      },
      "policy": {
        "requires_approval": true,
        "workflow_class": "security_settings"
      }
    }
  ]
}
```

The top-level `version` field is required. Only version `1` is supported today.

## Required Fields

`id`
: Stable lower-case identifier. Use `a-z`, `0-9`, and `-`.

`title`
: Short human-readable workflow name.

`steps`
: Non-empty ordered list of tool steps. Each step must include `id` and `tool`.

## Optional Top-Level Fields

`description`
: Longer summary of what the workflow accomplishes.

`parameters`
: Named value slots referenced in step arguments as `{parameter_name}`.

`skill_scope`
: Optional skill and tool allowlist context for replay policy and tool
  visibility.

`expected_foreground_package`
: Package name expected when the workflow completes successfully.

## Parameters

Parameters use lower_snake_case names matching `[a-z][a-z0-9_]*`.

Reference a parameter in any step argument value with brace syntax:

```json
"args": {
  "text": "{contact_name}"
}
```

Fields on each parameter object:

`name` (required)
: Parameter identifier.

`description`
: Human-readable explanation for workflow authors and review UI.

`default`
: Default substitution value when replay does not override the slot.

`required`
: When `true`, replay must supply a value (no default).

## Steps

Each step object contains:

`id` (required)
: Unique step identifier within the workflow file.

`tool` (required)
: Tool name from the Android tool catalog (see [Tool Spec](TOOL_SPEC.md)).

`args`
: String map of tool arguments. Values may be literals or `{parameter}`
  placeholders.

`description`
: Optional author note shown in workflow review surfaces.

`expected_state`
: Screen predicates evaluated after the step completes during replay.

`policy`
: Per-step policy hints for the replay engine.

### Expected State Predicates

`expected_state` helps replay verification confirm the device reached the right
screen before advancing:

`package_name`
: Expected foreground Android package.

`window_title`
: Expected window title when available.

`screen_text_contains`
: List of substrings that should appear somewhere on the redacted screen.

`element_present`
: Structured element checks. Each entry must include at least one selector:

- `text`
- `content_description`
- `node_id`
- `view_id`

Optional `match` field: `exact` or `contains` (default `contains`).

Predicates should use stable, non-sensitive UI labels. Do not embed passwords,
tokens, or one-time codes in workflow files.

### Per-Step Policy

`policy` does not bypass the global policy engine. It tells replay which steps
are approval-sensitive or belong to a workflow risk class:

`requires_approval`
: When `true`, replay should route the step through the approval path even if
  the default tool policy would allow it silently.

`workflow_class`
: One of the `PolicyWorkflowClass` wire values (for example `general`,
  `payment`, `security_settings`). See `PolicyV2Model.kt`.

## Skill Scope

`skill_scope` is optional metadata for replay:

`skill_id`
: Active skill identifier when the workflow was captured or authored.

`allowed_tools`
: Tool names permitted for this workflow. Every entry must exist in the
  Android tool catalog.

## Trace to Workflow Conversion

A successful agent run can be summarized as a [WorkflowTrace] and converted
into a [WorkflowDefinition] via `WorkflowTraceSerializer`:

1. Each `ACT` agent step becomes a workflow step (`tool` + `args`).
2. Post-step screen snapshots become `expected_state` predicates.
3. Argument values that appear in the original user task become parameters.
4. Medium/high-risk tools get `policy.requires_approval = true`.

Developers can hand-edit the generated file to refine parameters, tighten
expected states, or add policy requirements before replay.

## Parsing and Validation

`WorkflowDefinitionParser` loads workflow JSON and fails closed:

- Schema version must be `1`
- `id`, `title`, and `steps` are required
- Tool names must exist in `AndroidToolCatalog`
- Tool arguments are validated (placeholders substituted with defaults first)
- `{parameter}` references must name a declared parameter

Invalid files return `WorkflowParseResult.Invalid` with every validation error.

## Related Code

```text
app/src/main/java/dev/touchpilot/app/workflow/
  WorkflowModels.kt              data classes + JSON round-trip
  WorkflowDefinitionParser.kt    JSON loader + validation
  WorkflowTraceSerializer.kt     WorkflowTrace → WorkflowDefinition
```

## Out of Scope (Milestone 10 follow-ups)

- Workflow replay engine execution
- Trace capture UI and storage paths
- Workflow review UI
