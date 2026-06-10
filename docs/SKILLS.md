# Skills

TouchPilot skills are local Markdown files that package reusable Android task
knowledge. Bundled skills live under:

```text
app/src/main/assets/skills/<skill-id>/SKILL.md
```

Skills are advisory context for the local agent. They can narrow tool
visibility and describe successful task behavior, but they do not bypass tool
validation, skill allowlists, approval prompts, or safety policy.

## Skills v2 Format

Each `SKILL.md` should start with a front matter block followed by normal
Markdown instructions:

```markdown
---
id: settings
title: Settings
description: Navigate and inspect Android Settings screens safely.
risk: medium
aliases:
  - settings
  - android settings
  - wi-fi settings
allowed_tools:
  - observe_screen_context
  - open_app
  - open_settings_panel
  - tap
  - scroll
  - press_back
  - wait_for_idle
  - wait_for_app
success_criteria:
  - The requested Settings screen is foreground.
  - The agent stops after the requested state is visible.
examples:
  - open Wi-Fi settings
  - show Bluetooth settings
  - go to this app's notification settings
---

# Settings

Prefer direct settings panels when available. Observe the screen before tapping
ambiguous targets, and stop when the requested settings screen is visible.
```

The front matter block is the machine-readable contract. The Markdown body is
human-readable guidance that may also be included in agent context.

## Required Fields

`id`
: Stable lower-case identifier for the skill. Use `a-z`, `0-9`, and `-`.
  The `id` must match the bundled directory name.

`title`
: Short user-facing skill name.

`description`
: One-sentence summary shown in UI and passed to the agent.

`risk`
: Skill-level risk hint. Allowed values are `low`, `medium`, and `high`.
  Risk can make approval copy more cautious, but it must never reduce the
  risk of an individual tool call.

`allowed_tools`
: List of tool names the skill may request. Every value must match a tool in
  the Android tool catalog. Tool execution still goes through normal argument
  validation, approval, and policy checks.

## Optional Fields

`aliases`
: Natural-language names or phrases that can match this skill. Keep aliases
  specific enough that exact commands such as `back`, `home`, and `scroll` are
  not captured by skill matching.

`success_criteria`
: Observable outcomes that tell the agent when to stop. Criteria should be
  concrete and screen-checkable, not broad goals.

`examples`
: Example user requests that should match the skill. Examples are matching and
  prompt context, not tests by themselves.

## Authoring Rules

- Keep the front matter at the top of the file.
- Use lower_snake_case metadata keys.
- Keep metadata values as strings or lists of strings.
- Prefer the smallest useful `allowed_tools` list.
- Use `observe_screen_context` for agent decisions and reserve
  `observe_screen` for raw debugging skills.
- Mark messaging, purchases, account changes, deletion, permissions, and
  security-sensitive workflows as `high` risk.
- Do not include secrets, tokens, personal data, or device-specific state in
  bundled skills.

## Runtime Expectations

The Skills v2 runtime should use this contract as follows:

- `SkillStore` parses metadata and Markdown body from bundled assets.
- `SkillRegistry` exposes enabled skills and lookup by ID, title, and alias.
- `IntentGate` can match enabled skills from aliases and examples while exact
  deterministic commands keep priority.
- Agent prompts can include description, allowed tools, examples, risk, and
  success criteria.
- Tool execution enforces the active skill allowlist outside the model.
- Approval and policy layers may use skill risk as additional context, never as
  a reason to lower caution.
