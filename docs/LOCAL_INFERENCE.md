# Local Inference

TouchPilot has a local command-provider boundary, a deterministic on-device
router for simple safe actions, and a LiteRT command model runtime path. The
first bundled model is a tiny route-logit model that verifies on-device LiteRT
execution before larger trained routing models are added.

## Runtime Evaluation

TouchPilot now includes a static target-ranking evaluation path for Milestone 8.
It uses committed fixture screens plus expected node IDs to measure whether the
existing deterministic ranking path keeps the right target at rank 1 or within
an allowed top-N threshold, without executing any Android action.

Run it with:

```bash
./gradlew testDebugUnitTest --tests 'dev.touchpilot.app.tools.targets.TargetRankingEvaluatorTest'
```

The current committed fixture set lives at:

- `app/src/test/resources/target-ranking/fixtures.json`

### ExecuTorch

ExecuTorch is the strongest fit for a future Android-native LLM path. It
provides Java/Kotlin API bindings through an Android AAR, includes JNI/native
runtime pieces, and publishes Android artifacts for generic and LLaMA use cases.
It is the best candidate when TouchPilot needs a PyTorch-oriented deployment
path with packaged Android integration.

Tradeoffs:

- Requires model export and runtime packaging work.
- Backend choice matters for device performance.
- Adds native dependency and model asset lifecycle complexity.

### LiteRT

LiteRT is the best fit for small routing, classifier, embedding, or other
compact on-device models that can use Android CPU/GPU/NPU acceleration. The
modern `CompiledModel` API is available for Android, while the legacy
`Interpreter` API remains available for compatibility.

Tradeoffs:

- Excellent Android integration for supported model formats.
- Better suited to small task models than arbitrary GGUF LLMs.
- Requires conversion or selection of LiteRT-compatible models.

### llama.cpp

llama.cpp remains the most flexible path for GGUF local LLMs, especially when
model portability and quantization matter. It is attractive for offline
experiments and broad model support, but Android app integration requires
native/JNI packaging and careful memory/performance tuning.

Tradeoffs:

- Broad GGUF model support and quantization options.
- Android integration is more native-build heavy.
- Device RAM, thermals, and token speed will strongly constrain UX.

## Local Runtime Modes

The Android app should keep all core runtime modes local:

- Local router: default offline deterministic routing for simple commands such
  as observe, back, home, scroll, open app, and tap text.
- Local model: LiteRT command-routing runtime. If the model cannot be loaded
  or returns invalid output, the run stops with a final answer. No fallback to
  the deterministic router occurs.
- Future local LLM/VLM: broader local reasoning, planning, and visual fallback
  behind the same tool and policy contract.

The local router is intentionally conservative. It is useful for validating the
command-provider boundary, tool policy path, skill allowlists, approvals, and
fallback behavior without shipping a large model runtime prematurely.

Cloud fallback is not part of the product direction. Basic Android control and
AI-assisted routing should not require a provider URL, model name, or API key.

## Runtime Boundary

Local inference work should keep one stable contract: command providers emit a
structured JSON command or a final answer, and Android tool execution remains
behind validation, skill allowlists, approval policy, and local logs.

- Deterministic local router: current default. Not used as a fallback when the
  LiteRT model cannot be loaded — those runs stop with a final answer instead.
- Small local routing model: current LiteRT path for command route logits and
  future target for richer classification and structured argument extraction.
- Local LLM runtime: future broader reasoning path for multi-step workflows.

## LiteRT Command Router Asset Contract

The first local model target is a small command router, not a general chat
model. The Android app currently bundles a tiny LiteRT model to exercise real
on-device inference and looks for:

- `app/src/main/assets/models/command_router/manifest.json`
- `app/src/main/assets/models/command_router/model.tflite`

The manifest describes the runtime and model asset:

```json
{
  "runtime": "LiteRT",
  "model_asset": "models/command_router/model.tflite",
  "tokenizer_asset": null,
  "version": "tiny-router-1"
}
```

The bundled `tiny-router-1` model accepts compact task features and emits route
logits for these commands:

- `press_back`
- `press_home`
- `scroll` backward
- `scroll` forward
- `open_app`
- `tap`

This first model intentionally keeps argument extraction in Kotlin. Larger
trained command-routing models can replace `model.tflite` behind the same JSON
command contract.

The model runtime must emit the existing command-provider JSON contract:

```json
{
  "tool": "open_app",
  "args": {
    "target": "Settings"
  }
}
```

or:

```json
{
  "final": "I cannot do that safely."
}
```

Every model output is treated as untrusted. It still passes through JSON
parsing, tool validation, skill allowlists, safety policy, approval flow, and
redacted logs/traces.

## Recommended Next Runtime

Use LiteRT first for a small command-routing model, because the target task is
classification/structured routing rather than general chat. Keep ExecuTorch as
the preferred future path for Android-native LLM experiments. Use llama.cpp for
GGUF compatibility once the app has a native runtime boundary and model-file
management.

References:

- https://docs.pytorch.org/executorch/stable/using-executorch-android.html
- https://ai.google.dev/edge/litert/android
- https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
