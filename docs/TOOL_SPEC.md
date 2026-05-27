# Tool Specification

Tools are the only way an agent may affect the Android device.

## Tool Shape

```json
{
  "name": "tap",
  "description": "Tap a visible UI target.",
  "risk": "medium",
  "args": {
    "target": "string",
    "strategy": "text|node_id|bounds"
  }
}
```

## Initial Tools

- `observe_screen`: serialize the current accessibility tree (raw debug dump).
- `observe_screen_context`: serialize the current screen as a normalized
  `ScreenContext` with sensitive text redacted (see
  [Structured vs. raw observation](#structured-vs-raw-observation)).
- `open_app`: launch an installed app by package name or label.
- `tap`: tap a semantic target by visible text, stable `node_id`, or bounds.
- `type_text`: type text into the focused field or selected target.
- `scroll`: scroll the current view.
- `press_back`: send Android back.
- `press_home`: return to launcher.
- `wait_for_ui`: wait for a screen change or expected text.

The app implements `observe_screen`, `observe_screen_context`, `open_app`,
`tap`, `type_text`, `scroll`, `press_back`, `press_home`, and `wait_for_ui`
from the Android Tools screen and the agent command-provider loop.

## Structured vs. raw observation

Two observation tools exist:

- `observe_screen` returns the raw accessibility tree as a flat string. It is
  retained for debugging and backward compatibility.
- `observe_screen_context` returns a normalized `ScreenContext` serialized as
  stable JSON: app/package/window metadata, visible nodes with semantic roles
  and bounds, and per-node action flags (`clickable`, `isInputField`,
  `scrollable`, etc.).

**Prefer `observe_screen_context` for agent decision-making.** Structured
context lets the agent reason over roles, bounds, and action flags before
picking a tool, and it is more reliable than parsing a raw dump. Reach for
`observe_screen` only when debugging the raw tree.

`observe_screen_context` output is **redacted by default**: sensitive visible
text (passwords, tokens, OTPs, emails, card numbers) is replaced with
`[REDACTED]`, and any redacted node is flagged. The top-level
`containsSensitiveContent` flag indicates whether the screen held sensitive
text. Both observation tools are LOW risk and require no approval.

A serialized `ScreenContext` looks like:

```json
{
  "appLabel": "Gmail",
  "packageName": "com.google.android.gm",
  "windowTitle": "Inbox",
  "nodes": [
    {
      "nodeId": "0.0",
      "role": "BUTTON",
      "text": { "raw": "Compose", "displaySafe": "Compose", "isSensitive": false },
      "bounds": { "left": 0, "top": 0, "right": 100, "bottom": 48 },
      "clickable": true,
      "isInputField": false,
      "scrollable": false
    }
  ],
  "containsSensitiveContent": false
}
```

All command providers return one JSON command at a time:

```json
{
  "tool": "observe_screen",
  "args": {}
}
```

Screen snapshots include `node_id` and `bounds` fields for each serialized
accessibility node. Prefer `node_id` for exact taps after `observe_screen`.
Bounds use `left,top,right,bottom` format and are intended as a fallback when a
semantic selector is not reliable.

Final answers use:

```json
{
  "final": "Done."
}
```

## Result Shape

```json
{
  "ok": true,
  "message": "Tapped target",
  "data": {}
}
```

Failures must be explicit:

```json
{
  "ok": false,
  "message": "Target not found",
  "data": {
    "target": "Send"
  }
}
```
