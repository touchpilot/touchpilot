---
id: media-volume
title: Volume
description: Open Android sound settings and adjust media, ringtone, or notification volume sliders.
risk: low
aliases:
  - volume
  - sound
  - media volume
  - ringtone
  - notification volume
  - volume up
  - volume down
  - mute
allowed_tools:
  - observe_screen_context
  - open_settings_panel
  - open_app
  - tap
  - scroll
  - wait_for_idle
  - wait_for_app
success_criteria:
  - The system Sound & vibration (or equivalent) settings screen is visible.
  - The target volume slider is positioned at the requested level, or the user has confirmed any final manual adjustment.
  - The agent stops once the requested volume level is visible on screen.
examples:
  - open sound settings
  - set media volume to 80 percent
  - turn the ringer down
---

# Volume

Open the system sound settings and adjust the media, ringtone, or notification
volume sliders. TouchPilot locates the right volume control from the visible
screen context and drags the corresponding slider.

Always observe the screen before tapping a slider to confirm which stream is
being changed. Stop as soon as the requested level is visible. If the slider
value cannot be set precisely, report the current value to the user instead of
guessing.