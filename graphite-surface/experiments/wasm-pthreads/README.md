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
  http://127.0.0.1:8080/ \
  1200x900 \
  /tmp/graphite-pthread-gate.png
```

## Current result

The unmodified binding passes isolation, SharedArrayBuffer, WebGPU,
OffscreenCanvas transfer, native `emscripten_dlopen`, render-pthread Context
creation and two-recorder-pthread startup. It then fails when recorder work
first creates a GPU buffer because each pthread has a separate JavaScript
WebGPU handle registry.

The synchronous proxy variant resolves that ownership failure. The real
Graphite gate passes with two native recorder pthreads, deterministic
Recording insertion, successful presentation and 44.55 ms of measured CPU
overlap. This validates the architecture, but not production readiness. The
remaining work and acceptance criteria are defined in the root
`WEB_PTHREADS_MIGRATION.md`.

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

### Synchronous proxy experiment

A follow-up gate on 2026-08-26 tested Emdawn `v20260824.202544` with the
release-recommended Emscripten 5.0.6. The unmodified binding reproduced the
missing-handle result. A second build added `__proxy: "sync"` to the Emdawn
library functions, matching the workaround formerly used by Emscripten's
WebGPU binding.

In Chrome 151.0.7922.174, both recorder pthreads still reported that the device
was absent from their local JavaScript object tables. With synchronous proxying,
however, both successfully created and released a `WGPUBuffer` because the
binding calls ran on the main runtime thread:

```text
baseline: recorder0=-1 recorder1=-1 local0=-1 local1=-1
proxy:    recorder0= 3 recorder1= 3 local0=-1 local1=-1
```

This proves the ownership workaround for the focused Emdawn buffer gate. It
does not by itself prove the full Graphite path or acceptable performance. See
the root `EMDAWN_PTHREAD_PROXY_FINDINGS.md` for evidence, architecture
constraints, exact versions, logs, and remaining tests.

The minimal gate sources are `emdawn-handle-gate.cpp` and
`emdawn-handle-gate.html`. Build the matching Emdawn package and compile the
probe with its local port:

```shell
em++ emdawn-handle-gate.cpp \
  -o emdawn-handle-gate.mjs \
  --use-port=/absolute/path/to/emdawnwebgpu_pkg/emdawnwebgpu.port.py:shared_memory=true \
  -pthread \
  -sPTHREAD_POOL_SIZE=2 \
  -sMODULARIZE=1 \
  -sEXPORT_ES6=1 \
  -sEXPORT_NAME=createGate \
  -sENVIRONMENT=web,worker \
  -sALLOW_BLOCKING_ON_MAIN_THREAD=0 \
  -sASSERTIONS=1 \
  -sEXPORTED_FUNCTIONS=_main,_emdawn_device_ready,_emdawn_device_failed,_emdawn_render_status,_emdawn_recorder_status,_emdawn_recorder_local_handle \
  -sNO_EXIT_RUNTIME=1
```

To build the proxy variant, copy the Emdawn package, run
`bun run patch-emdawn-proxy.ts <copy>/webgpu/src/library_webgpu.js`, compile
against the copied port, and add `-DEMDAWN_PROXY_ENABLED=1`.

### Real Graphite gate

`graphite-emdawn-gate.cpp` covers the complete path with the Skia m152 used by
this project:

- one owner thread creates the WebGPU device, Graphite Context and presentation
  Recorder;
- two pthreads use distinct native Graphite Recorders;
- each pthread draws 20,000 circles and snaps a native Recording;
- the owner inserts both Recording objects, submits them to one canvas texture,
  and waits for `GPUQueue.onSubmittedWorkDone()`;
- the HTML reports each recorder interval, CPU overlap, handle locality and
  asynchronous device errors.

The control build without proxy failed with `status=-6`; neither recorder
pthread had the JavaScript device object. The proxy build passed in Chrome
151.0.7922.174 with both recorders at state `2`, both local handles at `-1`,
`deviceError=0`, and 44.55 ms of measured overlap. The final screenshot showed
both blue and green recordings on the WebGPU canvas.

This complete gate currently uses Emscripten 4.0.7's legacy
`-sUSE_WEBGPU=1`, because that is the WebGPU C++ ABI consumed by Skia m152.
`patch-emdawn-proxy.ts` supports both that legacy `libwebgpu.js` and current
Emdawn's `library_webgpu.js`. Build the control before applying the patch; then
patch the binding, add `-DEMDAWN_PROXY_ENABLED=1`, and build the proxy variant.

The canvas texture is deliberately acquired only after both recordings are
ready. Acquiring it before the recorder work lets the browser compositor
expire it before submit and correctly triggers a WebGPU validation error.

The Emdawn Futures ABI generated by Dawn `1e897275` is not source-compatible
with this Skia backend. A production Emdawn migration must update Skia and
Emdawn together; linking components from those two API generations is invalid.

## Gate

The authoritative pass criteria and fallback rules live in
`SURFACE_AND_NATIVE_THREADS.md` under "Browser pthread prototype".

The real Graphite proxy probe now covers Context creation, two native recorder
pthreads, opaque Recording handoff, deterministic insertion, presentation,
GPU queue completion, visual output, and overlap timing. The focused current
Emdawn test separately covers handle proxying against its newer ABI. Resize,
cancellation, shutdown, the 60-second bounded-load test, proxy-call timing, and
the complete metric set still have to be implemented and measured before the
production gate can pass.
