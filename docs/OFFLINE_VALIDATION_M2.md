# Milestone 2 Offline Validation

Milestone 2 (Hybrid Local AI Core) is not complete until the main user flows
run with no cloud provider, no API key, and no backend service configured.

This document is the offline validation checklist for Milestone 2 and the
companion to the automated unit suite in
`app/src/test/java/dev/touchpilot/app/agent/OfflineMilestone2Test.kt`.

## Scope

These flows must all succeed with the device in airplane mode and no provider
secret configured:

- `Hello` — conversational gate greeting.
- `Help` — conversational gate help reply.
- `Open Settings` — deterministic local router routes to `open_app`.
- `Go back` — deterministic local router routes to `press_back`.
- One ambiguous request — local-model-needed path routes through
  `LocalModelCommandProvider`, falling through to the deterministic router
  when no local model asset is bundled.
- One blocked secret-entry path — `DefaultActionPolicy` blocks `type_text`
  when the text contains a recognized secret, and the agent emits a
  `policy_blocked` event.

## Prerequisites

- Build with `./gradlew assembleDebug` (no network required after the first
  build).
- For unit verification: `./gradlew test` runs the offline suite on the JVM
  with no Android device, no emulator, and no provider secret.
- For live verification on an emulator or device:
  - Put the device in airplane mode.
  - Confirm `Settings -> Apps -> TouchPilot -> Permissions` shows no cleared
    provider secret.
  - Confirm the runtime mode is `Local Router` or `Local Model` in the agent
    panel.

## Checklist

### Automated (JVM unit suite)

- [ ] `./gradlew test --tests "dev.touchpilot.app.agent.OfflineMilestone2Test"` passes.
- [ ] `Hello` flow yields a `user_message` followed by an `assistant_message`
      whose text matches `ConversationalGate` and no tool events.
- [ ] `Help` flow yields a `user_message` followed by an `assistant_message`
      whose text matches `ConversationalGate` and no tool events.
- [ ] `Open Settings` flow drives `LocalRouterCommandProvider` to emit
      `observe_screen` then `open_app(target=settings)`, and the matching
      event sequence ends with `tool_succeeded`.
- [ ] `Go back` flow drives `LocalRouterCommandProvider` to emit
      `observe_screen` then `press_back`, and the matching event sequence
      ends with `tool_succeeded`.
- [ ] Ambiguous request (`show me something useful`) routes through
      `LocalModelCommandProvider` with a stub local runtime and never calls
      a cloud client; deterministic fallback still produces a parseable
      command or a `final` answer.
- [ ] `type_text` with `text=password=hunter2` is rejected by
      `DefaultActionPolicy` and the agent emits a `policy_blocked` event.

### Live (emulator or device, optional but recommended)

- [ ] Device is in airplane mode for the duration of the run.
- [ ] `Hello` typed in the chat returns the conversational reply.
- [ ] `Help` typed in the chat returns the conversational reply.
- [ ] `Open Settings` opens the Settings app via the local router.
- [ ] `Go back` issues an Android back press.
- [ ] An ambiguous request such as `show me something useful` does not crash
      and is handled by the local-model path with deterministic fallback.
- [ ] Attempting to type a password through `type_text` is blocked and the
      block is visible in the agent event log.
- [ ] No outbound network requests appear in `adb shell dumpsys netstats`
      attributable to `dev.touchpilot.app` while the flows run.

## Evidence

For each pass, capture:

- TouchPilot commit hash,
- date and runtime mode (Local Router or Local Model),
- `./gradlew test` output for the offline suite,
- for the live run: device or emulator name, Android version, and a copy of
  the agent event log exported through `Export Debug Trace`.

## Known Post-Milestone Follow-Up Areas

- Replace the deterministic fallback in the ambiguous flow with a bundled
  LiteRT model asset once Milestone 8 lands one.
- Add an instrumentation test that drives `AgentRunner` end-to-end against a
  fake `AndroidToolExecutor`; current coverage is at the component level
  because `AndroidToolExecutor` depends on `android.content.Context`.
- Extend the policy-block path to cover send-message and purchase workflows
  once Milestone 7 reworks the policy engine.
