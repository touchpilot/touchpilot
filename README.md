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
- Run the first local LiteRT command-routing model, with ExecuTorch and
  llama.cpp documented as future runtime paths.
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

## Current Status

TouchPilot has completed the local Android agent foundation, hybrid local AI
core, normalized screen context, reliable Android control baseline, and bounded
local multi-step loop. The current codebase includes a Skills v2 registry,
active skill selection, skill-aware prompts, skill allowlists, and risk-aware
approval context. The project is now in the 1.0.0 release path: the skills pack
and release-signing work are being finalized, the version is moving to 1.0.0,
and GitHub Releases is the first distribution channel.

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
debug key so it can be installed on emulators such as LDPlayer. When the
`TOUCHPILOT_RELEASE_*` signing secrets are present, the release build uses the
real release keystore instead.

The current agent MVP defaults to the local router for simple Android actions
and includes a LiteRT local model mode for command routing. An experimental
cloud fallback can be configured with an OpenAI-compatible chat completions URL,
model name, and API key. Fallback URL and model are stored in app preferences.
API keys are encrypted with an Android Keystore-backed key before being stored.

When the model selects a medium- or high-risk Android tool, TouchPilot asks the
user for approval in the chat before executing it. Low-risk observation and wait
tools can run without a prompt.

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
app/        Android app source, runtime code, assets, and tests
docs/       Architecture, roadmap, validation, and tool documentation
examples/   MCP/provider examples and integration notes
.github/    CI, issue templates, labels, and PR automation
gradle/     Gradle wrapper files
```

TouchPilot is currently a single Android application module. Internal runtime
areas such as `agent`, `tools`, `androidcontrol`, `memory`, and `security` live
as packages under `app/src/main/java/dev/touchpilot/app/`. Packaged Markdown
skills live under `app/src/main/assets/skills/`.

See [Code Structure](docs/CODE_STRUCTURE.md) for the current package layout and
future module-split direction.

## Community
https://discord.gg/TvXwsNbx
