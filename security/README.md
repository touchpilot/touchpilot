# Security

This module contains the first security primitives for the Agent MVP:

- manual approval hooks for medium- and high-risk model-selected tools,
- risk checks based on the tool catalog,
- Android Keystore-backed provider API key encryption.

The approval flow is intentionally synchronous from the agent loop's
perspective: the background agent thread waits while the Android UI presents an
approval dialog. Denied actions are not executed and are recorded in the local
tool timeline.
