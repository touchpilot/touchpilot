---
id: browser
title: Browser
description: Open browser pages, search the web, and inspect visible results.
risk: medium
aliases:
  - browser
  - web browser
  - search the web
  - open website
  - web search
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
  - The requested browser app or page is foreground.
  - The requested search query or URL has been entered only when the target input is visible.
  - The agent reports visible results without entering sensitive data.
examples:
  - open the browser
  - search the web for local weather
  - open example.com in Chrome
---

# Browser

Use browser-related Android tools to open pages, search the web, and inspect
visible results. Prefer observing the screen before typing or tapping when the
browser state is unclear.

Never enter passwords, payment data, recovery codes, or other secrets into a
web page. Stop and ask the user if the browser requests account, payment, or
security information.

Allowed initial tools:

- `observe_screen_context`
- `open_app`
- `tap`
- `long_press`
- `type_text`
- `scroll`
- `swipe`
- `press_back`
- `wait_for_idle`
- `wait_for_app`
