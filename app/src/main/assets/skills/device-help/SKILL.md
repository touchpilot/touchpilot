---
id: device-help
title: Device Help
description: Navigate current Android screens to help users find visible controls or safe help paths.
risk: medium
aliases:
  - device help
  - android help
  - help me navigate
  - find controls
  - screen navigation
allowed_tools:
  - observe_screen_context
  - tap
  - long_press
  - scroll
  - swipe
  - press_back
  - press_home
  - wait_for_idle
success_criteria:
  - The requested visible control, help screen, or navigation target is found.
  - The agent explains when the requested target is not visible.
  - The agent stops before account, payment, deletion, permission, or security-sensitive changes.
examples:
  - help me find the privacy option
  - go back to the previous screen
  - scroll until the help section is visible
---

# Device Help

Use this skill for safe screen navigation and help-finding tasks inside the
current Android app or system screen. Observe before tapping when labels are
ambiguous, and prefer scroll or back navigation over risky state changes.

Do not approve purchases, delete data, change account state, modify security
settings, or enter sensitive information.

Allowed initial tools:

- `observe_screen_context`
- `tap`
- `long_press`
- `scroll`
- `swipe`
- `press_back`
- `press_home`
- `wait_for_idle`
