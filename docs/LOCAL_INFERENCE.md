# Local Inference

TouchPilot has a local command-provider boundary, a deterministic on-device
router for simple safe actions, and a LiteRT command model runtime path. The
first bundled model is a tiny route-logit model that verifies on-device LiteRT
execution before larger trained routing models are added.

## Runtime Evaluation

TouchPilot includes a local model evaluation runner for Milestone 8. It reads
committed fixture sets, evaluates deterministic local routing and target
ranking output only, and prints total cases, passed cases, failed cases, and
failure details. The runner does not call network services or execute Android
actions.

Run the full minimal eval set with one command:

```bash
./gradlew localModelEval
```

You can also run the unified test class directly:

```bash
./gradlew testDebugUnitTest --tests 'dev.touchpilot.app.eval.LocalModelEvalRunnerTest'
```

Focused suite tests remain available:

```bash
./gradlew testDebugUnitTest --tests 'dev.touchpilot.app.tools.targets.TargetRankingEvaluatorTest'
./gradlew testDebugUnitTest --tests 'dev.touchpilot.app.eval.CommandRoutingEvaluatorTest'
```

The current committed fixture sets live at:

- `app/src/test/resources/target-ranking/fixtures.json`
- `app/src/test/resources/command-routing/fixtures.json`

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

The manifest is static, local metadata that identifies the bundled model role
and the contract the runtime must understand. It must not reference remote
assets.

```json
{
  "role": "command_router",
  "runtime": "LiteRT",
  "model_asset": "models/command_router/model.tflite",
  "tokenizer_asset": null,
  "version": "tiny-router-1",
  "contract_version": 1
}
```

Manifest fields:

| Field | Meaning |
| --- | --- |
| `role` | The local model role the asset serves (e.g. `command_router`). |
| `runtime` | Runtime that loads the asset (currently `LiteRT`). |
| `model_asset` | Path to the bundled model asset, relative to `assets/`. |
| `tokenizer_asset` | Optional tokenizer asset path; `null`/blank when unused. |
| `version` | Human-readable asset version label. |
| `contract_version` | Input/output contract version the asset supports. Must be `>= 1` and `<=` the runtime's `SUPPORTED_CONTRACT_VERSION`. |

`LocalModelManifest.parse` reads this file and applies defaults for omitted
optional fields; `LocalModelManifest.validationErrors` validates it. A manifest
that fails validation (blank required field, or an unsupported `contract_version`)
makes the local model report as unavailable and the runtime falls back to the
deterministic router. `LocalModelManifestTest` validates the bundled manifest.

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

## Benchmark Summary

Issue #268 requires a minimal local benchmark summary that maintainers can use
to compare model changes without external services.

The benchmark path is intentionally narrow:

- Uses static local tasks only.
- Does not execute Android actions.
- Does not send network requests.
- Measures model load time and repeated `route()` inference time.
- Reports a simple heap delta metric when the runtime can observe it.

### Run the formatter/unit contract

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --tests 'dev.touchpilot.app.localinference.LocalModelBenchmarkTest'
```

### Run the live bundled-model benchmark

This test requires an attached emulator or device:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.touchpilot.app.localinference.LocalModelBenchmarkSummaryLiveTest
```

The live test logs a PR-friendly summary like:

```text
Local model benchmark summary
runtime=LiteRT version=tiny-router-1 asset=models/command_router/model.tflite available=true
load_ms=12.34 load_heap_delta_kb=256
iterations_per_scenario=5
- open_settings: avg_ms=0.42 min_ms=0.39 max_ms=0.51 avg_heap_delta_kb=0 sample={"tool":"open_app","args":{"target":"settings"}}
notes=static local examples only | no Android tool execution | no network requests
```

### Limitations

- Results are only comparable on roughly similar hardware and thermal state.
- The current benchmark exercises the bundled LiteRT command router only.
- Heap delta is a coarse process-level signal, not a memory profiler.
- The benchmark is designed for regression checks in PR review, not product UI.

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
