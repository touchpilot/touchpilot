---
id: device-help
title: Device Help
description: Navigate device help and support surfaces like app info and system settings.
risk: medium
aliases:
  - device help
  - help
  - phone help
  - support
  - system help
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
  - The requested help or settings surface is visible.
  - The agent stops before making destructive changes.
examples:
  - open device help
  - show app info
  - open accessibility settings
---

# Device Help

Use device help for navigation around system surfaces and support screens.
Prefer direct settings panels when they exist. Stop once the requested help
surface is visible, and avoid destructive changes unless the user explicitly
asks for them.
