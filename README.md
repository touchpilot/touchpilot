# TouchPilot

Android-native AI agent runtime for safe, observable phone control.

TouchPilot is inspired by OpenClaw and PicoClaw, but it is scoped around one
hard problem first: letting an AI agent operate an Android device through
explicit, permissioned, inspectable tools.

<img width="1536" height="1024" alt="touchpilot" src="https://github.com/user-attachments/assets/4099bf82-1504-4443-9b42-cc2d18e92b02" />

## Goals

- Run as a native Android app, not only as a desktop companion.
- Expose Android actions through typed tools with clear risk levels.
- Use AccessibilityService for semantic UI observation and control.
- Support OpenAI-compatible model providers first.
- Add local model inference later through mobile runtimes such as ExecuTorch or
  llama.cpp.
- Keep user approval and audit logs central to the runtime.

## Early Scope

The first milestone is a local Android app that can:

- show a chat interface,
- connect to an OpenAI-compatible model endpoint,
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

The current agent MVP accepts an OpenAI-compatible chat completions URL, model
name, and API key inside the app. The API key is not persisted yet; proper
Keystore-backed secret storage is a separate security task.

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

Initial Android scaffold with an AccessibilityService debug spike.
