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
| Milestone 2 | Complete | Hybrid local AI core. |
| Milestone 3 | Complete | Screen context and local understanding. |
| Milestone 4 | Complete | Reliable Android control. |
| Milestone 5 | Complete | Local multi-step agent loop. |
| Milestone 6 | In final review | Skills system v2. |
| Milestone 7 | Active | Safety and policy v2. |
| Milestone 8 | Planned | Local model quality. |
| Milestone 9 | Planned | Product UI v2. |
| Milestone 10 | Planned | Workflow automation. |
| Milestone 11 | Planned | Demonstration to skill. |
| Milestone 12 | Planned | Local extension tools. |
| Milestone 13 | Planned | Real device beta. |
| Milestone 14 | Planned | Advanced local AI. |
| Milestone 15 | In progress | 1.0 release. |
| Milestone 16 | Planned | Mobile agent host foundation. |
| Milestone 17 | Planned | Local memory and trace system. |
| Milestone 18 | Planned | Workflow capture and replay. |
| Milestone 19 | Planned | Skills and extension management. |
| Milestone 20 | Planned | Skill discovery and run entry. |
| Milestone 21 | Planned | Skill pack expansion. |
| Milestone 22 | Planned | Optional local intelligence. |
| Milestone 23 | Planned | Real device host hardening. |
| Milestone 24 | Planned | 2.0 platform release. |

The active roadmap is between the final Milestone 6 skill-system PRs and the
1.0.0 release work in Milestone 15. Milestones 1 through 5 are retained as
completed baselines so future work has a clear starting point.

Current project status:

- Milestones 1 through 5 are complete in the main branch.
- Milestone 6 is complete and the bundled Skills v2 pack is being finalized for
  the 1.0.0 release line.
- Milestone 15 has started. Open work covers release signing, version bumps,
  documentation refreshes, GitHub Release publishing, and the final release tag
  path.

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

## Milestone 2: Hybrid Local AI Core

Goal: make TouchPilot a real local AI product, not only a command router.

Status: complete. Milestone 2 established the local reasoning core boundary,
intent gate, agent event contract, local model contracts, known-skill routing,
local-only runtime selection, and offline validation for the main user flows.

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

- [x] Define the local reasoning core boundary.
- [x] Add an intent gate for exact commands, known skills, unsafe requests, and
  model-needed requests.
- [x] Remove cloud/provider fallback from the normal product path.
- [x] Define local model input/output contracts for intent, tool selection, target
  ranking, and final answer.
- [x] Build agent event types for messages, tool calls, approvals, errors, and
  final responses.
- [x] Keep deterministic router as fallback and test baseline.
- [x] Add offline-only validation for the main user flows.

Exit criteria:

TouchPilot can route simple requests deterministically, use a local model path
for ambiguous requests, and expose all work as local agent events.

## Future Roadmap

## Milestone 3: Screen Context and Understanding

Goal: let TouchPilot understand the current Android screen locally.

Status: complete. Milestone 3 established normalized screen context,
sensitive-text redaction, screen summaries, suggested actions, and offline
screen-understanding validation.

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
- Validate representative screen understanding offline; see
  [Milestone 3 Offline Screen Understanding Validation](OFFLINE_VALIDATION_M3.md).

Exit criteria:

TouchPilot can say what screen it sees and suggest useful local actions without
cloud inference.

## Milestone 4: Reliable Android Control

Goal: make Android action execution reliable enough for real tasks.

Status: complete. Milestone 4 delivered selector-based target resolution,
hardened core Android tools, bounded wait/retry behavior, explicit tool
verification, and live validation coverage. See
[Milestone 4 Reliable Android Control Validation](OFFLINE_VALIDATION_M4.md).

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

Status: complete. Milestone 5 established the bounded local agent loop,
step-visible event stream, result feedback, stop conditions, and clarification
handling.

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

Status: in final review. The core runtime paths are present: bundled skills are
loaded through a registry, disabled skills are excluded from matching, an active
skill can scope agent context, tool visibility is filtered by skill allowlists,
and approval prompts can include active-skill risk context. Remaining work is
mainly finishing the open skill-pack and skill-management PRs.

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

- Define stable `SKILL.md` metadata; see [Skills](SKILLS.md).
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

Status: active. The first policy model and active-skill risk surfaces are in
main, and follow-up PRs are open for workflow classifiers, centralized policy
decisions, app-aware policy, approval copy, and expanded redaction.

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

## Post-1.0 Direction

Milestone 15 is the stable public release. The post-1.0 path should stay
grounded in the final vision of TouchPilot as a small, auditable mobile agent
host. The work is split into smaller milestones so each one adds a usable piece
of the platform without relying on a local GPT-class model.

## Milestone 16: Mobile Agent Host Foundation

Goal: make TouchPilot a host runtime with explicit lifecycle, permissions, and
session boundaries.

```text
stable 1.0 runtime
       │
       ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Host       │ → │ Session      │ → │ Permission │
│ lifecycle  │   │ boundaries   │   │ boundary   │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
               visibility ◄──────► audit  ▼
                        │          explicit trust
                        ▼
               inspectable on-device control
```

Deliverables:

- Define host-level session creation, pause, stop, and cleanup behavior.
- Add clearer permission surfaces for Android tools, skills, and extensions.
- Add host status indicators for active session, foreground service, and
  permission readiness.
- Keep the runtime visible and explainable when it is active.

Exit criteria:

Users can tell what state the host is in, what it can access, and what scope a
session is using.

## Milestone 17: Local Memory and Trace System

Goal: turn logs and memory into a usable on-device host record.

```text
agent activity
      │
      ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Session    │ → │ Local       │ → │ Trace      │
│ events     │   │ memory      │   │ viewer     │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
      preferences ◄─────┼────► replay     ▼
                        │          export / inspect
                        ▼
                  on-device history
```

Deliverables:

- Add local memory entries for preferences, recurring apps, and task history.
- Add a structured trace viewer for steps, tool calls, approvals, and errors.
- Add trace export and import for debugging and support.
- Add redaction rules for stored traces and memory records.

Exit criteria:

Users can inspect what TouchPilot did, why it did it, and what it remembers on
device.

## Milestone 18: Workflow Capture and Replay

Goal: make successful tasks repeatable as local workflows.

```text
successful task trace
        │
        ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Trace      │ → │ Workflow     │ → │ Replay     │
│ capture    │   │ definition   │   │ engine     │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
      parameters ◄──────┼──────► verify   ▼
                        │          each step
                        ▼
                 workflow review UI
```

Deliverables:

- Add capture from completed agent runs into workflow definitions.
- Add a workflow editor for parameters, expected states, and step order.
- Add deterministic replay with step-by-step verification.
- Add workflow failure recovery and repair suggestions.

Exit criteria:

TouchPilot can turn a known task into a repeatable, inspectable workflow and
replay it with verification.

## Milestone 19: Skills and Extension Management

Goal: make skills and external tools first-class, revocable host resources.

```text
skill files / MCP tools
         │
         ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Registry   │ → │ Permissions  │ → │ Revocation │
│ and scope  │   │ and policy   │   │ and audit  │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
        import/export ◄──┼──────► enable  ▼
                        │          disable
                        ▼
              user-managed capability set
```

Deliverables:

- Add skills import, export, enable, disable, and revocation flows.
- Add explicit MCP and local extension permissions separate from Android tool
  permissions.
- Add a skill and extension review UI with visible scope and risk.
- Expand audit logs so external capability use is easy to inspect later.

Exit criteria:

Users can manage skills and extensions as visible capabilities instead of
hidden runtime behavior.

## Milestone 20: Skill Discovery and Run Entry

Goal: make the product page show individual skills users can run directly.

```text
use page
   │
   ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Skill      │ → │ Skill run    │ → │ Chat /    │
│ list       │   │ entry point  │   │ detail    │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
           examples ◄───┼──────► active   ▼
                        │          skill scope
                        ▼
                "what can I do here?" page
```

Deliverables:

- Show individual skills on the Use page instead of only skill packs.
- Surface example prompts for each skill so users can understand the action.
- Make each skill card a runnable entry point that activates the skill and
  takes the user into the next step.
- Keep the page focused on discoverability and simple entry into common tasks.

Exit criteria:

Users can open the Use page, understand what TouchPilot can do, and run a skill
from that list.

## Milestone 21: Skill Pack Expansion

Goal: grow TouchPilot through reusable skill packs for common Android tasks.

```text
core skills
     │
     ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Pack       │ → │ Review       │ → │ Bundled    │
│ backlog    │   │ and testing  │   │ skill pack │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
        app-specific ◄──┼──────► release  ▼
                        │          cadence
                        ▼
                 richer on-device knowledge
```

Deliverables:

- Add a skill-pack backlog for common Android surfaces and repeatable tasks.
- Add pack-level authoring guidelines for tool allowlists, risk, and examples.
- Add review and validation for new bundled skills before they ship.
- Add pack release notes so new skills are discoverable and auditable.
- Expand bundled skills into more app-specific and task-specific coverage.

Exit criteria:

TouchPilot can gain new capability primarily by adding vetted skill packs, not
just by changing the core agent runtime.

## Milestone 22: Optional Local Intelligence

Goal: add stronger local reasoning without making it required for the product.

```text
hosted Android tools
       │
       ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Optional   │ → │ Local        │ → │ Better     │
│ model pack │   │ planner/VLM  │   │ reasoning  │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
     deterministic ◄────┼────► fallback   ▼
                        │          same tool contract
                        ▼
                 capable-device upgrade path
```

Deliverables:

- Add model file management for optional local LLM, VLM, and OCR assets.
- Add a local planner that can sit on the same tool contract as the router.
- Improve ambiguity handling, clarification, and preference learning.
- Benchmark optional model paths against deterministic fallback.

Exit criteria:

TouchPilot can use richer local intelligence on capable devices, but the core
agent still works when those models are absent.

## Milestone 23: Real Device Host Hardening

Goal: validate the host on real devices and make it dependable.

```text
beta device
   │
   ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Install    │ → │ Device      │ → │ Support    │
│ and setup  │   │ behavior    │   │ artifacts  │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
     compatibility ◄────┼────► battery   ▼
                        │          bug export
                        ▼
                  real-device beta
```

Deliverables:

- Add real-device onboarding and compatibility checks.
- Review battery, foreground service, and OEM-specific behavior.
- Add local bug report export and support bundles.
- Validate the host flow across multiple Android versions.

Exit criteria:

TouchPilot behaves predictably on real devices, not just emulators.

## Milestone 24: 2.0 Release

Goal: ship the post-1.0 mobile agent host as a stable platform release.

```text
TouchPilot host
      │
      ▼
┌────────────┐   ┌──────────────┐   ┌────────────┐
│ Versioned  │ → │ Safety +     │ → │ Public     │
│ contracts  │   │ extensions   │   │ platform   │
└────────────┘   └──────┬───────┘   └─────┬──────┘
                        │                 │
       docs/tests ◄─────┼──────► release  ▼
                        │          process
                        ▼
                  stable host product
```

Deliverables:

- Freeze and version the tool, skill, workflow, memory, and policy contracts.
- Publish the host-oriented documentation set and release process.
- Keep extension boundaries explicit, revocable, and auditable.
- Keep optional local intelligence as an enhancement, not a requirement.

Exit criteria:

TouchPilot is a stable, auditable Android agent host with optional local
intelligence and a clear platform boundary.
