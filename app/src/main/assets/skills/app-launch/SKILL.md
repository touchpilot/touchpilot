---
id: app-launch
title: App Launch
description: Open installed Android apps and verify the requested app is foreground.
risk: medium
aliases:
  - app launch
  - open app
  - launch app
  - start app
  - switch to app
allowed_tools:
  - open_app
  - get_foreground_app
  - wait_for_app
  - press_back
success_criteria:
  - The requested app is foreground.
  - The foreground package or launcher label matches the user's request when available.
  - The agent stops after the requested app is open.
examples:
  - open Calculator
  - launch Chrome
  - switch to Settings
---

# App Launch

Open installed apps by package name or visible launcher label. Prefer
`wait_for_app` or `get_foreground_app` to verify that the requested app is
foreground after launch.

Do not interact with app content after launch unless the user asks for a
follow-up task.

Allowed initial tools:

- `open_app`
- `get_foreground_app`
- `wait_for_app`
- `press_back`
