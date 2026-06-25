---
id: share
title: Share
description: Open the system share sheet so the user can pick where to send the current content.
risk: medium
aliases:
  - share
  - share sheet
  - send to
  - share with
  - share page
allowed_tools:
  - observe_screen_context
  - open_app
  - tap
  - long_press
  - scroll
  - wait_for_idle
  - wait_for_app
success_criteria:
  - The system share sheet is visible and the user can pick a target app.
  - No share target, recipient, or send button is tapped without explicit user approval.
  - The agent stops once the share sheet is open and waits for the user to choose.
examples:
  - share this page
  - open the share sheet
  - share the current link
---

# Share

Open the Android system share sheet so the user can choose where to send the
currently visible content. TouchPilot never picks a target app or recipient
on the user's behalf.

Trigger the share affordance (for example, the share icon in an app bar, or
Share from an overflow menu or long-press menu), then stop as soon as the
share sheet is visible. Wait for the user to choose a target before doing
anything else.