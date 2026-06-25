---
id: clipboard
title: Clipboard
description: Copy text to or paste from the system clipboard by driving the clipboard UI of another app.
risk: low
aliases:
  - clipboard
  - copy
  - paste
  - copy to clipboard
  - paste from clipboard
allowed_tools:
  - observe_screen_context
  - open_app
  - find_element
  - focus_input
  - type_text
  - long_press
  - tap
  - wait_for_idle
  - wait_for_element
success_criteria:
  - For copy: the source text is selected and a Copy action has been confirmed in a context menu or selection toolbar.
  - For paste: the target input field shows the pasted text from the clipboard.
  - The agent stops once the clipboard operation is visible on screen.
examples:
  - copy the selected text
  - paste into the notes field
  - select all and copy
---

# Clipboard

Help the user copy text from one location and paste it into another. Because
TouchPilot does not expose a direct clipboard read or write tool, this skill
drives the standard Android selection and context menu UI instead.

For copy actions, long-press a selectable element to surface the selection
toolbar, choose Select all or a specific range, then tap Copy. For paste
actions, focus the target input field and tap the Paste affordance in the
keyboard or selection toolbar.

Observe the screen before and after each step to confirm the clipboard
operation succeeded, and stop once the copied or pasted content is visible.