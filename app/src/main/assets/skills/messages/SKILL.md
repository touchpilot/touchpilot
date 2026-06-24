---
id: messages
title: Messages
description: Draft SMS or chat replies and require explicit approval before sending.
risk: high
aliases:
  - messages
  - sms
  - text message
  - send a text
  - draft a reply
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
  - A draft message is visible and ready for user review.
  - Nothing is sent without explicit user approval.
examples:
  - draft a text to Alex
  - open messages and compose a reply
  - prepare an SMS but do not send it
---

# Messages

Messaging actions are high risk. Draft messages first and ask for user approval
before sending anything.

Never tap send, share, or confirm buttons unless the user explicitly approves
the outgoing message content.
