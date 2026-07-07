# User Guide (1.0)

TouchPilot is a local-first Android AI agent designed for safe, inspectable
phone control. This guide covers the default setup and the minimum workflow
needed to start a reliable session.

## Before first run

1. Install TouchPilot on the target device.
2. Open Android accessibility settings and enable TouchPilot Accessibility Service.
3. Open **Settings** in TouchPilot and verify runtime status in **Runtime**.
4. Send a simple task from chat (for example, "open settings").

## Core runtime workflow

- Use the **Chat** tab for natural-language prompts.
- Review tool calls in step cards and logs in **Logs**.
- Use **Settings → Runtime** to switch between local/legacy runtime modes.
- Use **Settings → Skills** to inspect allowed tool scope and skill state.
- If a tool is considered high risk, approval is required before execution.

## Troubleshooting sequence

1. If an action fails unexpectedly, open **Settings → Help** and check
   **Known limitations**.
2. Compare your device against the published matrix.
3. Re-run with a narrow prompt and verify accessibility permissions.
4. Open a bug report only if behavior is not already documented.

## What to expect from this beta

- Local-first tool execution is preferred by default.
- Sensitive actions are guarded by policy/approval checks.
- Validation and compatibility quality still improve during milestone work.

## References

- [Contracts](CONTRACTS.md)
- [Policy](POLICY.md)
- [Tool Spec](TOOL_SPEC.md)
- [Workflows](WORKFLOWS.md)
- [Skill Authoring Guide](SKILL_AUTHORING_GUIDE.md)
- [Known Limitations](KNOWN_LIMITATIONS.md)
