# Local Inference

Phase 5 introduces a local command-provider seam and a deterministic on-device
router for simple safe actions. It does not yet bundle a full local LLM runtime.

## Runtime Evaluation

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

## Local-First Runtime Modes

The Android app now has two runtime modes:

- Local router: default offline deterministic routing for simple commands such
  as observe, back, home, scroll, open app, and tap text.
- Experimental cloud fallback: optional OpenAI-compatible chat completions for
  development and complex tasks.

The local router is intentionally conservative. It is useful for validating the
command-provider boundary, tool policy path, skill allowlists, approvals, and
fallback behavior without shipping a large model runtime prematurely.

Cloud fallback support remains available, but basic Android control should not
require a provider URL, model name, or API key.

## Runtime Boundary

Local inference work should keep one stable contract: command providers emit a
structured JSON command or a final answer, and Android tool execution remains
behind validation, skill allowlists, approval policy, and local logs.

- Deterministic local router: current default and fallback when no model asset
  is available.
- Small local routing model: first real local model target for command
  classification and structured argument extraction.
- Local LLM runtime: future broader reasoning path for multi-step workflows.
- Experimental cloud fallback: optional secondary path, not the primary product
  direction.

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
