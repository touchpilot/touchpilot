---
id: app-launch
title: App Launch
description: Open installed apps by name or package and confirm the requested app is foreground.
risk: low
aliases:
  - app launch
  - launch app
  - open app
  - start app
allowed_tools:
  - observe_screen_context
  - open_app
  - wait_for_app
  - press_back
  - wait_for_idle
success_criteria:
  - The requested app is foreground.
  - The agent stops after the app is open.
examples:
  - open Chrome
  - launch Settings
  - open Gmail
---

# App Launch

Use the smallest direct action that launches the requested app. Prefer the
app's package name when it is already known, and confirm the foreground app
after launching.
