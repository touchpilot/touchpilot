# Roadmap

## Phase 0: Android Control Spike

- [x] Create native Android app shell.
- [x] Add AccessibilityService permission flow.
- [x] Serialize current UI tree.
- [x] Execute tap, type, back, and home from a debug screen.
- [x] Add scroll action.
- [x] Add local tool execution log.
- [x] Add app launching by package or label.
- [x] Add wait-for-text UI synchronization.

## Phase 1: Agent MVP

- [x] Add basic agent task UI.
- [x] Add OpenAI-compatible provider config.
- [x] Add structured JSON command loop.
- [x] Route model-selected tools through the local tool executor.
- [x] Add tool-call timeline and local logs.
- [x] Add manual approval for medium/high risk tools.
- [x] Add Keystore-backed API key storage.
- [x] Add basic tool argument validation.

## Phase 2: Reliability

- [x] Stable UI selectors.
- [x] Retry and wait policies.
- [x] Better error recovery.
- [x] Task verification after actions.
- [x] Exportable debug traces.

## Phase 3: Skills

- Add Markdown skill files.
- Load skills into prompt context.
- Add tool allowlists per skill.
- Provide starter skills for browser, settings, and messages.

## Phase 4: MCP

- Add MCP client support.
- Optionally expose Android tools as an MCP server.
- Provide examples for desktop agents calling TouchPilot.

## Phase 5: Local Inference

- Evaluate ExecuTorch, llama.cpp, and LiteRT.
- Start with small local models for routing and simple tool calls.
- Keep cloud/provider fallback available for complex tasks.
