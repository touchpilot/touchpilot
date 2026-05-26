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
- `swipe`: swipe a gesture surface (pager, carousel, drawer, map) by direction
  or between explicit coordinates — distinct from `scroll`, which drives a
  scrollable container's accessibility scroll action.
- `press_back`: send Android back.
- `press_home`: return to launcher.
- `wait_for_ui`: wait for a screen change or expected text.

The app implements `observe_screen`, `open_app`, `tap`, `type_text`, `scroll`,
`swipe`, `press_back`, `press_home`, and `wait_for_ui` from the Android Tools
screen and the agent command-provider loop.

`swipe` has two input modes. In **direction mode** (the primary path) the caller
passes `direction` (`left`, `right`, `up`, or `down`, naming the direction the
finger travels); the gesture is planned within an optional container target
(same selector/scoring path as `scroll`) or the active window when no container
is given. In **coordinate mode** the caller passes explicit `start_x`,
`start_y`, `end_x`, `end_y` (plus an optional `duration_ms`). Unlike `scroll`,
`swipe` imposes no scrollable-role constraint on its container, dispatches a raw
drag gesture rather than an accessibility scroll action, and fails explicitly on
an invalid direction, an incomplete/out-of-range coordinate set, or a missing or
ambiguous container.

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
