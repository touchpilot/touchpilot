---
id: browser
title: Browser
description: Open web pages and search results, then inspect the visible page content.
risk: low
aliases:
  - browser
  - chrome
  - web search
  - search the web
allowed_tools:
  - observe_screen_context
  - open_app
  - tap
  - long_press
  - type_text
  - scroll
  - swipe
  - press_back
  - wait_for_idle
  - wait_for_app
success_criteria:
  - The requested page or search results are visible.
  - The agent stops after the requested content is on screen.
examples:
  - open google.com
  - search for touchpilot android
  - show my open browser tabs
---

# Browser

Use browser-related Android tools to open pages, search the web, and inspect
visible results.

Observe the screen before tapping ambiguous links or buttons. Prefer stable
targets from screen context and stop once the requested page or results are
visible.
