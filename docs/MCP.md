# MCP

**Contract status:** Experimental — not frozen for 1.0. The MCP tool-call
contract (`McpModels.kt`) and the local extension plugin API manifest
(`PluginApiManifest.kt`) may change shape without a version bump until MCP is
merged into the main agent loop. See
[CONTRACTS.md](CONTRACTS.md#experimental-contracts).

Phase 4 adds a small MCP client to the Android app.

## Local Extension Boundary

Local extension tools are the on-device side of the MCP boundary. They are
registered into TouchPilot storage, but they are not Android tools and they do
not inherit Android tool permissions.

Each local extension must be granted explicitly in Settings > MCP before it can
list or call tools. The grant is keyed to the extension identity, and any
feature flags declared in the manifest are reviewed separately from the Android
policy system.

The client uses HTTP JSON-RPC and implements the core lifecycle needed to
interoperate with tool servers:

- initialize a session,
- list tools,
- call a selected tool with JSON arguments,
- preserve `Mcp-Session-Id` when a server returns one.

MCP tools are not yet merged into the main agent loop. The Android control layer
remains the source of truth for phone actions, approvals, and audit logs. This
keeps the first MCP integration low risk while establishing protocol plumbing
for later interoperability.

Running an MCP server from the phone is deferred. Before TouchPilot exposes
Android tools over MCP, it needs an explicit server permission model,
foreground-service lifecycle, local-network binding controls, and request
authentication.

## Local Extension Plugin API Manifest

Local extension tools must declare a versioned plugin API manifest when they are
registered. TouchPilot validates the manifest locally at load time and rejects
incompatible extensions with actionable guidance instead of failing later during
tool calls.

Bundled runtime reference:

- `app/src/main/assets/extensions/plugin-api-manifest.json`

Extension manifests are stored with each registered tool and must include:

```json
{
  "api_version": "1.0.0",
  "name": "weather",
  "description": "Local weather tools",
  "endpoint": "http://localhost:8080",
  "feature_flags": {
    "network_access": true
  }
}
```

Manifest fields:

| Field | Meaning |
| --- | --- |
| `api_version` | Semver plugin API version the extension targets. Must match the supported major version and must not be newer than the runtime's supported version. |
| `name` | Extension tool name shown in Settings. |
| `description` | Human-readable summary for review UI. |
| `endpoint` | Local MCP HTTP JSON-RPC endpoint for the extension. |
| `feature_flags` | Explicit capability opt-ins. Omitted flags are disabled. Unknown flags are rejected. |

Supported feature flags:

- `network_access`
- `privileged_host` (privileged; must be explicitly set to `true` to opt in)
- `file_system` (privileged; must be explicitly set to `true` to opt in)

`PluginApiManifest.parse` reads extension manifests and
`PluginApiManifest.validationErrors` / `compatibilityErrors` validate them before
registration or load. `LocalExtensionToolStore.load` separates valid tools from
invalid manifests so Settings can show recommended actions.

References:

- https://modelcontextprotocol.io/docs/learn/architecture
- https://modelcontextprotocol.io/specification/2025-11-25/server/tools
