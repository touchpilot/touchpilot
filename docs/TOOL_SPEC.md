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
- `open_settings_panel`: open a specific Android Settings panel via a native
  Settings intent (see [Settings Panels](#settings-panels)).
- `tap`: tap a semantic target by visible text, stable `node_id`, or bounds.
- `type_text`: type text into the focused field or selected target.
- `scroll`: scroll the current view.
- `press_back`: send Android back.
- `press_home`: return to launcher.
- `wait_for_ui`: wait for a screen change or expected text.

The app implements `observe_screen`, `open_app`, `open_settings_panel`, `tap`,
`type_text`, `scroll`, `press_back`, `press_home`, and `wait_for_ui` from the
Android Tools screen and the agent command-provider loop.

## Settings Panels

`open_settings_panel` takes a single `panel` argument and dispatches a native
Android `Settings` intent. It only navigates to a panel — it never toggles a
setting. The agent must use one of the allowlisted keys; any other value is
rejected before execution with an explicit error rather than guessed.

| `panel`           | Settings intent                              | Notes                                  |
| ----------------- | -------------------------------------------- | -------------------------------------- |
| `wifi`            | `ACTION_WIFI_SETTINGS`                        | Wi-Fi panel                            |
| `bluetooth`       | `ACTION_BLUETOOTH_SETTINGS`                   | Bluetooth panel                        |
| `accessibility`   | `ACTION_ACCESSIBILITY_SETTINGS`               | Accessibility services                 |
| `app_info`        | `ACTION_APPLICATION_DETAILS_SETTINGS`         | TouchPilot's own app-details page      |
| `notifications`   | `ACTION_APP_NOTIFICATION_SETTINGS`            | TouchPilot's notification settings     |
| `system_settings` | `ACTION_SETTINGS`                             | Top-level system settings              |

This is a medium-risk action: it requires approval and is recorded in the tool
execution log like any other device-affecting tool. An unsupported panel returns
`ok: false` with a message listing the supported panels.

### Coverage

- `AndroidToolCatalogTest` exercises the panel allowlist: every supported panel
  validates, and unsupported / missing / blank / unknown arguments are rejected
  with explicit errors. It also pins the allowlist to the issue #89 panel set.
- `DefaultActionPolicyTest.requiresApprovalForOpenSettingsPanel` confirms the
  tool is gated behind approval as a medium-risk action.
- Live intent dispatch (`AndroidToolExecutor.openSettingsPanel`) requires a real
  `Context`/device and is verified via manual emulator testing.

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
