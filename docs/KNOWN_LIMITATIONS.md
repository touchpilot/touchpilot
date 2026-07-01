# Known Limitations

Documented incompatibilities, OEM-specific behavior, and workarounds for
TouchPilot on real Android devices. This page is a Milestone 13 deliverable
(issue #387). Update it whenever compatibility testing finds new breakage.

Before filing a bug, check whether your device profile is already listed here.
When reporting a new issue, link to the relevant limitation entry if one exists.

## How entries are structured

Each limitation includes:

- **Profile** — OEM/skin, device family, API range
- **Symptom** — what the user or tester observes
- **Affected tools** — tool names from [Tool Spec](TOOL_SPEC.md)
- **Workaround** — steps that restore expected behavior, if any
- **Tracking** — GitHub issue or result log path

## Emulator and stock Android

### Weak or empty accessibility trees

| Field | Value |
|-------|-------|
| Profile | All devices; worst on WebView-heavy or custom-drawn UIs |
| Symptom | `observe_screen` returns sparse trees; tap/type targets missing |
| Affected tools | `observe_screen`, `tap`, `type_text`, `scroll` |
| Workaround | Retry after `wait_for_idle`; OCR fallback path documented in [OCR Fallback](OCR_FALLBACK.md) |
| Tracking | [OCR_FALLBACK.md](OCR_FALLBACK.md) |

### `press_home` verification heuristic

| Field | Value |
|-------|-------|
| Profile | OEM launchers whose package name does not contain `launcher` |
| Symptom | Tool executes but post-action verification may report inconclusive |
| Affected tools | `press_home` |
| Workaround | Confirm visually; file a matrix result with launcher package name |
| Tracking | Compatibility matrix — pending OEM data |

### Settings panel intent variance

| Field | Value |
|-------|-------|
| Profile | Android 12–15; OEM-skinned Settings apps |
| Symptom | `open_settings_panel` may open the wrong panel or generic Settings |
| Affected tools | `open_settings_panel` |
| Workaround | Use `open_app` with a visible Settings label; navigate manually |
| Tracking | [LIVE_TESTING.md](LIVE_TESTING.md) unchecked panel items |

## Samsung One UI

### Battery optimization and background limits

| Field | Value |
|-------|-------|
| Profile | Samsung One UI, API 31+ |
| Symptom | Accessibility service disconnects after screen off or aggressive sleep |
| Affected tools | All tools requiring an active AccessibilityService |
| Workaround | Disable battery optimization for TouchPilot; add to "Never sleeping apps" in Device care |
| Tracking | Matrix row not yet validated — see [Compatibility Matrix](COMPATIBILITY_MATRIX.md) |

## Xiaomi MIUI / HyperOS

### Autostart and battery saver

| Field | Value |
|-------|-------|
| Profile | Xiaomi / Redmi / POCO, MIUI 13+ / HyperOS |
| Symptom | Service disabled after reboot; agent stops in background |
| Affected tools | All tools requiring an active AccessibilityService |
| Workaround | Enable Autostart, disable battery restrictions, pin app in recents during tests |
| Tracking | Matrix row not yet validated |

## Oppo / Realme ColorOS

### Accessibility persistence

| Field | Value |
|-------|-------|
| Profile | ColorOS 12+, API 31+ |
| Symptom | Accessibility toggle reverts or requires re-confirmation after updates |
| Affected tools | Permission flow; all control tools |
| Workaround | Re-enable from system Settings; disable app "auto manage" battery options |
| Tracking | Matrix row not yet validated |

## Testing gaps (not device bugs)

### Manual accessibility enablement undertested

| Field | Value |
|-------|-------|
| Profile | All |
| Symptom | Most CI and early validation used adb to enable the service |
| Affected tools | Permission onboarding |
| Workaround | Run [Device Compatibility Checklist](DEVICE_COMPATIBILITY_CHECKLIST.md) §1 with manual enablement |
| Tracking | [LIVE_TESTING.md](LIVE_TESTING.md) follow-up |

### MCP local test server fixture missing

| Field | Value |
|-------|-------|
| Profile | All |
| Symptom | MCP integration cannot be fully validated in automated runs |
| Affected tools | MCP client UI only |
| Workaround | Manual MCP server for exploratory testing |
| Tracking | [LIVE_TESTING.md](LIVE_TESTING.md) follow-up |

## Adding a new limitation

1. Complete a run using [Device Compatibility Checklist](DEVICE_COMPATIBILITY_CHECKLIST.md).
2. Add a subsection under the appropriate OEM heading (or create one).
3. Update the matrix row status to `fail` or `partial`.
4. Open a GitHub issue if engineering work is required; link it in **Tracking**.

## Related docs

- [Compatibility Matrix](COMPATIBILITY_MATRIX.md)
- [Device Compatibility Checklist](DEVICE_COMPATIBILITY_CHECKLIST.md)
- [Live Testing](LIVE_TESTING.md)
- [OCR Fallback](OCR_FALLBACK.md)
