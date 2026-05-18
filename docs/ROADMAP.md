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
- [ ] Optionally expose Android tools as an MCP server.
- [x] Provide examples for desktop agents calling TouchPilot.

## Phase 5: Local Inference

- [x] Evaluate ExecuTorch, llama.cpp, and LiteRT.
- [x] Start with local routing for simple tool calls.
- [x] Keep cloud/provider fallback available for complex tasks.
- [ ] Integrate a real on-device model runtime.

## Phase 6: Live Validation

- [x] Add live emulator/device test checklist.
- [x] Validate AccessibilityService connection.
- [x] Validate observe, tap, type, scroll, open app, back, and home.
- [x] Validate approval dialog behavior.
- [x] Validate skill allowlist behavior.
- [x] Validate local router mode.
- [x] Validate MCP client UI.
- [x] Validate debug trace export.
- [x] Fix bugs found during live testing.

See [Live Testing](LIVE_TESTING.md) for the Phase 6 checklist, commands,
and current live-test results.

## Phase 7: Repo Quality and Workflow

- [ ] Add GitHub Actions CI for debug and release builds.
- [ ] Add Android lint checking.
- [ ] Add unit-test task to CI.
- [ ] Add issue templates.
- [ ] Add pull request template.
- [ ] Define issue and PR label taxonomy.
- [ ] Define milestone naming convention.

Open discussion before implementation:

- exact label set,
- issue template types,
- PR checklist contents,
- whether CI starts as required or informational.

## Phase 8: Local-First Architecture Cleanup

- [x] Make local router the default agent mode.
- [x] Move cloud/OpenAI-compatible provider into experimental fallback flow.
- [x] Rename provider/cloud-centric wording where appropriate.
- [x] Document the local-first runtime architecture.
- [x] Define the boundary for local command router, small model router, and future local LLM.

## Phase 9: Product UI Redesign

- [ ] Replace the single debug screen with app sections.
- [ ] Add primary Agent screen.
- [ ] Add Skills screen.
- [ ] Add Local Runtime screen.
- [ ] Add Android Tools debug screen.
- [ ] Add MCP screen.
- [ ] Add Logs and Debug Trace screen.
- [ ] Add Settings screen.
- [ ] Make local-first mode prominent.
- [ ] Keep cloud/API fallback secondary.

## Phase 10: Safety and Policy

- [ ] Create a policy layer separate from UI and tool execution.
- [ ] Block sensitive workflows by default.
- [ ] Add app-specific restrictions.
- [ ] Add sensitive text redaction in logs and traces.
- [ ] Add clearer approval reasons.
- [ ] Add risk-specific approval UI.
- [ ] Define MCP trust boundaries.

## Phase 11: Real Local Model Runtime

- [ ] Integrate LiteRT command-routing model path.
- [ ] Add model asset loading.
- [ ] Add local model status UI.
- [ ] Emit local inference results through the existing JSON command loop.
- [ ] Keep deterministic local router as fallback.
- [ ] Keep cloud provider optional and experimental.
- [ ] Document future ExecuTorch and llama.cpp experiments.
