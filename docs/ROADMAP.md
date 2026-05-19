# Roadmap

## Project Direction

TouchPilot is a local-first Android AI agent runtime. Cloud and
OpenAI-compatible providers are useful development fallbacks, but the product
direction is local inference, local skills, local logs, local policy, and
Android-native control.

## Milestone 1: Local-First Agent Foundation

Milestone 1 includes Phase 1 through Phase 11. The target is a validated
Android agent runtime that can run locally first, control Android through safe
tools, expose maintainable project workflow, and provide a real product UI.

Status: complete for the local-first foundation scope. Remaining work below is
tracked as post-Milestone 1 follow-up, not as a blocker for the foundation.

## Phase 0: Android Control Spike

- [x] Create native Android app shell.
- [x] Add AccessibilityService permission flow.
- [x] Serialize current UI tree.
- [x] Execute tap, type, back, and home from a debug screen.
- [x] Add scroll action.
- [x] Add local tool execution log.
- [x] Add app launching by package or label.
- [x] Add wait-for-text UI synchronization.

## Phase 1: Agent MVP

- [x] Add basic agent task UI.
- [x] Add OpenAI-compatible provider config.
- [x] Add structured JSON command loop.
- [x] Route model-selected tools through the local tool executor.
- [x] Add tool-call timeline and local logs.
- [x] Add manual approval for medium/high risk tools.
- [x] Add Keystore-backed API key storage.
- [x] Add basic tool argument validation.

## Phase 2: Reliability

- [x] Stable UI selectors.
- [x] Retry and wait policies.
- [x] Better error recovery.
- [x] Task verification after actions.
- [x] Exportable debug traces.

## Phase 3: Skills

- [x] Add Markdown skill files.
- [x] Load skills into prompt context.
- [x] Add tool allowlists per skill.
- [x] Provide starter skills for browser, settings, and messages.

## Phase 4: MCP

- [x] Add MCP client support.
- [ ] Post-Milestone 1: optionally expose Android tools as an MCP server.
- [x] Provide examples for desktop agents calling TouchPilot.

## Phase 5: Local Inference

- [x] Evaluate ExecuTorch, llama.cpp, and LiteRT.
- [x] Start with local routing for simple tool calls.
- [x] Keep cloud/provider fallback available for complex tasks.
- [x] Integrate a real on-device LiteRT command-routing runtime.

## Phase 6: Live Validation

- [x] Add live emulator/device test checklist.
- [x] Validate AccessibilityService connection.
- [x] Validate observe, tap, type, scroll, open app, back, and home.
- [x] Validate approval prompt behavior.
- [x] Validate skill allowlist behavior.
- [x] Validate local router mode.
- [x] Validate MCP client UI.
- [x] Validate debug trace export.
- [x] Fix bugs found during live testing.

See [Live Testing](LIVE_TESTING.md) for the Phase 6 checklist, commands,
and current live-test results.

## Phase 7: Repo Quality and Workflow

- [x] Add GitHub Actions CI for debug and release builds.
- [x] Add Android lint checking.
- [x] Add unit-test task to CI.
- [x] Add issue templates.
- [x] Add pull request template.
- [x] Define issue and PR label taxonomy.
- [x] Define milestone naming convention.

Milestones use GitHub milestones for phase and milestone tracking. Labels stay
focused on type, area, and status.

## Phase 8: Local-First Architecture Cleanup

- [x] Make local router the default agent mode.
- [x] Move cloud/OpenAI-compatible provider into experimental fallback flow.
- [x] Rename provider/cloud-centric wording where appropriate.
- [x] Document the local-first runtime architecture.
- [x] Define the boundary for local command router, small model router, and future local LLM.

## Phase 9: Product UI Redesign

- [x] Replace the single debug screen with app sections.
- [x] Add primary Agent screen.
- [x] Add Skills screen.
- [x] Add Local Runtime screen.
- [x] Add Android Tools debug screen.
- [x] Add MCP screen.
- [x] Add Logs and Debug Trace screen.
- [x] Add Settings screen.
- [x] Make local-first mode prominent.
- [x] Keep cloud/API fallback secondary.

## Phase 10: Safety and Policy

- [x] Create a policy layer separate from UI and tool execution.
- [x] Block sensitive workflows by default.
- [x] Add app-specific restrictions for sensitive workflow classes.
- [x] Add sensitive text redaction in logs and traces.
- [x] Add clearer approval reasons.
- [x] Add risk-specific approval UI.
- [x] Define MCP trust boundaries.

## Phase 11: Real Local Model Runtime

- [x] Integrate LiteRT command-routing model path.
- [x] Add model asset loading.
- [x] Add local model status UI.
- [x] Emit local inference results through the existing JSON command loop.
- [x] Keep deterministic local router as fallback.
- [x] Keep cloud provider optional and experimental.
- [x] Document future ExecuTorch and llama.cpp experiments.

The bundled `tiny-router-1` model is intentionally small. It proves the real
LiteRT runtime path, asset loading, status reporting, and JSON command contract.
A trained local model can replace the asset behind the same boundary later.

## Post-Milestone 1 Follow-Up

- Train or select a stronger command-routing model with structured argument
  extraction.
- Add an Android-tools MCP server only after defining server permissions,
  authentication, and foreground-service behavior.
- Add a prompt file or prompt-builder layer for richer local assistant behavior.
- Add deeper live-test automation for MCP and approval flows.
