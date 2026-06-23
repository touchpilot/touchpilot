# Vision

TouchPilot is an Android-first agent runtime.

Most agent systems are designed for desktops, servers, or chat channels first,
then extended to mobile later. TouchPilot starts from the phone: permissions,
screen state, accessibility metadata, foreground services, battery limits,
notifications, intents, and user approval.

## Product Thesis

Phones already hold the user's most common workflows. A useful mobile agent must
be able to understand the visible UI, act through ordinary app surfaces, and
explain what it is doing before it touches sensitive data.

TouchPilot should feel like a pilot, not a hidden automation daemon:

- visible when active,
- explicit about requested actions,
- narrow in permissions,
- reversible where possible,
- logged by default.

## Non-Goals For V1

- No broad unsupervised control of a user's main phone.
- No hidden background automation.
- No banking, purchasing, password, or account-recovery workflows.
- No dependency on a local LLM for the first usable version.
- No attempt to replicate every OpenClaw channel or plugin at launch.

## Long-Term Direction

TouchPilot should become a small, auditable mobile agent host:

- native Android control tools,
- local-first memory,
- MCP-compatible extensions,
- optional local inference,
- skill files that users can read and edit,
- clear safety boundaries for app actions.

## Final Goal

The post-1.0 destination is a stable Android agent host that stays useful even
without a local GPT-class model:

- native Android control with clear permissions and approvals,
- local-first memory, logs, and reusable traces,
- readable and editable skills,
- optional local reasoning and visual fallback where devices can support it,
- extension boundaries that are explicit, revocable, and auditable,
- a product that remains safe and understandable when it runs only on
  deterministic routing plus policy.
