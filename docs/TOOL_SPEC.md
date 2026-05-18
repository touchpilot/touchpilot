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

- `observe_screen`: serialize the current accessibility tree.
- `open_app`: launch an installed app by package name or label.
- `tap`: tap a semantic target by visible text, stable `node_id`, or bounds.
- `type_text`: type text into the focused field or selected target.
- `scroll`: scroll the current view.
- `press_back`: send Android back.
- `press_home`: return to launcher.
- `wait_for_ui`: wait for a screen change or expected text.

The first Android spike implements `observe_screen`, `open_app`, `tap`,
`type_text`, `scroll`, `press_back`, `press_home`, and `wait_for_ui` from the
debug screen and the first LLM agent loop.

The initial agent loop asks OpenAI-compatible models to return one JSON command
at a time:

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
