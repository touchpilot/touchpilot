# Local Model Evaluation Fixtures

A small, canonical set of static fixtures that document what each local model
role in TouchPilot is expected to answer. They are the local data contract for
Milestone 8 model-quality work, so later changes to model behavior can be
evaluated consistently.

These are **reference fixtures only**. This set intentionally does **not** train
a model, add runtime/UI behavior, or run benchmark automation — it is committed
data plus this document.

## Roles

| Role | File | Produced today by |
| --- | --- | --- |
| Intent classification | `intent-classification.json` | `agent/IntentGate.kt` |
| Tool selection | `tool-selection.json` | local router / `agent/AgentCommandProvider.kt` |
| Argument extraction | `argument-extraction.json` | local router / `localinference/LocalCommandModelRuntime.kt` |
| Screen summary | `screen-summary.json` | `screen/ScreenContextSummarizer.kt` |
| Target ranking | `../target-ranking/fixtures.json` | `tools/targets/TargetRankingEvaluation.kt` |

Target ranking already ships with its own evaluator and fixture shape; it is
listed here for completeness and is not duplicated.

## Format

Each file is a JSON object:

```json
{
  "version": 1,
  "role": "<role_name>",
  "description": "What this role answers.",
  "cases": [
    {
      "id": "stable_snake_case_id",
      "description": "One sentence on what this case checks.",
      "input": { },
      "expected": { }
    }
  ]
}
```

- `version` — fixture schema version (currently `1`).
- `role` — matches the role names in the table above.
- `cases[].id` — stable, unique within the file.
- `cases[].input` / `cases[].expected` — role-specific shapes described below.

### Role-specific shapes

- **intent_classification** — `input.task` (and optional `input.available_skills`);
  `expected.intent` is one of `exact_command`, `known_skill`, `unsafe_request`,
  `clarification_needed`, `screen_inquiry`, `local_model_needed`.
- **tool_selection** — `input.task` (and optional `input.screen`);
  `expected.tool` is an Android tool catalog name.
- **argument_extraction** — `input.task`; `expected.tool` plus `expected.args`
  with catalog argument keys (`target`, `text`, `direction`, ...).
- **screen_summary** — `input.screen` (a normalized screen context); `expected`
  may use `sentence_contains` (substrings), `suggested_tools`, and `weak_screen`.

A screen node uses: `node_id`, `role`, `text`, `bounds`, and optional
`clickable` / `scrollable` / `input_field` / `view_id_resource_name` flags.

## Authoring rules

- Keep the set small and deterministic; this is not a training dataset.
- No secrets, credentials, personal data, real accounts, or financial values.
  Unsafe-workflow cases describe the request only (e.g. "pay my phone bill") so
  the classifier's refusal can be documented — they contain no sensitive data.
- Give every case a stable `id` and a one-line `description`.

`LocalModelEvalFixturesTest` validates that every file here parses and follows
this format.
