---
id: wifi
title: Wi-Fi
description: Open Wi-Fi settings, list visible networks, and guide the user to connect or disconnect.
risk: medium
aliases:
  - wifi
  - wi-fi
  - wireless
  - connect wifi
  - wifi settings
allowed_tools:
  - observe_screen_context
  - open_settings_panel
  - open_app
  - tap
  - scroll
  - wait_for_idle
  - wait_for_app
success_criteria:
  - The system Wi-Fi settings panel is visible.
  - The user has confirmed the desired network selection before any connect toggle is tapped.
  - The agent stops once the requested network state is visible or after asking the user to confirm.
examples:
  - open wifi settings
  - show available wifi networks
  - connect to my home wifi
---

# Wi-Fi

Use the Wi-Fi settings panel to inspect available networks and let the user
connect or disconnect. TouchPilot does not perform the actual network
authentication on behalf of the user.

Open the Wi-Fi panel first, observe the on-screen network list, and only tap
connect or disconnect toggles after the user has confirmed the target network.
Never enter passwords or credentials on behalf of the user.