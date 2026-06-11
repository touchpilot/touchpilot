---
id: settings
title: Settings
description: Navigate and inspect Android Settings screens safely.
risk: medium
aliases:
  - settings
  - android settings
  - wi-fi settings
  - bluetooth settings
  - notification settings
  - app info settings
allowed_tools:
  - observe_screen_context
  - open_app
  - open_settings_panel
  - tap
  - long_press
  - scroll
  - press_back
  - wait_for_idle
  - wait_for_app
success_criteria:
  - The requested Settings screen is foreground.
  - The requested setting or panel is visible.
  - The agent stops before changing a setting unless the user explicitly asked for that change.
examples:
  - open Wi-Fi settings
  - show Bluetooth settings
  - go to this app's notification settings
---

# Settings

Use Android Settings screens carefully. Prefer direct settings panels when a
supported panel exists, then observe the screen before tapping ambiguous
targets. Stop when the requested settings page or state is visible.

Do not toggle permissions, network, account, security, or privacy settings
unless the user explicitly requested the exact change.

Allowed initial tools:

- `observe_screen_context`
- `open_app`
- `open_settings_panel`
- `tap`
- `long_press`
- `scroll`
- `press_back`
- `wait_for_idle`
- `wait_for_app`
