# Graphite pthread experiment

This module is not published and is included only with:

```shell
./gradlew -Pgraphite.pthreadsExperiment=true \
  :experiments:wasm-pthreads:stageJsPthreadExperiment
```

The shipped Skia Wasm archives were built without the `atomics` and
`bulk-memory` target features. Emscripten therefore rejects them when the main
and Graphite side modules enable shared-memory pthreads. The experiment must
first rebuild JetBrains Skia tag `m152-7bb45c7c26` after applying
`skia-pthreads.patch`.

The build uses Emscripten 4.0.7, matching Skiko's
`docker/linux-emscripten-amd64/Dockerfile`.

Pass the rebuilt checkout to Gradle and stage either host:

```shell
./gradlew \
  -Pgraphite.pthreadsExperiment=true \
  -Pskia.dir=/absolute/path/to/skia-m152 \
  :experiments:wasm-pthreads:stageJsPthreadExperiment

./gradlew \
  -Pgraphite.pthreadsExperiment=true \
  -Pskia.dir=/absolute/path/to/skia-m152 \
  :experiments:wasm-pthreads:stageWasmPthreadExperiment
```

Serve a staged distribution with the required isolation headers:

```shell
bun run server.ts build/pthreadExperiment/js 8080
```

Then open <http://localhost:8080>. A valid test environment must report
`crossOriginIsolated == true`, expose `SharedArrayBuffer`, and expose WebGPU.
The probe intentionally stops instead of falling back to the browser main
thread when any prerequisite is missing.

For headless Chrome diagnostics, create a blank DevTools target first and let
the Bun client navigate it so worker exceptions are captured from startup:

```shell
bun run cdp-probe.ts \
  ws://127.0.0.1:9223/devtools/page/TARGET_ID \
  30000 \
  http://127.0.0.1:8080/
```

## Current result

Chrome for Testing 152.0.7977.54 passes the isolation, SharedArrayBuffer,
WebGPU, OffscreenCanvas transfer, native `emscripten_dlopen`, render-pthread
Context creation, and two-recorder-pthread startup stages. The gate currently
fails when recorder work first creates a GPU buffer. Emscripten 4.0.7 keeps a
separate JavaScript WebGPU handle registry in each pthread, so recorder workers
cannot resolve the device handle created in the render worker.

The source also contains a diagnostic compatibility shim for the legacy
`maxInterStageShaderComponents` limit expected by `-sUSE_WEBGPU=1`. It must not
be treated as a production fix.

The focused Emdawnwebgpu gate reaches the same architectural limit without
Skia. It uses Dawn commit `1e897275172a23f27b0022fa6beae3084ed54a9b`, the
revision pinned by Skia m152, and Emscripten 4.0.7 with
`shared_memory=true`. Chrome for Testing 152.0.7977.54 reports the imported
device in the render pthread's JavaScript object table, but the same native
handle is absent from both recorder pthread tables:

```text
[emdawn gate] render handle=145840 local-js-object=yes
[emdawn gate] recorder=1 handle=145840 local-js-object=no
[emdawn gate] recorder=0 handle=145840 local-js-object=no
```

Therefore Emdawnwebgpu m152 does not make one WebGPU device usable from
multiple pthread realms. The browser architecture must be revisited; replacing
the legacy binding alone is not a fix.

The minimal gate sources are `emdawn-handle-gate.cpp` and
`emdawn-handle-gate.html`. Build the matching Emdawn package and compile the
probe with its local port:

```shell
em++ emdawn-handle-gate.cpp \
  -o emdawn-handle-gate.mjs \
  --use-port=/absolute/path/to/emdawnwebgpu_pkg/emdawnwebgpu.port.py:shared_memory=true \
  -pthread \
  -sPTHREAD_POOL_SIZE=3 \
  -sMODULARIZE=1 \
  -sEXPORT_ES6=1 \
  -sEXPORT_NAME=createGate \
  -sENVIRONMENT=web,worker \
  -sALLOW_BLOCKING_ON_MAIN_THREAD=0 \
  -sASSERTIONS=1 \
  -sEXPORTED_FUNCTIONS=_main,_emdawn_device_ready,_emdawn_device_failed,_emdawn_render_status,_emdawn_recorder_status \
  -sNO_EXIT_RUNTIME=1
```

## Gate

The authoritative pass criteria and fallback rules live in
`SURFACE_AND_NATIVE_THREADS.md` under "Browser pthread prototype".

The current native probe covers Context creation on the render pthread, two
real Graphite recorder pthreads, opaque native Recording handoff, deterministic
insertion, presentation, and overlap timing. Only Context creation and recorder
startup have executed successfully so far; Recording handoff, insertion, and
presentation remain blocked by the WebGPU handle failure. Resize,
cancellation, shutdown, the 60-second bounded-load test, and the complete
metric set still have to be implemented and measured before the gate can pass.
