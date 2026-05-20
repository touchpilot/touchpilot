# References

## Reasoning Core References

- OpenClaw: https://github.com/openclaw/openclaw
- PicoClaw: https://github.com/sipeed/picoclaw

Use these projects to study agent-loop structure, session handling,
prompt/context layering, skill loading, memory boundaries, tool policy, and
event logging. TouchPilot should adapt these patterns to a smaller 100% local
Android runtime.

## Android Control References

- MobileClaw: https://github.com/MobileClaw/MobileClaw
- ClawMobile: https://github.com/ClawMobile/ClawMobile

Use these projects to study observe-act loops, screenshot and UI-tree handling,
Android action tools, trace capture, workflow replay, and demonstration-to-skill
patterns.

## UI/Event UX Reference

- wende/mobileclaw: https://github.com/wende/mobileclaw

Use this project to study mobile chat UX for visible agent work: tool-call
cards, running/success/error states, reasoning state, diffs, traces, and event
streams.

## Local Runtime References

- LiteRT: https://ai.google.dev/edge/litert
- ExecuTorch: https://github.com/pytorch/executorch
- llama.cpp: https://github.com/ggml-org/llama.cpp

Use LiteRT first for compact local routing, classification, ranking, OCR-adjacent
models, and other small Android-friendly inference. Keep ExecuTorch and
llama.cpp as future local LLM/VLM runtime candidates.

## Notes

TouchPilot should borrow patterns, not copy identity. The product direction is a
100% local hybrid Android AI agent: local model reasoning where ambiguity exists,
deterministic routing where correctness matters, local skills and workflows for
repeatable knowledge, and policy/approvals for safety.
