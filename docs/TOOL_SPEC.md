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
- `long_press`: long-press a semantic target by visible text, stable `node_id`,
  `view_id`, or bounds — for context menus, selection mode, and long-click
  handlers.
- `type_text`: type text into the focused field or selected target.
- `scroll`: scroll the current view.
- `press_back`: send Android back.
- `press_home`: return to launcher.
- `wait_for_ui`: wait for a screen change or expected text.

The app implements `observe_screen`, `open_app`, `tap`, `long_press`,
`type_text`, `scroll`, `press_back`, `press_home`, and `wait_for_ui` from the
Android Tools screen and the agent command-provider loop.

`long_press` resolves its target through the shared target resolver (the same
selector/scoring path as `type_text` and `scroll`), then dispatches a bounded
long-press gesture (a single-point stroke held past the Android long-press
timeout) at the resolved node. It fails explicitly when the target is missing
or ambiguous rather than pressing a best guess.

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
