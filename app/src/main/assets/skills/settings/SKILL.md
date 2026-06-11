---
id: settings
title: Settings
description: Navigate and inspect Android Settings screens safely.
risk: medium
aliases:
  - settings
  - android settings
  - wi-fi settings
  - wifi settings
  - bluetooth settings
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
  - The agent stops after the requested state is visible.
examples:
  - open Wi-Fi settings
  - show Bluetooth settings
  - go to notification settings
---

# Settings

Use Android settings screens carefully. Prefer read-only observation unless the
user explicitly asks to change a setting.

Prefer direct settings panels when available. Observe the screen before tapping
ambiguous targets, and stop when the requested settings screen is visible.
