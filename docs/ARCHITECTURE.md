# Architecture

TouchPilot is organized around a small agent runtime and a typed Android tool
layer. The product direction is local-first: the default command path runs on
device and cloud providers are optional experimental fallbacks.

```text
User
  -> Chat UI
  -> Agent Runtime
  -> Agent Command Provider
      -> Local Router (default)
      -> Local LiteRT Routing Model
      -> Future Local LLM Runtime
      -> Experimental Cloud Fallback
  -> Tool Router
  -> Android Tool Layer
  -> Accessibility / Intents / Storage / Notifications

MCP Client
  -> HTTP JSON-RPC MCP Server
  -> External tools
```

## Core Modules

- `app`: Android UI, navigation, settings, permissions.
- `agent`: session loop, local routing, fallback provider clients, prompt
  building, conversational gating, and retries.
- `tools`: tool specifications, routing, validation, execution results.
- `androidcontrol`: AccessibilityService integration and action execution.
- `memory`: local sessions, tool logs, skills, and audit storage.
- `security`: approvals, policy checks, risk classification, secret storage.
- `mcp`: HTTP JSON-RPC client for external MCP tool servers.
- `localinference`: LiteRT command-router runtime and local-model fallback.

## Local-First Execution Loop

1. User sends a request.
2. Agent runtime builds context from session, skills, and current policy.
3. The selected command provider returns a message or a structured tool call.
4. Tool router validates the requested tool and arguments.
5. Active skill allowlist approves or denies the requested tool.
6. Security policy approves, denies, or asks the user.
7. Android tool layer executes the action.
8. Result is logged and fed back to the agent.

## Runtime Boundaries

TouchPilot separates command production from tool execution:

- Deterministic local router: the current default. It maps simple commands such
  as observe, back, home, scroll, open app, and tap text to structured tool
  calls without network access.
- Small local routing model: the current LiteRT path. It emits route logits for
  simple commands, keeps deterministic Kotlin argument extraction for now, and
  still emits the same structured JSON command format.
- Local LLM runtime: future on-device reasoning path for richer multi-step
  tasks. It should use the same tool validation, approval, skill allowlist, and
  logging pipeline as the router.
- Experimental cloud fallback: optional development and complex-task fallback.
  It is secondary to local routing and should not be required for basic Android
  control.

All command providers share the same policy boundary. A provider can suggest a
tool call, but only the tool router, skill allowlist, and safety policy decide
whether it can execute.
