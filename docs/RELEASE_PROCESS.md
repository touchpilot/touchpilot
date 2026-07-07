# Release Process

This document describes the 1.0-oriented release flow for stable signed artifacts.

## Preconditions

- `main` branch has passing required checks.
- Release manifests are updated for the same contract surface:
  - `app/build.gradle.kts` (`versionCode` / `versionName`)
  - `app/src/main/assets/models/command_router/manifest.json`
  - `app/src/main/assets/skills/manifest.json`
  - `app/src/main/assets/workflows/manifest.json`
- GitHub Actions release secrets configured:
  - `TOUCHPILOT_RELEASE_KEYSTORE_B64`
  - `TOUCHPILOT_KEYSTORE_PASSWORD`
  - `TOUCHPILOT_KEY_ALIAS`
  - `TOUCHPILOT_KEY_PASSWORD`

## Release steps

1. Create a release notes file from `docs/RELEASE_NOTES_TEMPLATE.md` and fill the checklist.
2. Run release checklists in `docs/RELEASE_CHECKLIST.md` and archive results.
3. Tag main:

   ```bash
   git checkout main
   git pull
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```

4. Verify the `Release` GitHub Actions workflow completes successfully.
5. Attach the generated APK and checksum artifacts and publish release notes.

## Post-release

- Archive release notes and validation evidence under `docs/releases/` and compatibility docs if behavior changed.
- Monitor device reports and file any regressions as new issues before the next milestone.
