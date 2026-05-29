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
- `wait_for_idle`: wait until the redacted screen context remains stable.
- `wait_for_app`: wait until a package name or launcher label is foreground.
- `focus_input`: focus a visible editable input field without typing.
- `clear_text`: clear the focused or resolved editable input field.
- `dismiss_keyboard`: hide the soft keyboard if it is visible.

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
`press_back`, `press_home`, `wait_for_ui`, `focus_input`, `clear_text`, and
`dismiss_keyboard` from the Android Tools screen and the agent command-provider
loop.
`press_back`, `press_home`, `wait_for_ui`, `wait_for_app`, `focus_input`,
`clear_text`, and `dismiss_keyboard` from the Android Tools screen and the
agent command-provider loop.
`press_back`, `press_home`, `wait_for_ui`, `wait_for_idle`, `wait_for_app`,
`focus_input`, `clear_text`, and `dismiss_keyboard` from the Android Tools
screen and the agent command-provider loop.

`dismiss_keyboard` is observation-gated: it inspects the accessibility window
list for a `TYPE_INPUT_METHOD` window first. If the keyboard is already hidden,
the tool is a no-op and reports `was_visible_before=false`. If it is visible,
the accessibility service flips `softKeyboardController.showMode` to
`SHOW_MODE_HIDDEN` (which routes through InputMethodManagerService and cannot
navigate the foreground app), waits briefly for the IME to settle, and then
restores the prior show mode so subsequent taps on an editable field bring the
keyboard back. The tool is single-attempt and never accepts or logs text input.

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

`wait_for_idle` accepts optional `stable_ms`, `timeout_ms`, and
`include_bounds` arguments. By default it waits until the redacted screen
context is stable for 500 ms, with a 5,000 ms timeout. Timeouts are bounded to
avoid stuck agent loops.

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
