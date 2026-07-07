# Skill Authoring Guide

Skills are local-first bundles that declare tool allowlists, prompts, and
behavioral guard rails. This guide points to the sections in the runtime
contracts that keep skill definitions stable.

## Authoring quick path

1. Start with a valid skill manifest under `app/src/main/assets/skills/`.
2. Ensure the skill:
   - declares its **risk** and **scope**
   - lists only intended tool names from [TOOL_SPEC.md](TOOL_SPEC.md)
   - includes short examples for expected behavior
3. Add the `.md` or `.json` entry to your local skill catalog.
4. Validate by loading with a local session and reviewing tool execution logs.

## Review requirements

A skill should expose enough review surface for safe operation:

- Tool allowlist before execution
- risk label in logs and settings UI
- explicit behavior boundaries in prompts/examples
- deterministic expected outputs in docs where possible

## See also

- [SKILLS.md](SKILLS.md)
- [Policy](POLICY.md)
- [Contracts](CONTRACTS.md)
