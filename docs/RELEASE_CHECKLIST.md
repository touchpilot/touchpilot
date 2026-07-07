# TouchPilot Release Checklist (1.0 + patch releases)

This checklist is used for release PRs and release tag validation.
Use one checklist file per release and archive it with release artifacts.

## 1) Versioning and contract surface

- [ ] Bump app version in `app/build.gradle.kts`
  - `versionName` and `versionCode` are the published app package versions.
- [ ] Confirm model manifest version:
  - `app/src/main/assets/models/command_router/manifest.json`
    - `version`
    - `contract_version`
- [ ] Confirm skill pack version:
  - `app/src/main/assets/skills/manifest.json`
    - `pack_version`
    - all expected skill ids are listed
- [ ] Confirm workflow versioning:
  - `app/src/main/assets/workflows/manifest.json`
    - `schema_version`
  - all `app/src/main/assets/workflows/*/workflow.json` files use `version: 1`
  - runtime parser enforces workflow version via `WorkflowDefinition.CURRENT_VERSION`

## 2) Build and security

- [ ] Validate signing key source:
  - `TOUCHPILOT_KEYSTORE_*` values are set in CI secret scope for release builds.
- [ ] Run `./gradlew :app:assembleRelease` with release signing properties set.
- [ ] Verify `release-artifacts/app-release.apk` is created.
- [ ] Verify file is SHA-256 checksummed before publishing.

## 3) Test and validation

- [ ] `./gradlew testDebugUnitTest`
- [ ] `./gradlew assembleDebug` and `./gradlew assembleRelease`
- [ ] `./gradlew lintDebug`
- [ ] Run required live checks from `docs/LIVE_TESTING.md`
- [ ] Export and attach at least one bug report with current checks and logs (per contributor review template)

## 4) Documentation

- [ ] Copy and complete `docs/RELEASE_NOTES_TEMPLATE.md`.
- [ ] Update release notes for release evidence and migration notes.
- [ ] Link release artifact + checksums in release notes.
- [ ] Update `docs/VALIDATION_MATRIX.md` if validation scope changed.
- [ ] Confirm `docs/RELEASE_PROCESS.md` steps were followed end-to-end.

## 5) Release execution

- [ ] Create a tagged commit from `main` (e.g., `v1.0.0`).
- [ ] Trigger `Release` workflow on tag push.
- [ ] Confirm `Release` workflow completes successfully.
- [ ] Publish checksums and release notes.

## Post-release

- [ ] Confirm APK install works on at least one physical Android 11+ device.
- [ ] Notify community with release notes and any migration notes.

Reference issue: [#375](https://github.com/touchpilot/touchpilot/issues/375).
