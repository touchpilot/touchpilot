---
id: apps
title: App Launcher
description: Open and switch between installed apps by name, then confirm the right app is in front.
risk: low
aliases:
  - app launcher
  - open an app
  - launch an app
  - switch apps
  - switch to app
allowed_tools:
  - observe_screen_context
  - open_app
  - get_foreground_app
  - wait_for_app
  - wait_for_idle
  - press_home
examples:
  - open the calculator app
  - launch maps
  - switch to gmail
success_criteria:
  - The requested app is the foreground app.
  - The agent stops once the app is open and ready.
---

# App Launcher

Open or switch to an installed app by its launcher label or package name.

Prefer `open_app` with the app's visible name. After launching, confirm the app
is in front with `get_foreground_app` or `wait_for_app`, and stop once the
requested app is ready. Do not sign in, change settings, or take in-app actions
unless the user asks for that separately.
