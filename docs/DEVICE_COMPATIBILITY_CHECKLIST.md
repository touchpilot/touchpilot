# Device Compatibility Checklist

Step-by-step workflow for validating TouchPilot on a physical device or emulator
profile from the [Compatibility Matrix](COMPATIBILITY_MATRIX.md). Implements the
tester workflow from issue #387.

All testing uses **local debug builds**. No telemetry is collected. Failure
reports must be inspectable locally via exported debug traces.

## Before you start

Record these fields at the top of your result log:

```text
Device:
OEM / skin:
Android version / API:
TouchPilot commit:
Build type: debug
Accessibility enabled via: manual | adb
Tester:
Date:
```

Install the app:

```bash
./gradlew installDebug
```

Optional: enable accessibility via adb for repeatable runs (also test manual
enablement at least once per OEM):

```bash
adb shell settings put secure enabled_accessibility_services dev.touchpilot.app/dev.touchpilot.app.androidcontrol.TouchPilotAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Launch TouchPilot:

```bash
adb shell monkey -p dev.touchpilot.app 1
```

## 1. Permission and onboarding

- [ ] App installs without error.
- [ ] App launches to the main screen.
- [ ] Accessibility status shows **connected** (after enablement).
- [ ] Accessibility can be enabled through **system Settings UI** (not only adb).
- [ ] Accessibility remains enabled after force-stopping and relaunching TouchPilot.
- [ ] Foreground service notification appears when the agent loop is active.
- [ ] Battery optimization is disabled or a documented OEM workaround is applied
      (see [Known Limitations](KNOWN_LIMITATIONS.md)).
- [ ] Foreground service persistence review:
  - [ ] Run an agent task, background the app, and wait 5 minutes.
  - [ ] Return to app and confirm tool execution still works.
  - [ ] Document OEM settings path used to verify service persistence.
- [ ] `Export Debug Trace` writes a file under the app files directory.

## 2. Core tool execution

Run each action from the Tools page (or agent) and confirm a successful tool log
entry. Use Android Settings as the target app unless noted.

| # | Tool | Action | Pass | Notes |
|---|------|--------|------|-------|
| 1 | Observe | `Observe Current Screen` returns a non-empty UI tree | [ ] | |
| 2 | Open app | `Open App` opens Settings by label | [ ] | |
| 3 | Wait | `wait_for_app` confirms Settings is foreground | [ ] | |
| 4 | Tap | `Tap Text` taps a visible Settings row | [ ] | |
| 5 | Scroll | `Scroll Down` then `Scroll Up` on a scrollable screen | [ ] | |
| 6 | Type | `Type Into Focused Field` enters text in a focused field | [ ] | |
| 7 | Wait | `wait_for_idle` settles after scroll or type | [ ] | |
| 8 | Back | `Back` returns to the previous screen | [ ] | |
| 9 | Home | `Home` returns to the launcher | [ ] | |

## 3. Automated smoke (optional)

On a connected device or emulator, run the compatibility instrumentation smoke
test. It logs device metadata and exercises observe → Settings → scroll →
back → home without requiring TouchPilot's accessibility service:

```bash
./gradlew connectedDebugAndroidTest \
  --tests 'dev.touchpilot.app.compatibility.DeviceCompatibilitySmokeLiveTest'
```

Capture logcat lines tagged `DeviceCompatSmoke` for your result log.

## 4. Record results

1. Copy [`compatibility/results/TEMPLATE.md`](compatibility/results/TEMPLATE.md)
   to `docs/compatibility/results/YYYY-MM-DD-<device-slug>.md`.
2. Fill in pass/fail per section and attach evidence paths.
3. Update the matrix row in [COMPATIBILITY_MATRIX.md](COMPATIBILITY_MATRIX.md)
   and [`compatibility/matrix.json`](compatibility/matrix.json).
4. If anything failed, add or update an entry in
   [Known Limitations](KNOWN_LIMITATIONS.md).

## Evidence commands

Screenshot:

```bash
adb exec-out screencap -p > tmp/compat-<device>.png
```

UI dump:

```bash
adb shell uiautomator dump /sdcard/window.xml
adb shell cat /sdcard/window.xml
```

Device metadata:

```bash
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
```

## Safety rules

- Do not modify device settings outside the tool scope under test.
- Do not use cloud device farms for canonical matrix rows; local reproducibility
  is required.
- Redact sensitive content in shared logs; use TouchPilot's exported traces which
  apply built-in redaction.

## Related docs

- [Compatibility Matrix](COMPATIBILITY_MATRIX.md)
- [Known Limitations](KNOWN_LIMITATIONS.md)
- [Live Testing](LIVE_TESTING.md)
- [Tool Spec](TOOL_SPEC.md)
