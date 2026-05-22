# Milestone 3 Offline Validation

Milestone 3 (Local Screen Understanding) is not complete until TouchPilot can understand Android screens locally without cloud vision or chat models.

This document is the offline validation checklist for Milestone 3 and the companion to the automated unit suite in `app/src/test/java/dev/touchpilot/app/screen/OfflineMilestone3Test.kt`.

## Scope

These flows must all succeed with the device in airplane mode and no provider secret configured:

- **Screen context capture** — AccessibilityService builds ScreenContext from the active window
- **Local summary generation** — ScreenContextSummarizer produces a summary without network calls
- **Suggested actions** — ScreenContextSummarizer generates action suggestions locally
- **Redaction** — Sensitive text (passwords, emails, API keys) is redacted in logs and exports
- **Five target screens** — TouchPilot chat, Android Settings, Launcher, Input field, Weak Accessibility

## What This Validates

Milestone 3's core claim: TouchPilot can understand Android screens locally without cloud vision or chat models.

- Screen context is built locally via AccessibilityService (no OCR fallback, no cloud vision)
- Summary is generated locally via ScreenContextSummarizer (no OpenAI or any API calls)
- Suggested actions are generated locally via ScreenContextSummarizer
- NO cloud/backend calls are made (verified via logcat, network monitor, or airplane mode)
- Sensitive text (passwords, emails) is redacted in logs and exports

## Prerequisites

- Build with `./gradlew assembleDebug` (no network required after the first build)
- For unit verification: `./gradlew test` runs the offline suite on the JVM with no Android device, no emulator, and no provider secret
- For live verification on an emulator or device:
  - Put the device in airplane mode
  - Confirm `Settings -> Apps -> TouchPilot -> Permissions` shows no cleared provider secret
  - Confirm the runtime mode is `Local Router` or `Local Model` in the agent panel
  - Enable AccessibilityService: `adb shell settings put secure enabled_accessibility_services dev.touchpilot.app/dev.touchpilot.app.androidcontrol.TouchPilotAccessibilityService`

## Checklist

### Automated (JVM unit suite)

Every test drives the real screen-understanding components and asserts their behavior:

- [ ] `./gradlew :app:testDebugUnitTest --tests "dev.touchpilot.app.screen.OfflineMilestone3Test"` passes
- [ ] `ScreenContextBuilder` builds normalized context from raw Accessibility snapshots
- [ ] `ScreenContextBuilder` filters empty layout containers and keeps only signal nodes
- [ ] `ScreenContextBuilder` applies redaction via `ScreenText.of()` for all node text
- [ ] `ScreenContextSummarizer` produces a deterministic summary from ScreenContext
- [ ] `ScreenContextSummarizer` generates suggested actions for visible controls
- [ ] `ScreenContextSummarizer` filters sensitive nodes from suggestions
- [ ] `ScreenContextSummarizer` always includes back/home navigation suggestions
- [ ] `SensitiveTextRedactor` redacts passwords, emails, API keys, and credit cards
- [ ] `SensitiveTextRedactor` detects sensitive text via `containsSensitiveText()`
- [ ] `ScreenText` holds raw + displaySafe versions with correct `isSensitive` flag
- [ ] `ScreenText.of()` applies redaction when sensitive patterns are detected
- [ ] `ScreenContext.containsSensitiveContent` correctly flags screens with sensitive data
- [ ] `ScreenContext.toJson()` with `redacted=true` produces redacted JSON output
- [ ] `ToolExecutionLog` records redacted tool arguments and messages

### Live (emulator or device, required for full validation)

For each of the 5 target screens, confirm:

#### 1. TouchPilot Chat Interface
- [ ] Device is in airplane mode for the duration of the test
- [ ] `Observe Current Screen` returns a non-empty ScreenContext
- [ ] Screen summary is generated locally (no network call)
- [ ] Suggested actions include tap, type, scroll, back, home
- [ ] No outbound network requests appear in `adb shell dumpsys netstats` attributable to `dev.touchpilot.app`

#### 2. Android Settings Screen
- [ ] Device is in airplane mode
- [ ] Navigate to Settings app
- [ ] `Observe Current Screen` returns ScreenContext with Settings package name
- [ ] Summary mentions "Settings" screen and visible actions
- [ ] Suggested actions include Settings-specific controls (e.g., "Network", "Display")
- [ ] No outbound network requests

#### 3. Launcher/Home Screen
- [ ] Device is in airplane mode
- [ ] Press home to navigate to launcher
- [ ] `Observe Current Screen` returns ScreenContext with launcher package
- [ ] Summary identifies the launcher screen
- [ ] Suggested actions include app icons and navigation
- [ ] No outbound network requests

#### 4. Input Field Screen (e.g., Browser Search)
- [ ] Device is in airplane mode
- [ ] Open browser or app with text input field
- [ ] Focus an input field
- [ ] `Observe Current Screen` returns ScreenContext with input field node
- [ ] Summary mentions the input field
- [ ] Suggested actions include "Type into [field]"
- [ ] No outbound network requests

#### 5. Weak/Limited Accessibility Screen (if testable)
- [ ] Device is in airplane mode
- [ ] Navigate to an app with limited Accessibility exposure (e.g., some games or custom views)
- [ ] `Observe Current Screen` returns ScreenContext (may be minimal)
- [ ] Summary handles weak context gracefully (mentions limited UI data)
- [ ] Suggested actions still include back/home navigation
- [ ] No outbound network requests

### Redaction Verification (Live)

- [ ] Type a password into an input field (e.g., "my password is hunter2")
- [ ] Observe the screen context via debug UI
- [ ] Confirm password text is redacted in the summary (shows "[REDACTED]" or similar)
- [ ] Confirm password text is redacted in the tool execution log
- [ ] Type an email address (e.g., "user@example.com")
- [ ] Confirm email is redacted in logs
- [ ] Export debug trace and verify sensitive text is redacted in the exported file

### Network Verification (Live)

- [ ] Enable airplane mode before launching TouchPilot
- [ ] Run all screen observation flows
- [ ] Run `adb shell dumpsys netstats detail` and confirm no rx/tx bytes for `dev.touchpilot.app`
- [ ] Run `adb logcat | grep -i touchpilot` and confirm no HTTP/network error logs
- [ ] Disable airplane mode and repeat to confirm no difference in behavior (should work offline)

## Evidence

For each pass, capture:

- TouchPilot commit hash
- Date and runtime mode (Local Router or Local Model)
- `./gradlew test` output for the offline suite
- For live runs: device or emulator name, Android version
- Copy of agent event log exported through `Export Debug Trace`
- Network monitoring output (`dumpsys netstats detail`)
- Screenshots of each screen type with visible summaries

## Manual Test Steps

### Airplane Mode Test

```bash
# Enable airplane mode
adb shell settings put global airplane_mode_on 1
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE

# Verify airplane mode is on
adb shell settings get global airplane_mode_on
# Should return: 1

# Launch TouchPilot
adb shell monkey -p dev.touchpilot.app 1

# Run screen observation tests
# (Use the app's debug UI to observe screens)

# Check network stats
adb shell dumpsys netstats detail | grep dev.touchpilot.app
# Should show no entries or zero bytes

# Disable airplane mode after testing
adb shell settings put global airplane_mode_on 0
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE
```

### Logcat Monitoring

```bash
# Monitor TouchPilot logs
adb logcat | grep -i touchpilot

# Look for any network-related errors or API calls
# Should see only local processing logs
```

### Network Monitoring

```bash
# Get network stats before test
adb shell dumpsys netstats detail > before_netstats.txt

# Run screen observation flows

# Get network stats after test
adb shell dumpsys netstats detail > after_netstats.txt

# Compare - should be identical or show zero bytes for TouchPilot
diff before_netstats.txt after_netstats.txt
```

### Redaction Testing

```bash
# Type sensitive text into an input field via debug UI
# Example: "my password is hunter2"

# Export debug trace via app UI

# Pull the trace file
adb pull /sdcard/Android/data/dev.touchpilot.app/files/debug-traces/latest.json

# Search for sensitive text in the trace
grep -i "hunter2" latest.json
# Should return no matches (text should be [REDACTED])
```

## Known Post-Milestone Follow-Up Areas

- Add instrumentation tests that drive the full screen-understanding flow end-to-end against a fake AccessibilityService
- Extend redaction patterns to cover additional sensitive data types (SSN, phone numbers, etc.)
- Add visual diff regression tests for screen summaries
- Integrate OCR fallback validation once OCR is implemented
- Add performance benchmarks for screen context building on large UI trees

## References

- `docs/OFFLINE_VALIDATION_M2.md` — Milestone 2 offline validation template
- `docs/LIVE_TESTING.md` — Live testing procedures and emulator setup
- `docs/LOCAL_INFERENCE.md` — Local inference architecture and LiteRT model contract
- `app/src/main/java/dev/touchpilot/app/screen/ScreenContextBuilder.kt` — Screen context normalization
- `app/src/main/java/dev/touchpilot/app/screen/ScreenContextSummarizer.kt` — Local summary generation
- `app/src/main/java/dev/touchpilot/app/security/SensitiveTextRedactor.kt` — Text redaction logic
