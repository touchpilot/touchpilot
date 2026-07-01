# Compatibility Test Results

Committed result logs from [Device Compatibility Checklist](../../DEVICE_COMPATIBILITY_CHECKLIST.md)
runs. Each file documents one device or emulator profile pass.

## Naming convention

```text
YYYY-MM-DD-<manufacturer>-<model-slug>.md
```

Example: `2026-07-01-samsung-galaxy-s24.md`

## How to add a result

1. Copy [TEMPLATE.md](TEMPLATE.md) to a new file in this directory.
2. Complete all sections after running the checklist.
3. Update [COMPATIBILITY_MATRIX.md](../../COMPATIBILITY_MATRIX.md) and
   [matrix.json](../matrix.json).
4. Reference the result path in your PR or issue.

Results are local-first evidence only. Do not commit secrets, API keys, or
unredacted sensitive screen content.
