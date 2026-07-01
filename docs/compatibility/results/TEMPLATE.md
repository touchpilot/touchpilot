# Compatibility Result — YYYY-MM-DD — &lt;device-slug&gt;

Copy this template to `docs/compatibility/results/YYYY-MM-DD-<device-slug>.md` after
each compatibility run.

## Run metadata

| Field | Value |
|-------|-------|
| Device | |
| OEM / skin | |
| Android version | |
| API level | |
| TouchPilot commit | |
| Build type | debug |
| Accessibility enabled via | manual / adb |
| Tester | |
| Date | |

## Overall status

`pass` | `fail` | `partial` | `blocked`

## Permission and onboarding

| Check | Result | Notes |
|-------|--------|-------|
| Install | | |
| Launch | | |
| Accessibility connected | | |
| Manual a11y enablement | | |
| A11y persistence | | |
| Foreground notification | | |
| Battery optimization | | |
| Debug trace export | | |

## Core tools

| Tool | Result | Notes |
|------|--------|-------|
| observe | | |
| open_app | | |
| wait (app/idle) | | |
| tap | | |
| scroll | | |
| type | | |
| back | | |
| home | | |

## Instrumentation smoke (optional)

| Field | Value |
|-------|-------|
| Ran `DeviceCompatibilitySmokeLiveTest` | yes / no |
| Log tag | `DeviceCompatSmoke` |
| Outcome | |

## Evidence

- Screenshot:
- Trace export path:
- Screen recording:
- Logcat excerpt:

## Matrix update

- [ ] Updated [COMPATIBILITY_MATRIX.md](../../COMPATIBILITY_MATRIX.md)
- [ ] Updated [matrix.json](../matrix.json)
- [ ] Added/updated [KNOWN_LIMITATIONS.md](../../KNOWN_LIMITATIONS.md) if needed

## Issues filed

- 
