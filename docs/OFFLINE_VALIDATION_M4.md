# Milestone 4 Reliable Android Control Validation

Milestone 4 hardens TouchPilot's Android control layer so local agents can act
through deterministic, auditable tools instead of guessing from raw screen
dumps.

## Completion Summary

- Target selectors are represented as structured arguments: text, content
  description, node ID, bounds, view ID, role, and input/scroll constraints.
- `TargetResolver` ranks visible `ScreenContext` nodes deterministically and
  fails safely on missing, disabled, low-confidence, or ambiguous targets.
- `tap`, `long_press`, `type_text`, `focus_input`, `clear_text`, `scroll`, and
  `swipe` route target selection through the shared resolver path where a
  target is needed.
- `wait_for_idle`, `wait_for_app`, and `wait_for_ui` provide bounded wait
  behavior for post-action settling.
- Tool execution returns explicit success/failure messages and verification
  data so the agent loop can stop instead of blindly continuing.
- Emulator live tests run from GitHub Actions through
  `.github/workflows/android-live-tests.yml`.

## Issue 75 Acceptance Criteria

- [x] Target selector model is defined.
- [x] Target resolver ranks and rejects candidates safely.
- [x] `tap` uses resolved selectors and reports ambiguity clearly.
- [x] `type_text` can focus and handle input targets reliably.
- [x] `scroll` can target scrollable containers where possible.
- [x] Wait/retry policies are structured and bounded.
- [x] Tool results are verified after execution.
- [x] Milestone 4 offline/live validation is documented.

## Validation Commands

Run the focused unit coverage:

```bash
./gradlew testDebugUnitTest --tests 'dev.touchpilot.app.tools.*' --tests 'dev.touchpilot.app.tools.targets.*'
```

Run the broader local check:

```bash
./gradlew testDebugUnitTest
```

Run live emulator validation through GitHub Actions:

```bash
gh workflow run android-live-tests.yml --repo touchpilot/touchpilot
```

## Follow-Up Scope

Milestone 4 is complete as the reliable-control foundation. Remaining product
hardening should be tracked as post-milestone bugs or Milestone 6+ work, such
as broader real-device coverage, additional Tools page controls, and UI polish
for tool logs.
