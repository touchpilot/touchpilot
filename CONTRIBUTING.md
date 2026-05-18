# Contributing

TouchPilot is early. Contributions should keep the project Android-first,
auditable, and safe by default.

## Development Principles

- Prefer native Android APIs for device control.
- Keep tool schemas explicit and typed.
- Add risk levels to new tools.
- Add logs for agent decisions and tool execution.
- Avoid broad permissions unless the feature clearly needs them.
- Keep user-facing automation reversible or confirmable where possible.

## Suggested Workflow

1. Open an issue describing the feature or bug.
2. Keep pull requests focused.
3. Include screenshots or logs for UI/runtime changes when possible.
4. Update docs when changing architecture, tools, permissions, or safety policy.

## Titles and Labels

Issue titles should be clear and user-facing:

- `Bug: Tap by node_id fails after screen transition`
- `Feature: Add local runtime status screen`

Pull request titles should use a short prefix:

- `fix: handle missing accessibility root`
- `feat: add local runtime screen`
- `docs: add live testing checklist`
- `test: add emulator validation fixture`
- `ci: add Android lint workflow`
- `refactor: split agent settings UI`
- `chore: update Gradle config`

Use labels to describe the work:

- `type: bug`, `type: feature`, `type: docs`, `type: refactor`, `type: test`, `type: chore`
- `area: android-control`, `area: agent`, `area: local-inference`, `area: skills`, `area: mcp`, `area: security`, `area: ui`, `area: ci`, `area: docs`
- `status: needs-triage`, `status: needs-design`, `status: blocked`, `status: ready`, `status: in-progress`

Use GitHub milestones for phase and milestone tracking instead of phase labels.

## Code Style

- Kotlin for Android app and runtime code.
- C++ only for performance-sensitive native modules such as local inference.
- TypeScript is acceptable for web dashboards, docs tooling, or external
  examples.
- Python is acceptable for experiments, evaluation scripts, and prototypes.

## Safety Review

Any change touching AccessibilityService, screen capture, notifications,
contacts, SMS, files, or outbound sharing must include a short safety note in
the pull request.
