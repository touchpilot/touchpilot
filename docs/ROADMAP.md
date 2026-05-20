# Roadmap

## Project Direction

TouchPilot is a 100% local Android AI agent. It should not depend on cloud LLMs,
hosted routing, or an external backend for its core product behavior.

The architecture is hybrid local AI:

- small local models handle ambiguity, ranking, summarization, and next-action
  selection
- deterministic routing handles exact commands and correctness-sensitive paths
- local skills encode reusable domain knowledge
- workflows encode repeatable Android tasks
- policy and approvals protect risky actions
- local memory and logs stay on device

```text
you ask                         Android responds
  │                                  ▲
  ▼                                  │
┌────────┐   ┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Chat   │ → │ Reasoning  │ → │ Hybrid Local │ → │ Android    │
│ UI     │   │ Core       │   │ Router       │   │ Tools      │
└────────┘   └─────┬──────┘   └──────┬───────┘   └─────┬──────┘
                   │                 │                 │
       skills ◄────┼────► memory     │                 ▼
       local files │      SQLite     │        Accessibility / Intents
                   │                 │        Storage / Notifications
                   ▼                 ▼
          local model reasoning   policy + logs
          router → LiteRT → future local LLM/VLM
```

TouchPilot should learn the reasoning core from OpenClaw and PicoClaw, the
Android control/tool loop from ClawMobile and MobileClaw, and the chat event UX
from wende/mobileclaw. The result should still be Android-native, local-first,
and safety-focused rather than a clone of any reference project.

## Milestone Status

| Milestone | Status | Purpose |
| --- | --- | --- |
| Milestone 1 | Complete | Local-first Android agent foundation. |
| Milestone 2 | Next | Hybrid local AI core. |
| Milestone 3 | Planned | Screen context and local understanding. |
| Milestone 4 | Planned | Reliable Android control. |
| Milestone 5 | Planned | Local multi-step agent loop. |
| Milestone 6 | Planned | Skills system v2. |
| Milestone 7 | Planned | Safety and policy v2. |
| Milestone 8 | Planned | Local model quality. |
| Milestone 9 | Planned | Product UI v2. |
| Milestone 10 | Planned | Workflow automation. |
| Milestone 11 | Planned | Demonstration to skill. |
| Milestone 12 | Planned | Local extension tools. |
| Milestone 13 | Planned | Real device beta. |
| Milestone 14 | Planned | Advanced local AI. |
| Milestone 15 | Planned | 1.0 release. |

The active roadmap starts at Milestone 2. Milestone 1 is retained only as the
completed baseline so future work has a clear starting point.

## Completed Baseline: Milestone 1

Milestone 1 included the original Phase 1 through Phase 11 work. It proved the
foundation: Android tools, chat-first UI, local router, LiteRT runtime path,
skills, policy, logs, CI, and live emulator validation.

```text
user input
   │
   ▼
┌────────────┐   ┌──────────────┐   ┌──────────────┐
│ Android UI │ → │ Agent MVP    │ → │ JSON Command │
│ shell      │   │ + local mode │   │ Loop         │
└────────────┘   └──────┬───────┘   └──────┬───────┘
                        │                  │
        skills/MCP ◄────┼────► LiteRT      ▼
                        │          ┌──────────────┐
                        ▼          │ Android      │
              safety + approvals → │ Tools        │
                                   └──────┬───────┘
                                          ▼
                                 logs + live tests
```

Completed scope:

- native Android app shell
- AccessibilityService permission flow
- observe, tap, type, scroll, open app, back, home, and wait tools
- local tool execution log
- local router mode
- LiteRT command-routing runtime path
- Markdown skills and allowlists
- MCP client support
- safety policy and approval UI
- product UI sections
- debug traces
- CI, lint, unit-test workflow, issue templates, PR template, and labels
- live emulator validation

## Future Roadmap

## Milestone 2: Hybrid Local AI Core

Goal: make TouchPilot a real local AI product, not only a command router.

```text
user message
    │
    ▼
┌────────────┐   ┌────────────┐   ┌──────────────┐
│ Intent     │ → │ Reasoning  │ → │ Agent Event  │
│ Gate       │   │ Core       │   │ Stream       │
└────────────┘   └─────┬──────┘   └──────┬───────┘
                       │                 │
      exact commands ◄─┼─► local model   ▼
      skill match      │          tool running / success /
      safety precheck  │          failure / approval / final
                       ▼
              deterministic fallback
```

Deliverables:

- Define the local reasoning core boundary.
- Add an intent gate for exact commands, known skills, unsafe requests, and
  model-needed requests.
- Remove cloud/provider fallback from the normal product path.
- Define local model input/output contracts for intent, tool selection, target
  ranking, and final answer.
- Build agent event types for messages, tool calls, approvals, errors, and
  final responses.
- Keep deterministic router as fallback and test baseline.
- Add offline-only validation for the main user flows.

Exit criteria:

TouchPilot can route simple requests deterministically, use a local model path
for ambiguous requests, and expose all work as local agent events.

## Milestone 3: Screen Context and Understanding

Goal: let TouchPilot understand the current Android screen locally.

```text
Android screen
      │
      ▼
┌────────────┐   ┌────────────┐   ┌──────────────┐
│ Observe    │ → │ Context    │ → │ Local Screen │
│ Screen     │   │ Builder    │   │ Understanding│
└────────────┘   └─────┬──────┘   └──────┬───────┘
                       │                 │
      UI tree ◄────────┼──────► OCR      ▼
      current app      │         summary + suggested actions
      clickable nodes  │
                       ▼
             future local VLM fallback
```

Deliverables:

- Build a structured screen context from Accessibility data.
- Include current app, visible text, clickable nodes, input fields, bounds, and
  scroll state.
- Add local screen summaries.
- Add suggested actions from visible controls.
- Add OCR fallback only where Accessibility data is weak.
- Keep local VLM as a future fallback, not the first dependency.

Exit criteria:

TouchPilot can say what screen it sees and suggest useful local actions without
cloud inference.

## Milestone 4: Reliable Android Control

Goal: make Android action execution reliable enough for real tasks.

```text
agent tool request
      │
      ▼
┌─────────────┐   ┌────────────┐   ┌──────────────┐
│ Tool Router │ → │ Selector   │ → │ Android      │
│ validation  │   │ Resolver   │   │ Action       │
└─────────────┘   └─────┬──────┘   └──────┬───────┘
                        │                 │
        observe tree ◄──┼──► wait policy  ▼
                        │          verification
                        ▼                 │
               retry / recovery ◄────────┘
```

Deliverables:

- Improve node IDs and selector stability.
- Support target resolution by text, node ID, bounds, semantic match, and OCR
  fallback.
- Add action verification after every tool call.
- Add structured retry and wait policies.
- Improve failures with actionable messages.
- Expand live tests on emulator and real device.

Exit criteria:

TouchPilot can reliably execute observe, open app, tap, type, scroll, back,
home, and wait across common Android screens.

## Milestone 5: Local Agent Loop

Goal: support short multi-step tasks through a local observe-decide-act loop.

```text
user goal
   │
   ▼
┌────────┐   ┌──────────┐   ┌────────┐   ┌──────────┐
│ Parse  │ → │ Observe  │ → │ Decide │ → │ Act      │
│ intent │   │ screen   │   │ locally│   │ safely   │
└────────┘   └──────────┘   └───┬────┘   └────┬─────┘
                                │             │
                                ▼             ▼
                         verify result ← tool output
                                │
                                ▼
                         continue or stop
```

Deliverables:

- Add step-based agent loop.
- Feed tool results back into the next local decision.
- Add maximum step limits and stop conditions.
- Add uncertainty handling and user clarification.
- Add verification-driven continuation.
- Make every step visible in the chat event stream.

Exit criteria:

TouchPilot can complete small multi-step local tasks such as opening a settings
subscreen and stopping when the expected state is reached.

## Milestone 6: Skills System v2

Goal: turn skills into the local knowledge layer for Android tasks.

```text
local skill files
       │
       ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Skill      │ → │ Skill        │ → │ Agent      │
│ Loader     │   │ Registry     │   │ Context    │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
          allowlists ◄──┼──► examples     ▼
                        │          tool visibility
                        ▼
                 skill enable UI
```

Deliverables:

- Define stable `SKILL.md` metadata.
- Add skill examples, allowed tools, risk level, and success criteria.
- Add local skill matching from user requests.
- Add skill enable/disable UI.
- Add starter Android skills for Settings, browser, app launch, help, and safe
  message drafting.
- Add skill tests and sample traces.

Exit criteria:

TouchPilot can choose a local skill, limit tools by skill policy, and execute
or explain the skill path.

## Milestone 7: Safety and Policy v2

Goal: make local Android automation safe by default.

```text
tool request
    │
    ▼
┌────────────┐   ┌────────────┐   ┌──────────────┐
│ Risk       │ → │ Policy     │ → │ Decision     │
│ Classifier │   │ Engine     │   │ allow/ask/deny │
└────────────┘   └─────┬──────┘   └──────┬───────┘
                       │                 │
       app rules ◄─────┼─────► skill     ▼
       sensitive text  │       rules   approval UI
                       ▼                 │
                  redacted logs ◄────────┘
```

Deliverables:

- Define policy rules per tool, app, and workflow class.
- Block or ask for sensitive workflows: payments, sending messages, deletion,
  account changes, permissions, security settings, and purchases.
- Add risk-specific approval copy.
- Add redaction tests for logs and traces.
- Add policy simulation tests.

Exit criteria:

The local model can suggest actions, but only the policy layer can approve
execution.

## Milestone 8: Local Model Quality

Goal: replace the tiny proof model with useful local AI components.

```text
training examples
       │
       ▼
┌────────────┐   ┌────────────┐   ┌──────────────┐
│ Dataset    │ → │ Local      │ → │ Android      │
│ + eval set │   │ Models     │   │ Runtime      │
└────────────┘   └─────┬──────┘   └──────┬───────┘
                       │                 │
        accuracy ◄─────┼─────► latency   ▼
                       │          deterministic fallback
                       ▼
                model manifest
```

Deliverables:

- Create local evaluation datasets for intent classification, tool selection,
  argument extraction, target ranking, and screen summary.
- Train or select better LiteRT models for routing and target ranking.
- Add model manifests and versioning.
- Benchmark latency, memory, load time, and accuracy.
- Keep deterministic fallback for every model role.
- Document when to use LiteRT, ExecuTorch, llama.cpp, or a future local VLM.

Exit criteria:

Local models measurably improve routing and screen understanding without making
basic control depend on them.

## Milestone 9: Product UI v2

Goal: make the local AI work visible and understandable.

```text
user opens app
      │
      ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Chat-first │ → │ Agent State  │ → │ Visual     │
│ Home       │   │ Model        │   │ Components │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
      skills ◄──────────┼────────► tools  ▼
      runtime           │          cards / approvals /
      settings          │          traces / errors
                        ▼
                  user trust
```

Deliverables:

- Add screen-summary and suggested-action cards.
- Add tool-call cards with running, success, failure, and blocked states.
- Add approval cards.
- Add skill-use cards.
- Add local model status and benchmark screen.
- Add trace viewer.
- Add clear offline/local-only indicators.

Exit criteria:

The user can see what the local AI understood, what it plans to do, what tool
ran, and why an action was blocked or approved.

## Milestone 10: Workflow Automation

Goal: save successful Android task traces as repeatable local workflows.

```text
successful task trace
        │
        ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Trace      │ → │ Workflow     │ → │ Replay     │
│ Capture    │   │ Definition   │   │ Engine     │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
      parameters ◄──────┼──────► policy   ▼
                        │          verify each step
                        ▼
                  workflow review UI
```

Deliverables:

- Capture successful action traces.
- Define workflow files with parameters and expected states.
- Add deterministic replay.
- Add verification after each workflow step.
- Add workflow review UI.
- Add workflow safety checks.

Exit criteria:

TouchPilot can replay known workflows locally with verification and policy
checks.

## Milestone 11: Demonstration to Skill

Goal: let users teach TouchPilot by doing.

```text
user demonstrates task
        │
        ▼
┌────────────┐   ┌────────────┐   ┌──────────────┐
│ Record     │ → │ Trace      │ → │ Skill        │
│ actions    │   │ Summary    │   │ Candidate    │
└────────────┘   └─────┬──────┘   └──────┬───────┘
                       │                 │
       screenshots ◄───┼───► UI tree     ▼
                       │          review / edit / approve
                       ▼                 │
               reusable local skill ◄────┘
```

Deliverables:

- Record user actions with screen context.
- Summarize demonstrations locally.
- Generate skill candidates from traces.
- Let the user review, edit, approve, or discard candidates.
- Replay approved skills.
- Add repair flow when replay fails.

Exit criteria:

A user can demonstrate a task once and convert it into an inspectable local
skill.

## Milestone 12: Local Extension Tools

Goal: extend TouchPilot locally without weakening privacy.

```text
agent needs external local tool
        │
        ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Tool       │ → │ Permission   │ → │ Local Tool │
│ Registry   │   │ Boundary     │   │ Bridge     │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
        MCP/client ◄────┼────► local HTTP ▼
                        │          external result
                        ▼
                 audit + revoke UI
```

Deliverables:

- Decide the local MCP/client/server boundary.
- Add explicit permissions for local extension tools.
- Add local tool registration and revocation UI.
- Add audit logs for extension tool calls.
- Keep Android control permissions separate from external tool permissions.

Exit criteria:

TouchPilot can use optional local tools while keeping trust boundaries visible
and revocable.

## Milestone 13: Real Device Beta

Goal: validate TouchPilot beyond the emulator.

```text
beta user
   │
   ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Install    │ → │ Permission   │ → │ Real       │
│ Flow       │   │ Onboarding   │   │ Device Run │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
      compatibility ◄───┼───► battery     ▼
                        │          bug report export
                        ▼
                  beta checklist
```

Deliverables:

- Real-device onboarding.
- Compatibility testing across Android versions and OEM skins.
- Battery and foreground-service behavior review.
- Local bug report export.
- Real-device beta checklist.
- Known limitations page.

Exit criteria:

TouchPilot works on multiple real Android devices with a repeatable beta test
process.

## Milestone 14: Advanced Local AI

Goal: improve reasoning while staying 100% local.

```text
complex user goal
       │
       ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Local LLM  │ → │ Planner      │ → │ Tool Loop  │
│ / VLM      │   │ + Memory     │   │ + Policy   │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
      screen summary ◄──┼──► preferences  ▼
                        │          verified actions
                        ▼
                 offline assistant UX
```

Deliverables:

- Evaluate local LLM runtime options.
- Evaluate local VLM/OCR fallback options.
- Add richer planning behind the same tool contract.
- Add ambiguity handling and clarification.
- Add local preference learning.
- Add model file management.

Exit criteria:

TouchPilot can handle longer local tasks without changing the tool, skill,
policy, and logging boundaries.

## Milestone 15: 1.0 Release

Goal: ship a stable local Android AI agent.

```text
stable product
      │
      ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Release    │ → │ Signed APK   │ → │ Public     │
│ Checklist  │   │ + Versioning │   │ Users      │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
      docs ◄────────────┼────────► support▼
      tests             │          feedback loop
      safety            ▼
                 changelog + roadmap
```

Deliverables:

- Stable architecture.
- Stable local model/tool/skill contracts.
- Signed APK.
- Public documentation.
- Skill authoring guide.
- Safety model documentation.
- Test and live validation matrix.
- Changelog and release process.

Exit criteria:

TouchPilot is installable, understandable, local-only by default, and safe
enough for public users.
