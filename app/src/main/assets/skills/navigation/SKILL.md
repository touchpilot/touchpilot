---
id: navigation
title: Device Navigation
description: Move around the device with home, back, and recent apps, and help the user find where they are.
risk: low
aliases:
  - device navigation
  - navigate the device
  - recent apps
  - recents
  - app switcher
allowed_tools:
  - observe_screen_context
  - get_foreground_app
  - press_back
  - press_home
  - wait_for_idle
examples:
  - show my recent apps
  - take me to the home screen
  - go to the previous screen
success_criteria:
  - The device is at the requested place (home, the previous screen, or recents).
  - The agent stops after navigating, without opening unrelated screens.
---

# Device Navigation

Help the user move around the device using the always-available navigation
controls: `press_home` to reach the launcher, `press_back` to step back, and
`observe_screen_context` / `get_foreground_app` to confirm where you landed.

These actions are reversible and low risk. Observe before and after navigating
so you can describe the resulting screen, and stop once the requested place is
visible. Do not open apps or change settings as part of plain navigation.
