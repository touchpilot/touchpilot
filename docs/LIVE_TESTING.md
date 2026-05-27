# Live Testing

Phase 6 validates TouchPilot on a real Android runtime. The current target is
the local Android emulator AVD named `TouchPilot_API_35`.

## Emulator Setup

Start the emulator from GNOME with the `TouchPilot Emulator` launcher, or run:

```bash
/home/ubuntu/Android/Sdk/emulator/emulator -avd TouchPilot_API_35 -gpu swiftshader_indirect -accel on
```

Install the debug APK:

```bash
cd /home/ubuntu/codes/tmimmanuel/touchpilot
ANDROID_HOME=/home/ubuntu/Android/Sdk ./gradlew installDebug
```

Enable the accessibility service for test runs:

```bash
/home/ubuntu/Android/Sdk/platform-tools/adb shell settings put secure enabled_accessibility_services dev.touchpilot.app/dev.touchpilot.app.androidcontrol.TouchPilotAccessibilityService
/home/ubuntu/Android/Sdk/platform-tools/adb shell settings put secure accessibility_enabled 1
```

Launch TouchPilot:

```bash
/home/ubuntu/Android/Sdk/platform-tools/adb shell monkey -p dev.touchpilot.app 1
```

## Checklist

- [x] Emulator boots and appears in `adb devices`.
- [x] TouchPilot installs with `./gradlew installDebug`.
- [x] TouchPilot launches.
- [x] Accessibility status shows connected.
- [x] `Observe Current Screen` returns a non-empty UI tree.
- [x] `Open App` opens Settings by label.
- [ ] `Open App` followed by `wait_for_app({label=Settings})` confirms
  Settings is foreground before follow-up actions.
- [x] `Tap Text` taps a visible target.
- [x] `Type Into Focused Field` enters text into a focused field.
- [x] `Scroll Down` and `Scroll Up` work on a scrollable screen.
- [x] `Back` and `Home` work.
- [x] Medium-risk model-selected tools show inline approval prompts.
- [x] Denied approvals stop execution and log the denial.
- [x] Active skill allowlists deny disallowed tools.
- [x] Local router mode handles simple commands such as `open settings`, `back`, `home`, and `scroll`.
- [x] MCP client UI is reachable and exposes endpoint, list, call, tool, and argument controls.
- [x] `Export Debug Trace` writes a trace file.

## Phase 6 Result

Completed on commit `470c7e1` using `TouchPilot_API_35` on Android 15.

Validated live:

- `installDebug` installed the debug APK on the emulator.
- `dev.touchpilot.app/.MainActivity` launched successfully.
- Accessibility status showed connected after enabling
  `dev.touchpilot.app/dev.touchpilot.app.androidcontrol.TouchPilotAccessibilityService`.
- `Open App` opened Android Settings by launcher label.
- The Agent, local-router, MCP, tool-log, and export-trace sections were
  reachable by scrolling.
- `Type Into Focused Field` logged `type_text(...) -> ok: typeIntoFocusedField`.
- `Wait For Text` logged `wait_for_ui(...) -> ok: waitForText`.
- `Export Debug Trace` wrote a file under
  `/sdcard/Android/data/dev.touchpilot.app/files/debug-traces/`.

Fixes made from live testing:

- Core debug controls now have stable Android resource IDs for ADB/UIAutomator
  checks.
- Debug action buttons hide the soft keyboard before executing, so live tests
  can reliably tap actions after entering text.
- The type-text debug path restores focus to the text input before invoking the
  accessibility `type_text` tool.

## Evidence

For each test pass, capture:

- emulator/device name,
- Android version,
- TouchPilot commit,
- failing checklist items,
- screenshots or trace paths when useful.

Take a screenshot:

```bash
/home/ubuntu/Android/Sdk/platform-tools/adb exec-out screencap -p > tmp/live-test.png
```

Dump visible UI text:

```bash
/home/ubuntu/Android/Sdk/platform-tools/adb shell uiautomator dump /sdcard/window.xml
/home/ubuntu/Android/Sdk/platform-tools/adb shell cat /sdcard/window.xml
```

## Known Post-Milestone Follow-Up Areas

- Manual accessibility enablement should be tested, not only adb settings.
- MCP needs a local test server fixture.
- Approval UI should gain automated instrumentation coverage.
- Product UI should continue to receive emulator screenshots or recordings for
  material UX changes.
