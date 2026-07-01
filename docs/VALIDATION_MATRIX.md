# Test & Live Validation Matrix (v1.0)

This document is the canonical validation matrix for TouchPilot v1.0. It lists device / OS combinations, test coverage (unit, integration, live/emulator), tool/workflow coverage, safety-path coverage, and the CI job names that exercise each item.

Purpose
- Give release reviewers a single-page view of what has been validated.
- Surface known gaps and link to evidence (reports, traces, screenshots).
- Be updated automatically by CI when possible and manually when needed.

How to read the matrix
- Device / OS: emulator or physical device tested.
- Unit / Integration / Live: whether coverage exists (✓/partial/✗).
- Tools: high-level capabilities covered (observe, tap, type, scroll, swipe, back/home).
- Safety-paths: approval UI, redaction, policy decisions.
- CI job: name of the GitHub Actions job that exercises the check.

Validation Matrix (initial)

| Device / OS | Unit | Integration | Live/Emulator | Tools covered | Safety-paths | CI job / Evidence |
|---|---:|---:|---:|---|---|---|
| Emulator: TouchPilot_API_35 (Android 15) | ✓ | ✓ | ✓ | observe, tap, type, scroll, back/home, open app | approval UI, deny/allow logs | `Android Live Tests` (android-live-tests.yml) — app/build/reports |
| Generic unit test matrix | ✓ | ✗ | ✗ | n/a | n/a | `gradle test` (android.yml) — test reports |

Known gaps
- Some swipe directions (horizontal pager) and settings panel deep links are pending live emulator validation (see docs/LIVE_TESTING.md checklist).
- Approval UI automated instrumentation coverage is limited; manual live testing used for verification.

Keeping this up-to-date
- Prefer automated updates from CI (see .github/workflows/publish-validation-matrix.yml).
- When adding a new emulator/device or CI job, update this file and add evidence links (artifacts, traces).

Related files
- docs/LIVE_TESTING.md
- docs/OFFLINE_VALIDATION_M2.md
- docs/OFFLINE_VALIDATION_M3.md
- docs/OFFLINE_VALIDATION_M4.md

If you want to extend this into a machine-readable matrix, add `docs/compatibility/matrix.json` following the existing `docs/compatibility/results/TEMPLATE.md`.

