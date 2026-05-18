# TouchPilot

Local-first Android AI agent runtime for safe, observable phone control.

TouchPilot is inspired by OpenClaw and PicoClaw, but it is scoped around one
hard problem first: letting an AI agent operate an Android device through
explicit, permissioned, inspectable tools.

<img width="1536" height="1024" alt="touchpilot" src="https://github.com/user-attachments/assets/4099bf82-1504-4443-9b42-cc2d18e92b02" />

## Goals

- Run as a native Android app, not only as a desktop companion.
- Expose Android actions through typed tools with clear risk levels.
- Use AccessibilityService for semantic UI observation and control.
- Prefer local-first routing and inference.
- Keep OpenAI-compatible model providers available as an experimental fallback.
- Add local model inference through mobile runtimes such as LiteRT, ExecuTorch,
  or llama.cpp.
- Keep user approval and audit logs central to the runtime.

## Early Scope

The first milestone is a local Android app that can:

- show a chat interface,
- run simple actions through the default local router without an API key,
- optionally connect to an OpenAI-compatible model endpoint as an experimental
  fallback,
- observe the current screen through AccessibilityService,
- tap, type, scroll, open apps, and press back/home through approved tools,
- record every model decision and tool execution in local logs.

## Development

Build the Android debug APK:

```bash
./gradlew assembleDebug
```

Build an installable development release APK:

```bash
./gradlew assembleRelease
```

During early development, the release build is signed with the local Android
debug key so it can be installed on emulators such as LDPlayer. Replace this
with a real release signing key before publishing.

The current agent MVP defaults to the local router for simple Android actions.
An experimental cloud fallback can be configured with an OpenAI-compatible chat
completions URL, model name, and API key. Fallback URL and model are stored in
app preferences. API keys are encrypted with an Android Keystore-backed key
before being stored.

When the model selects a medium- or high-risk Android tool, TouchPilot asks the
user for approval before executing it. Low-risk observation and wait tools can
run without a prompt.

If building outside Android Studio, make sure either `ANDROID_HOME` is set or
`local.properties` contains the local Android SDK path:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Reference Projects

- [OpenClaw](https://github.com/openclaw/openclaw) for sessions, skills,
  channels, and gateway design.
- [PicoClaw](https://github.com/sipeed/picoclaw) for lightweight runtime
  discipline and small-device deployment mindset.
- [MobileClaw](https://github.com/MobileClaw/MobileClaw) for mobile GUI
  automation and chat-channel agent ideas.
- [ClawMobile](https://github.com/ClawMobile/ClawMobile) for smartphone-native
  agent architecture and semantic Android control.
- [ExecuTorch](https://github.com/pytorch/executorch) for future on-device
  model inference.

## Repository Layout

```text
app/              Android app source
agent/            Agent runtime, provider clients, planner loop
tools/            Tool specs and tool router
androidcontrol/   Accessibility and Android action execution
memory/           Local session, logs, and skill storage
security/         Approvals, policy, risk levels, secrets handling
skills/           Markdown skills for task-specific behavior
docs/             Architecture, security, and tool documentation
examples/         Provider and MCP integration examples
```

## Status

Phase 1 Agent MVP: Android control spike, OpenAI-compatible command loop,
manual approval for medium/high-risk tools, Keystore-backed API key storage,
basic tool argument validation, and Phase 2 reliability features for stable
selectors, action retries, post-action verification, debug trace export, and
Phase 3 Markdown skills with prompt loading and per-skill tool allowlists.
Phase 4 adds an in-app MCP HTTP JSON-RPC client for initializing external MCP
servers, listing tools, and calling tools with JSON arguments.
Phase 5 adds a local command-provider boundary, an offline conservative local
router for simple tool calls, and a runtime evaluation for ExecuTorch, LiteRT,
and llama.cpp before embedding a full local model runtime. Phase 6 completes
live emulator validation for the Android debug app, including accessibility
connection, core Android tools, local-router selection, MCP UI reachability,
debug trace export, and stable UI IDs for repeatable device checks. Phase 8
makes local router mode the default and moves cloud provider support into an
experimental fallback role.
