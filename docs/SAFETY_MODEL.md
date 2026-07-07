# Safety Model

TouchPilot is a local-only Android control runtime. This model describes what
protections are active in the app, what they do not cover, and how to verify
them locally.

Last updated: **2026-07-07**

## What this document covers

This document covers four control planes for runtime safety:

- **Input and execution control** (tool contracts and approvals)
- **Permission scope control** (skills, tools, and MCP command paths)
- **Information safety** (redaction and logs/traces)
- **Auditability** (event stream, approvals, and trace exports)

It does not describe implementation internals or secret policy constants.

## Protection boundary

TouchPilot executes actions through a single local pipeline:

1. Model suggestion or user intent -> command proposal
2. Tool/scope validation against active skill allowlist and tool contracts
3. Local policy evaluation (`PolicyEngine`) with risk-based precedence
4. User approval gating for non-low-risk actions
5. Tool execution through controlled Android entry points

No remote execution happens as part of the default runtime.

## What is protected

### 1) Tool + workflow execution policy

- The app uses in-app policy tables to classify tool risk and workflow class.
- `LOW` tools run without prompts; `MEDIUM/HIGH` tools require approval.
- Sensitive workflow classes (`PAYMENT`, `ACCOUNT_CHANGE`,
  `PERMISSION_CHANGE`, `SECURITY_SETTINGS`, `UNKNOWN_SENSITIVE`, etc.) require
  user approval path and cannot silently run.
- `BLOCK` decisions are enforced in-process and do not execute tool calls.

Relevant docs:
- [Tool Spec](TOOL_SPEC.md)
- [Policy](POLICY.md)
- [Workflows](WORKFLOWS.md)

### 2) Skill and extension scope control

- Skills enter only through local discovery, validation, and registry load.
- Active-skill context is surfaced in approval messaging so users see the source.
- MCP/host tool sources remain subject to the same policy gate.

Relevant docs:
- [Skills](SKILLS.md)
- [Policy](POLICY.md)
- `app/src/main/java/dev/touchpilot/app/security/` (policy evaluation + risk mapping)

### 3) Input redaction and sensitive text handling

- Observation text in `screen_context` is redacted before it is persisted in chat
  summaries, traces, and exported artifacts.
- Sensitive values are flagged as redacted and not emitted as raw text in user-facing
  summaries.
- Raw logs may still be collected for local debugging and are kept in local app
  storage.

### 4) Audit trail

- Every tool run contributes to conversation events and trace entries.
- Approval events include decision kind and rationale.
- Completed runs can be reviewed from logs and exported for offline analysis.
- Exported workflow traces keep redaction settings in place.

## What is not covered

This model does **not** claim to protect against:

- A device that already trusts hostile accessibility extensions or a compromised
  runtime environment.
- OS-level compromise, keylogging, physical device compromise, or external malware
  that already has equivalent permissions.
- Misuse of intentionally granted permissions by a user-chosen model or skill.
- Untrusted offline backups, exports, or artifacts loaded outside the app trust
  boundary.

## Local verification checklist

Use this list before release or before enabling new risk classes:

1. Open the app with real permissions enabled.
2. Run one low-risk action and one high-risk action.
3. Confirm:
   - action shape is constrained to approved tool names in `TOOL_SPEC.md`.
   - policy route shows approval for high-risk actions.
   - logs contain an approval decision event for the high-risk action.
   - screenshots/exports show redacted text and do not expose secrets.
4. Confirm cancellation paths in logs/events preserve safety state.
5. Confirm blocked decisions never execute and remain visible in traces.

## Update process

When policies change materially:

- update `POLICY.md`, `TOOL_SPEC.md`, and this document together,
- run `./gradlew testDebugUnitTest`,
- file follow-up compatibility notes in the changelog/validation docs.

This is a local document and does not require a remote policy service.
