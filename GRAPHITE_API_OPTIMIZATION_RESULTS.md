# Graphite API optimization results

## Implemented contract

All eight points in [`GRAPHITE_API_OPTIMIZATION_PLAN.md`](GRAPHITE_API_OPTIMIZATION_PLAN.md)
were implemented on 2026-08-24.

- `graphiteDisplayList` is runtime-independent. Command-buffer limits are
  internal. `GraphiteEngine.createDisplayList` was removed.
- A command program owns command bytes plus an immutable local resource table.
  `DrawDisplayList` contains a four-byte table index, never nested list bytes.
- Each runtime assigns monotonic 64-bit IDs and publishes each immutable
  command program once to each consuming recorder worker. Later jobs carry IDs.
- Display lists and recordings are garbage-collected immutable command graphs.
  Internal frames, pending frames, and in-flight snapshots retain their command
  programs while asynchronous work needs them.
- Direct drawing and retained drawing are documented as separate choices.
- The continuous, on-demand, and manual sample screens each have their own
  ViewModel. Every ViewModel owns its runtime creation, scene resources,
  stable renderer, frame recording, reactive error, and cleanup.
- Native draw contexts and surface state are no longer public API. A later
  revision restored a user-owned renderer-based Compose overload with
  `Continuous`, `OnDemand`, and `Manual` modes, without exposing platform
  drawing objects.
- Common tests exercise independent construction and limits, display-list reuse,
  nested-depth validation, malformed resource indices, cross-runtime reuse,
  per-worker publication caching, monotonic IDs, retained lifetimes, runtime
  constructor validation, scene generation reuse, and per-ViewModel cleanup.

## Renderer scheduling revision

The later renderer revision keeps asynchronous recording and explicit
presentation. Render modes are immutable. `GraphiteRendererTest` covers manual
attachment checks, on-demand request conflation, mode validation, callback
serialization, and discarding scheduled work after mode or presentation
generation mismatches. Continuous and on-demand scheduling live in
`GraphiteSurface`; the manual sample owns its frame-driving `LaunchedEffect`.

The JVM library and sample tests, the public dependency-boundary check, and the
Android, JS, Wasm, iOS device, and iOS simulator compilation matrix passed for
this revision.

## Payload measurements

The deterministic command-format tests establish these properties:

| Case | Result |
| --- | --- |
| Direct `drawPath` | Geometry remains inline in the root command program. |
| Display list drawn once | Root size is independent of display-list byte size. |
| 100 KiB display list | Root contains one local resource entry and a four-byte `DrawDisplayList` index; it does not contain another 100 KiB payload. |
| Repeated list draws | Every draw adds only command/scoping data; one identity occupies one local resource-table entry. |
| Nested display lists | Child programs are strongly referenced and registered child-first; 64 levels validate and level 65 is rejected. |
| First runtime use | Metrics increment `registered`, `registeredBytes`, `publications`, and `publishedBytes`. |
| Cached use | `publications` and `publishedBytes` stay unchanged while `cacheHits` increments. |
| Runtime shutdown | `released` reaches the number of registered programs without closing application-owned handles. |
| JS transfer | The worker receives and returns one transferable `Int8Array` buffer. |
| Wasm transfer | The worker protocol is identical; the documented managed-array interop copies remain. |

`GraphiteOptimizationBenchmarkTest` is a repeatable JVM microbenchmark and
format assertion. One run on the development Mac produced:

| Measurement | Result |
| --- | ---: |
| Display-list command bytes | 340,008 B |
| Root drawing that list once | 17 B |
| Root drawing it 100 times | 908 B |
| First worker message | 340,069 B |
| Cached worker message | 45 B |
| Direct-path encoding average | 2,357 ns |
| Display-list draw encoding average | 1,003 ns |
| 100 display-list draws average | 9,355 ns |
| Nested-list draw encoding average | 5,126 ns |
| First runtime use | 9,253,916 ns |
| Cached runtime use | 454,750 ns |
| Validation time accumulated across both uses | 3,602,334 ns |
| Runtime preparation accumulated across both uses | 4,560,042 ns |

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

- JVM library and sample tests passed, including runtime constructor validation,
  per-ViewModel cleanup, and retained-lifetime tests.
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
- iOS simulator compilation, framework linking, and both native test suites
  passed after supplying the Android NDK's `llvm-objcopy` to the Skiko symbol
  patch task. The current Kotlin/Native POSIX bindings also required the
  monotonic-clock compatibility adjustment in `PlatformTime.ios.kt`.
- The JVM sample launched and animated successfully after adding the Swing
  implementation of `Dispatchers.Main` to the desktop application.
- The Android APK assembled and installed, but its process crashes while
  loading `libskiko-android-arm64.so`: the binary references the unavailable
  partition-allocator symbol
  `_ZN15partition_alloc8internal21PartitionAddressSpace6setup_E`.
- The iOS simulator app built, installed, launched, and continued producing
  sample and Metal render frames without a logged application fault, but its
  captured output remained entirely white. The iOS visual smoke therefore did
  not pass.
