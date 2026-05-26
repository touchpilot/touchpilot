# Contributing

TouchPilot is early. Contributions should keep the project **Android-first, auditable, and safe by default**.

## Development Principles

- Prefer native Android APIs for device control.
- Keep tool schemas explicit and strongly typed.
- Assign risk levels to all new tools.
- Add logs for agent decisions and tool execution.
- Avoid broad permissions unless the feature clearly requires them.
- Keep user-facing automation reversible or confirmable whenever possible.

## Before You Open a PR

### Test locally with your TouchPilot setup

All external pull requests **must include a completed _Real Behavior Proof_ section** in the PR body.

Please include:

- The real device or emulator setup you tested
- The exact commands or steps you ran after applying your changes
- Evidence that the fix or feature works after the patch
- The observed runtime result
- Anything you did **not** test

Accepted proof includes:

- Screen recordings (**preferred**)
- Screenshots
- Terminal logs
- Runtime logs (redacted if needed)
- Console output
- Linked artifacts

Unit tests, mocks, lint checks, and CI are valuable, but **do not satisfy this requirement on their own**.

### Acceptance Criteria

Pull requests that include **live behavior proof** (video strongly preferred) will be considered for review.

Pull requests without real runtime proof may be closed without review.

## Suggested Workflow

1. Open an issue describing the bug or feature.
2. Keep pull requests focused and limited in scope.
3. Include screenshots or logs for UI or runtime changes whenever possible.
4. Update documentation when changing architecture, tools, permissions, or safety policy.

## Titles and Labels

Issue titles should be clear and user-facing:

- `Bug: Tap by node_id fails after screen transition`
- `Feature: Add local runtime status screen`

Pull request titles should use a short prefix, optionally with an area scope in
parentheses (the scope should match one of the `area:` labels below):

- `fix: handle missing accessibility root`
- `feat: add local runtime screen`
- `feat(android-control): add long_press tool`
- `fix(settings): flip panel transition direction`
- `docs: add live testing checklist`
- `test: add emulator validation fixture`
- `ci: add Android lint workflow`
- `refactor: split agent settings UI`
- `chore: update Gradle config`

Use labels to describe the work:

### Type

- `type: bug`
- `type: feature`
- `type: docs`
- `type: refactor`
- `type: test`
- `type: chore`

### Area

- `area: android-control`
- `area: agent`
- `area: local-inference`
- `area: skills`
- `area: mcp`
- `area: security`
- `area: ui`
- `area: ci`
- `area: docs`

### Status

- `status: needs-triage`
- `status: needs-design`
- `status: blocked`
- `status: ready`
- `status: in-progress`

Use GitHub milestones for phase and milestone tracking instead of phase labels.
Milestones should use clear names such as `Milestone 1: Local-First Agent
Foundation`; individual issues and PRs should rely on type, area, and status
labels rather than phase labels.

## Code Style

- **Kotlin** for Android app and runtime code
- **C++** only for performance-sensitive native modules such as local inference
- **TypeScript** for web dashboards, documentation tooling, or external examples
- **Python** for experiments, evaluation scripts, and prototypes

## Safety Review

Any change affecting the following areas **must include a short safety note** in the pull request:

- `AccessibilityService`
- Screen capture
- Notifications
- Contacts
- SMS
- Files
- Outbound sharing

The safety note should briefly explain:

- What data or capability is being accessed
- Why access is necessary
- How misuse is prevented
- Any user confirmation or consent involved
