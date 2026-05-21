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

Every test drives the real `DefaultLocalReasoningCore` and asserts the event
contract it actually emits, rather than synthesising events. For flows that
reach `AgentRunner`, the tests inject a fake `AgentRunInvocation` that returns
events built with the same `AgentEvent` factory methods the production runner
uses (see `AgentRunner.kt`).

- [ ] `./gradlew :app:testDebugUnitTest --tests "dev.touchpilot.app.agent.OfflineMilestone2Test"` passes.
- [ ] `Hello` and `help` each produce exactly `user_message` then
      `final_answer` through the conversational short-circuit in
      `DefaultLocalReasoningCore`, with no tool events and no invocation.
- [ ] `Open Settings` goes through `IntentGate` and reaches the runner with
      `exactCommand = open_app(target=settings)` and `providerMode = LOCAL_ROUTER`;
      the resulting event sequence is `user_message -> tool_requested ->
      approval_required -> tool_running -> tool_succeeded -> final_answer`.
- [ ] `Go back` follows the same shape, producing an `approval_required` event
      for `press_back` and a `tool_succeeded` for the same tool.
- [ ] An unsafe phrase such as `change my password please` is short-circuited
      by `IntentGate.UnsafeRequest` and emits `user_message -> policy_blocked
      -> final_answer` without invoking the runner at all.
- [ ] An ambiguous reference such as `do the thing` is classified as
      `IntentDecision.ClarificationNeeded` and emits `user_message ->
      assistant_message` (clarification prompt) without invoking the runner.
- [ ] A non-deterministic request such as `show me something useful` is
      classified as `IntentDecision.LocalModelNeeded`; the core hands off to
      invocation preserving the session's `providerMode` and never sets
      `exactCommand`. No cloud provider is required.
- [ ] `type_text` with `text=my password is hunter2` is rejected by
      `DefaultActionPolicy` as a second line of defence and the agent emits a
      `policy_blocked` event with the matching wire shape.

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

## Live Validation Run

Run on commit `ab6092c` using `TouchPilot_API_35` (Android 15, x86_64 google_apis) on Windows. Airplane mode enabled throughout (`settings get global airplane_mode_on` returned `1`).

Validated live:

- `Hello` produced `Hello, I am TouchPilot, how can I help you?` via `ConversationalGate`, no tool call.
- `help` produced the conversational help reply, no tool call.
- `Open Settings` emitted an `open_app` approval card with `target: settings`, MEDIUM risk. Approving it launched `com.android.settings/.Settings` (confirmed via `dumpsys activity activities | grep ResumedActivity`).
- `Go back` emitted a `press_back` approval card. Approving it executed `pressBack` and navigated TouchPilot to the background.
- `show me something useful` ran two steps locally: `observe_screen` then the deterministic final answer `Local router completed its safe routing pass. Try a more specific request, a skill, or local model mode for ambiguous tasks.` — no cloud call.
- `type_text` from the Tools panel with `my password is hunter2` was blocked by `DefaultActionPolicy` before execution: `type_text({text=my password is hunter2}) -> false: TouchPilot blocked this request because password workflows are blocked.` (logged in `ToolExecutionLog`.)
- `dumpsys netstats detail` showed no rx or tx bytes attributed to `dev.touchpilot.app` (uid 10209) for the duration of the run — the app uid was absent from `mAppUidStatsMap`.

## Known Post-Milestone Follow-Up Areas

- Replace the deterministic fallback in the ambiguous flow with a bundled
  LiteRT model asset once Milestone 8 lands one.
- Add an instrumentation test that drives `AgentRunner` end-to-end against a
  fake `AndroidToolExecutor`; current coverage is at the component level
  because `AndroidToolExecutor` depends on `android.content.Context`.
- Extend the policy-block path to cover send-message and purchase workflows
  once Milestone 7 reworks the policy engine.
