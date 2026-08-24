# Graphite API optimization results

## Implemented contract

All eight points in [`GRAPHITE_API_OPTIMIZATION_PLAN.md`](GRAPHITE_API_OPTIMIZATION_PLAN.md)
were implemented on 2026-08-24.

- `GraphiteDisplayList.build` is runtime-independent and has its own buffer
  limit. `GraphiteRuntime.createDisplayList` was removed.
- A command program owns command bytes plus a retained local resource table.
  `DrawDisplayList` contains a four-byte table index, never nested list bytes.
- Each runtime assigns monotonic 64-bit IDs and publishes each immutable
  command program once to each consuming recorder worker. Later jobs carry IDs.
- Display lists, recordings, frames, frame insertions, pending frames, and
  in-flight snapshots use explicit, idempotent retained handles.
- Direct drawing and retained drawing are documented as separate choices.
- `GraphiteSampleViewModel` owns runtime creation, scene resources, sequential
  frame recording, generation replacement, failure state, and cleanup.
- Renderer callbacks, native draw contexts, render modes, surface state, and
  the renderer-based Compose overload are no longer public API.
- Common tests exercise independent construction and limits, closed handles,
  nested-depth validation, malformed resource indices, cross-runtime reuse,
  per-worker publication caching, monotonic IDs, retained lifetimes, ViewModel
  initialization races, scene generation reuse, and cleanup.

## Payload measurements

The deterministic command-format tests establish these properties:

| Case | Result |
| --- | --- |
| Direct `drawPath` | Geometry remains inline in the root command program. |
| Display list drawn once | Root size is independent of display-list byte size. |
| 100 KiB display list | Root contains one local resource entry and a four-byte `DrawDisplayList` index; it does not contain another 100 KiB payload. |
| Repeated list draws | Every draw adds only command/scoping data; one identity occupies one local resource-table entry. |
| Nested display lists | Child programs are retained and registered child-first; 64 levels validate and level 65 is rejected. |
| First runtime use | Metrics increment `registered`, `registeredBytes`, `publications`, and `publishedBytes`. |
| Cached use | `publications` and `publishedBytes` stay unchanged while `cacheHits` increments. |
| Runtime shutdown | `released` reaches the number of registered programs without closing application-owned handles. |
| JS transfer | The worker receives and returns one transferable `Int8Array` buffer. |
| Wasm transfer | The worker protocol is identical; the documented managed-array interop copies remain. |

`GraphiteOptimizationBenchmarkTest` is a repeatable JVM microbenchmark and
format assertion. One run on the development Mac produced:

| Measurement | Result |
| --- | ---: |
| Display-list command bytes | 204,008 B |
| Root drawing that list once | 96 B |
| Root drawing it 100 times | 8,808 B |
| First worker message | 204,148 B |
| Cached worker message | 124 B |
| Direct-path encoding average | 1,293 ns |
| Display-list draw encoding average | 3,185 ns |
| 100 display-list draws average | 36,868 ns |
| Nested-list draw encoding average | 1,182 ns |
| First runtime use | 10,117,333 ns |
| Cached runtime use | 371,916 ns |
| Validation time accumulated across both uses | 3,701,792 ns |
| Runtime preparation accumulated across both uses | 4,500,417 ns |

Timing values are comparative development measurements, not stable performance
guarantees. The byte counts are protocol assertions and are deterministic.
Encoding and validation durations remain observable through recorder metrics
and the resource metrics' preparation/validation fields; queue latency remains
observable through `totalQueueWaitNanos`/`maximumQueueWaitNanos`. Resource
payload volume and retirement are exposed through
`GraphiteMetricsSnapshot.resources`. The current evidence does not justify
adding a native `SkPicture` cache: the motivating byte copy is gone, while a
native cache would add a second platform-specific lifetime and
browser-consistency contract.

## Verification

The final verification commands and target outcomes are kept here so future
changes can repeat the same matrix:

```text
./gradlew :graphite-surface:jvmTest :sample:sharedUI:jvmTest
./gradlew :graphite-surface:compileAndroidMain \
  :graphite-surface:compileKotlinJs \
  :graphite-surface:compileKotlinWasmJs \
  :graphite-surface:compileKotlinIosArm64 \
  :graphite-surface:compileKotlinIosSimulatorArm64
./gradlew :sample:sharedUI:compileAndroidMain \
  :sample:sharedUI:compileKotlinJs \
  :sample:sharedUI:compileKotlinWasmJs \
  :sample:sharedUI:compileKotlinIosArm64 \
  :sample:sharedUI:compileKotlinIosSimulatorArm64
```

The repository exposes no Detekt, ktlint, or formatting task for these modules.
Existing included-build warnings about Skiko `compileOnly`, custom target names,
and Compose/Skiko version alignment are outside this API change.

Observed outcomes on 2026-08-24:

- JVM library and sample tests passed, including the ViewModel cancellation
  race and retained-lifetime tests.
- Android, Kotlin/JS, and Kotlin/Wasm library and shared sample targets compiled.
- JS and Wasm development distributions bundled successfully.
- The JS browser test suite passed. The Wasm Karma suite compiled but its
  runner hit the existing 30-second no-message timeout while loading; the Wasm
  application smoke test rendered and animated successfully with no console or
  application network errors.
- The shared sample's browser test task is not configured as an executable and
  Compose rejects that test task before launch. Its common tests pass on JVM,
  and the JS/Wasm application smokes cover the actual browser integration.
- The JS and Wasm smokes both created a 2560×1600 physical canvas, rendered the
  rotating triangle in two distinct frames, and reported no runtime or worker
  exception.
- iOS compilation is blocked before project Kotlin compilation by the included
  Skiko build's `patchSkikoSymbolsIosArm64` task because `llvm-objcopy` is not
  installed. The task itself reports `brew install llvm` as the prerequisite.
