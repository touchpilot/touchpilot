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

- [ ] Emulator boots and appears in `adb devices`.
- [ ] TouchPilot installs with `./gradlew installDebug`.
- [ ] TouchPilot launches.
- [ ] Accessibility status shows connected.
- [ ] `Observe Current Screen` returns a non-empty UI tree.
- [ ] `Open App` opens Settings by label.
- [ ] `Tap Text` taps a visible target.
- [ ] `Type Into Focused Field` enters text into a focused field.
- [ ] `Scroll Down` and `Scroll Up` work on a scrollable screen.
- [ ] `Back` and `Home` work.
- [ ] Medium-risk model-selected tools show approval prompts.
- [ ] Denied approvals stop execution and log the denial.
- [ ] Active skill allowlists deny disallowed tools.
- [ ] Local router mode handles simple commands such as `open settings`, `back`, `home`, and `scroll`.
- [ ] MCP client can initialize a test endpoint and list/call tools.
- [ ] `Export Debug Trace` writes a trace file.

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

## Known Follow-Up Areas

- Manual accessibility enablement should be tested, not only adb settings.
- MCP needs a local test server fixture.
- Approval UI needs more detailed policy reasons in Phase 10.
- Product UI issues should feed Phase 9.
