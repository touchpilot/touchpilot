# OCR Fallback Boundary

TouchPilot's primary local screen-understanding path is the Android Accessibility
service. Some surfaces (Canvas/SurfaceView rendering, custom views, games,
intentionally obfuscated screens) expose weak or empty Accessibility trees. The
OCR fallback boundary defined here exists so the rest of the agent has one place
to ask "is what I am seeing usable, and if not, can OCR help?" without making OCR
or a local VLM a Milestone 3 dependency.

## Pipeline

```text
Accessibility observation
   ─► screen context builder
   ─► ContextQualityDetector
       ─► Strong  ─► summarize / suggest directly
       ─► Weak    ─► OcrFallback.attempt(...)
                    ─► Recognized: surface as untrusted text, do not act on it
                    ─► Unavailable: respond honestly that the screen cannot be read
                    ─► NotAttempted: same conservative copy as Unavailable
       ─► Empty   ─► refuse to guess
```

## Package

All boundary types live under `dev.touchpilot.app.screen.ocr`:

- `ObservedScreenSignals` — small, platform-free value type carrying the counts
  the detector needs (total nodes, visible text count, clickable count, input
  field count, max depth, package name).
- `ContextQualityDetector` — returns `ContextQuality.Strong`,
  `ContextQuality.Weak(reason)`, or `ContextQuality.Empty`. Thresholds are
  injectable.
- `WeakReason` — `NO_VISIBLE_TEXT`, `NO_CLICKABLE_NODES`, `SHALLOW_TREE`,
  `MOSTLY_EMPTY`.
- `OcrFallback` — interface with `attempt(OcrRequest): OcrFallbackResult`.
- `OcrFallbackResult` — sealed: `NotAttempted`, `Unavailable(reason)`,
  `Recognized(text, confidence)`.
- `NoOpOcrFallback` — default boundary that always returns `Unavailable`.
- `WeakContextResponse` — generates the honest copy callers should emit for
  weak/empty contexts.

The detector takes counts, not Android types, so it runs as a JVM unit test and
so a future caller can derive signals from either an `AccessibilityNodeInfo`
walk or a higher-level screen context model (for example the one being added in
[#39](https://github.com/touchpilot/touchpilot/issues/39)).

## Local-only constraint

OCR and VLM implementations behind this interface MUST run fully on device. The
project direction (see `ROADMAP.md`, `LOCAL_INFERENCE.md`) is 100% local for
core product behavior, and OCR is no exception. Cloud OCR is not part of this
boundary and should not be added behind it.

A future LiteRT, ExecuTorch, or llama.cpp-backed OCR/VLM runtime can implement
`OcrFallback` once the runtime is selected. Until then, the bundled
implementation is `NoOpOcrFallback`, which returns `Unavailable` and lets the
agent respond honestly instead of guessing.

## Safety

- OCR output is untrusted noise. Any `Recognized.text` MUST pass through the
  existing `SensitiveTextRedactor` before being written to logs or traces, and
  MUST NOT be used as a tool argument without passing through validation, the
  active skill allowlist, and the safety policy — the same pipeline that
  protects against any other untrusted command source.
- Weak context must not cause unsafe guessing or blind tapping. The
  `WeakContextResponse` copy is intentionally conservative and includes "I will
  not act on it without confirmation" when recognized text is surfaced.
- Empty context produces a refusal rather than a guess.

## What is intentionally out of scope here

- Wiring the detector into the live `AccessibilityService` observation path —
  that belongs with the screen context builder
  ([#40](https://github.com/touchpilot/touchpilot/issues/40)).
- Choosing an OCR runtime and shipping a real `OcrFallback` implementation —
  deferred to the local OCR/VLM milestone.
- Capturing screen pixels for OCR — also deferred, and gated behind the same
  local-only and policy constraints when it lands.
