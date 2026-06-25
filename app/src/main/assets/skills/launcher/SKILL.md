---
id: launcher
title: Launcher
description: Return to the home screen or open an installed app from the launcher / app drawer.
risk: low
aliases:
  - launcher
  - home screen
  - open app
  - launch app
  - app drawer
  - go home
  - home
allowed_tools:
  - observe_screen_context
  - open_app
  - get_foreground_app
  - find_element
  - tap
  - scroll
  - swipe
  - press_home
  - wait_for_idle
  - wait_for_app
success_criteria:
  - The requested app is foreground, or the home screen is visible when the user asked to go home.
  - The agent stops after the requested app is open and the foreground app matches.
examples:
  - go home
  - open the app drawer
  - launch Spotify
  - open Chrome
---

# Launcher

Return to the home screen or open an installed application. Prefer the
`open_app` tool when the target package or app label is known; fall back to
navigating the home screen, app drawer, or search field for ambiguous names.

Always observe the screen before tapping launcher icons. Confirm the
foreground app matches the user's request before declaring success.