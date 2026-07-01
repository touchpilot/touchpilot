# Device Compatibility Matrix

TouchPilot compatibility testing tracks core tool execution and permission
flows across Android API levels and OEM skins. This matrix is the canonical
status board for Milestone 13 real-device beta work (issue #387).

## How to use

1. Pick an untested or stale row from the matrix below.
2. Follow [Device Compatibility Checklist](DEVICE_COMPATIBILITY_CHECKLIST.md).
3. Log the run under `docs/compatibility/results/` using the result template.
4. Update this page and [Known Limitations](KNOWN_LIMITATIONS.md) when you find
   breakage or OEM-specific workarounds.

Machine-readable status lives in [`compatibility/matrix.json`](compatibility/matrix.json).

## Status legend

| Status | Meaning |
|--------|---------|
| `pass` | All checklist items succeeded on this profile |
| `fail` | One or more core tools or permission steps failed |
| `blocked` | Could not complete testing (missing hardware, adb blocked, etc.) |
| `not tested` | No recorded run yet |

## API baseline (stock Google emulator)

CI runs `connectedDebugAndroidTest` on stock Google API images via
[`.github/workflows/android-live-tests.yml`](../.github/workflows/android-live-tests.yml).
These rows cover API-level regression; they do not replace OEM skin testing.

| Profile | API | Android | observe | tap | type | scroll | open app | back | home | wait | permissions | Last run | Notes |
|---------|-----|---------|---------|-----|------|--------|----------|------|------|------|-------------|----------|-------|
| Pixel 6 (stock) | 31 | 12 | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | — | CI matrix job |
| Pixel 6 (stock) | 33 | 13 | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | — | CI matrix job |
| Pixel 6 (stock) | 34 | 14 | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | — | CI matrix job |
| Pixel 6 (stock) | 35 | 15 | pass | pass | pass | pass | pass | pass | pass | partial | pass | 2026-03 | See [Live Testing](LIVE_TESTING.md) Phase 6 |

## OEM skin matrix (manual, local builds)

Test each profile with a local debug APK (`./gradlew installDebug`). Prefer
manual accessibility enablement at least once per OEM to catch settings UI
differences.

| OEM / skin | Example device | API range | observe | tap | type | scroll | open app | back | home | wait | permissions | Status | Notes |
|------------|----------------|-----------|---------|-----|------|--------|----------|------|------|------|-------------|--------|-------|
| Google Pixel (stock) | Pixel 6–9 | 31–35 | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | Reference stock behavior |
| Samsung One UI | Galaxy S / A series | 31–35 | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | Battery optimization prompts vary by One UI version |
| Xiaomi MIUI / HyperOS | Redmi / POCO | 31–35 | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | Autostart and battery saver often required |
| OnePlus OxygenOS | Nord / flagship | 31–35 | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | |
| Motorola (near-stock) | Moto G / Edge | 31–35 | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | |
| Oppo ColorOS | Find / Reno | 31–35 | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | not tested | Track separately; common a11y persistence issues |

## Core tools under test

These map to [Tool Spec](TOOL_SPEC.md) entries exercised in the checklist:

| Checklist name | Tool name(s) |
|----------------|--------------|
| Observe | `observe_screen`, `observe_screen_context` |
| Tap | `tap` |
| Type | `type_text` |
| Scroll | `scroll`, `scroll_to_element` |
| Open app | `open_app` |
| Back | `press_back` |
| Home | `press_home` |
| Wait | `wait_for_idle`, `wait_for_app`, `wait_for_ui` |

## Permission and onboarding checks

| Step | What to verify |
|------|----------------|
| Install | `./gradlew installDebug` succeeds |
| Accessibility | Service can be enabled manually from system settings |
| Accessibility persistence | Service remains enabled after app restart |
| Notifications | Foreground service notification visible when agent runs |
| Battery optimization | App exempted or documented workaround applied |
| Debug trace export | `Export Debug Trace` writes under app files dir |

## Evidence requirements

Each matrix update should cite:

- device or emulator name,
- Android version and API level,
- TouchPilot commit (`git rev-parse --short HEAD`),
- accessibility enablement method (manual UI vs adb),
- result log path under `docs/compatibility/results/`,
- exported trace path or screen recording when a row fails.

## Related docs

- [Device Compatibility Checklist](DEVICE_COMPATIBILITY_CHECKLIST.md)
- [Known Limitations](KNOWN_LIMITATIONS.md)
- [Live Testing](LIVE_TESTING.md)
- [Offline Validation M4](OFFLINE_VALIDATION_M4.md)
