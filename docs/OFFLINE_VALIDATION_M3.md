# Milestone 3 Offline Screen Understanding Validation

Milestone 3 proves that TouchPilot can understand Android screens locally. The
screen-understanding path must work without a cloud model, hosted backend, API
key, or network access.

This document is the offline validation checklist and the companion to the
automated JVM suite in
`app/src/test/java/dev/touchpilot/app/screen/OfflineMilestone3Test.kt`.

## Scope

These screen classes must be validated:

- TouchPilot's own chat screen.
- Android Settings.
- Android launcher.
- A screen with an input field.
- A weak or limited Accessibility screen, when one is available.

Validation must confirm:

- screen context is built locally from Accessibility data,
- summary text is generated locally,
- suggested actions are generated locally and do not execute automatically,
- no cloud/backend is required,
- sensitive text is redacted from summaries, events, logs, and traces where
  applicable.

## Automated Checklist

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "dev.touchpilot.app.screen.OfflineMilestone3Test"
```

Expected coverage:

- [ ] TouchPilot-like screen builds a local `ScreenContext`, answers `what can
      you do here`, and emits only user/assistant/final events.
- [ ] Android Settings-like screen exposes visible actions, scroll state, and
      tap/scroll suggestions.
- [ ] Launcher-like screen summarizes top visible apps and caps the suggestion
      list.
- [ ] Input-field screen keeps sensitive-looking text out of the transcript,
      final answer, assistant event payload, and suggestions.
- [ ] Weak Accessibility signals classify as weak and produce a conservative
      fallback response with no suggestions.
- [ ] No test path uses a cloud provider, backend service, Android device, or
      tool executor.

## Manual Live Checklist

Use the emulator/device setup from `docs/LIVE_TESTING.md`.

Before starting:

- [ ] Build and install the debug APK.
- [ ] Enable TouchPilot AccessibilityService.
- [ ] Use Local Router or Local Model runtime.
- [ ] Do not configure any cloud/provider API key.
- [ ] Optional: enable airplane mode for the full run.

TouchPilot screen:

- [ ] Launch TouchPilot.
- [ ] Ask `what can you do here`.
- [ ] Confirm the response names the TouchPilot screen or visible controls.
- [ ] Confirm suggested actions are listed but not executed automatically.

Android Settings:

- [ ] Open Settings.
- [ ] Return to TouchPilot or use the active observation flow.
- [ ] Ask `what screen am I on` or `what can you do here`.
- [ ] Confirm the summary mentions Settings-like visible actions.
- [ ] Confirm scroll/back/home style suggestions are present when applicable.

Launcher:

- [ ] Press Home.
- [ ] Ask for the current screen summary.
- [ ] Confirm visible launcher items are summarized locally.

Input-field screen:

- [ ] Open a screen with a text field.
- [ ] Use a sensitive-looking value such as `user@example.com` only in a test
      account or throwaway field.
- [ ] Ask for the current screen summary.
- [ ] Confirm sensitive-looking text is not shown verbatim in the summary,
      suggestion list, logs, or exported debug trace.

Weak Accessibility screen:

- [ ] Open a Canvas, SurfaceView, game, map, or other custom-rendered screen if
      available.
- [ ] Ask for the current screen summary.
- [ ] Confirm TouchPilot responds conservatively instead of guessing details.
- [ ] If no weak screen is available, record that limitation in the evidence.

Network/local-first checks:

- [ ] No cloud/provider API key is configured.
- [ ] No backend URL is required.
- [ ] If airplane mode is enabled, screen summaries still work.
- [ ] No unexpected network traffic is attributed to `dev.touchpilot.app`.

## Evidence To Capture

For each validation pass, record:

- TouchPilot commit hash,
- emulator/device name and Android version,
- runtime mode,
- `./gradlew :app:testDebugUnitTest --tests "dev.touchpilot.app.screen.OfflineMilestone3Test"` output,
- screenshots or recordings of the representative screens,
- exported debug trace path,
- any weak-screen limitation or failed checklist item.

## Current Automated Coverage

The JVM suite covers representative fixtures for TouchPilot, Settings, launcher,
input-field, and weak Accessibility contexts. It validates the local
`ScreenContextBuilder`, `ScreenContextSummarizer`, screen-inquiry path through
`DefaultLocalReasoningCore`, and redacted assistant event payloads.

Manual live validation is still expected before declaring Milestone 3 complete
on real devices.
