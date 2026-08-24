# Graphite API optimization plan

## Status and scope

Implementation completed on 2026-08-24. This document preserves the eight
changes and their acceptance criteria as the implementation record. The
verification results are recorded in
[`GRAPHITE_API_OPTIMIZATION_RESULTS.md`](GRAPHITE_API_OPTIMIZATION_RESULTS.md).

The renderer-removal decision in sections 6 and 7 was superseded later on
2026-08-24. The current API has a user-owned `GraphiteRenderer` with
`Continuous`, `OnDemand`, and `Manual` modes; the authoritative contract is in
[`SURFACE_AND_NATIVE_THREADS.md`](SURFACE_AND_NATIVE_THREADS.md). The original
text below is retained as historical implementation context.

The sample's single-ViewModel design in section 6 was also superseded. Each
renderer demo now has its own ViewModel, runtime, scene, renderer, state, and
cleanup so the three modes can be read as independent examples.

The accepted ownership, worker, cancellation, and presentation contracts in
[`SURFACE_AND_NATIVE_THREADS.md`](SURFACE_AND_NATIVE_THREADS.md) remain
authoritative. If implementation evidence requires changing one of those
contracts, update that decision log before changing the public API.

The API follows one ownership rule:

| Value | Construction and ownership |
| --- | --- |
| Paths, paints, images, fonts, and display lists | Runtime-independent CPU values |
| Recording targets, recordings, frames, and presentation | Bound to one `GraphiteRuntime` |
| Worker caches and GPU resources | Internal, registered when a runtime first uses a CPU value |

## 1. Decouple display-list construction from the runtime

Replace this API:

```kotlin
val triangle = runtime.createDisplayList {
    drawPath(path, paint)
}
```

with:

```kotlin
val triangle = GraphiteDisplayList.build {
    drawPath(path, paint)
}
```

Add the builder to the `GraphiteDisplayList` companion object in `commonMain`:

```kotlin
public companion object {
    public fun build(
        maxCommandBufferBytes: GraphiteCommandBufferLimit =
            GraphiteCommandBufferLimit.Default,
        block: GraphiteEncoder.() -> Unit,
    ): GraphiteDisplayList
}
```

Make these changes:

- Move `GraphiteCommandWriter` and `GraphiteEncoderImpl` construction from
  `GraphiteRuntime.createDisplayList` into `GraphiteDisplayList.build`.
- Remove the runtime readiness check from display-list construction.
- Remove `GraphiteRuntime.createDisplayList`. The project is implementing the
  version 1 contract, so retaining a compatibility wrapper would preserve the
  wrong ownership model.
- Keep `GraphiteRuntimeConfig.maxCommandBufferBytes` as the limit for one
  recording command buffer only.
- Use the builder parameter as the independent limit for one display list.
- Update KDoc, the sample, tests, and the command-limit section of
  `SURFACE_AND_NATIVE_THREADS.md`.

Completion criteria:

- A display list can be built before any runtime exists and after a runtime has
  closed.
- The same display list can be recorded by two runtimes.
- Exceeding the display-list limit throws
  `GraphiteEncodingException.CommandBufferTooLarge` without consulting or
  changing runtime state.
- Drawing a closed display list fails synchronously.
- Nested-list and maximum-depth validation still pass.
- All existing call sites use `GraphiteDisplayList.build`.

This point changes ownership only. It does not change the command protocol or
claim a performance improvement.

## 2. Stop embedding display-list bytes in every recording

The current `draw(displayList)` command copies the display list's complete byte
array into each recording. Replace that representation with an internal
immutable command program:

```kotlin
internal class GraphiteCommandProgram(
    internal val commands: ByteArray,
    internal val resources: List<GraphiteCommandResource>,
)
```

Keep each new top-level type in its own Kotlin file. Make
`GraphiteDisplayList` own one command program instead of a bare `ByteArray`.

Change encoding as follows:

- Give each command program a local resource table.
- Encode `DrawDisplayList` with a resource-table index instead of nested bytes.
- Retain nested display lists in the parent program's resource table.
- Preserve the rule that only finalized display lists can be nested, which
  keeps cycles impossible.
- Validate the referenced program once when it enters a consuming runtime,
  rather than recursively validating copied bytes in every recording.
- Keep direct geometry commands inline. A one-off `drawPath` should not pay
  display-list registration costs.

Completion criteria:

- Drawing a 100 KiB display list adds a fixed-size reference to a recording,
  not another 100 KiB payload.
- Recording command-buffer limits count the root recording program and its
  resource references, not the separately bounded display-list program.
- Nested display lists execute in the same painter order and under the same
  transform and clip scopes as before.
- Malformed resource indices fail validation before drawing work begins.

## 3. Register display lists on first runtime use

Implement the runtime resource behavior already required by the architecture:

- Assign an unsigned 64-bit monotonic resource ID when a runtime first uses a
  display-list identity.
- Reuse that ID for later uses in the same runtime.
- Assign independent IDs when different runtimes use the same public display
  list.
- Register nested command programs and their immutable CPU resources before
  publishing the recording job.
- Transfer each resource payload once to each worker that consumes it. Later
  jobs carry only runtime-local IDs.
- Let recorder and render workers cache validated local representations.
- Keep every ID and cache handle internal.

Use handle identity for version 1. Content hashing and cross-runtime cache
sharing remain outside this plan.

Do not make `SkPicture` part of the public contract. Start by caching the
validated portable command program. Add a native representation only after a
benchmark shows that replay or parsing time warrants it and the browser worker
model can preserve the same behavior.

Completion criteria:

- Repeated recording with one display list publishes its payload once per
  consuming worker.
- Later jobs send only the resource ID and draw parameters.
- Metrics expose registrations, cache hits, published resource bytes, and
  released resources.
- Runtime shutdown releases every resource namespace without affecting a
  display list still owned by application code or another runtime.

## 4. Implement retained lifetimes without command copying

Resource references require real retention. Replace byte-array snapshots with
internal retained handles:

- A parent display list retains every nested closeable resource it uses.
- An admitted recording job retains every referenced resource until the job
  completes or cancellation retires it.
- A completed `GraphiteRecording` retains its resources.
- A `GraphiteFrame` retains inserted recordings instead of copying their
  command arrays.
- The pending-frame mailbox and in-flight submission retain their own handles.
- Closing the caller's handle prevents new use but cannot invalidate work
  already retained by a display list, recording, frame, or GPU submission.
- Worker cache entries retire after their final logical reference and
  dependent GPU use disappear.

Keep `close()` idempotent and thread-safe on every public closeable handle.
Make every retain and release path explicit so cancellation, latest-wins frame
replacement, failure, and shutdown each release ownership exactly once.

Completion criteria:

- A caller can close a display list after `record` admits the job without
  breaking that job.
- A caller can close a recording after inserting it into a frame without
  breaking the frame.
- A caller can close a frame after `present` accepts it without breaking the
  pending or in-flight submission.
- Replacement, cancellation, terminal failure, and normal shutdown produce no
  negative reference counts, leaked handles, or premature worker-cache
  destruction.

## 5. Simplify the recording workflow and examples

Keep direct drawing and retained drawing as two clear choices:

```kotlin
recorder.record(target) {
    drawPath(dynamicPath, paint)
}
```

```kotlin
recorder.record(target) {
    draw(staticDisplayList, transform = cameraTransform)
}
```

Document direct encoder commands for one-off or changing content. Document
display lists for immutable command groups reused across recordings,
transforms, workers, or runtimes.

Keep explicit recorder selection. Version 1 does not add an automatic
scheduler or a convenience `runtime.record` method that hides queue choice.

Completion criteria:

- Public KDoc explains which drawing path to choose and states the lifecycle of
  each returned handle.
- Direct-drawing and retained-drawing examples compile in `commonMain`.

## 6. Move the sample runtime and rendering work into a ViewModel

Add `GraphiteSampleViewModel` under the sample's `commonMain`, in its own
`GraphiteSampleViewModel.kt` file. The ViewModel owns the `GraphiteRuntime`,
animated-scene resources, recording work, and cleanup. `App.kt` observes state
and hosts `GraphiteSurface`.

Add the Compose Multiplatform lifecycle dependencies to the version catalog
and the sample's `commonMain` dependencies:

- `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`
- `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose`

Use the lifecycle version aligned with the pinned Compose Multiplatform
release. Compose Multiplatform `1.12.0-beta03` currently aligns with lifecycle
`2.11.0-beta02`.

Model initialization explicitly in a separate `GraphiteSampleUiState.kt`
file:

```kotlin
internal sealed interface GraphiteSampleUiState {
    data object Initializing : GraphiteSampleUiState
    data class Ready(val runtime: GraphiteRuntime) : GraphiteSampleUiState
    data class Failed(val error: Throwable) : GraphiteSampleUiState
}
```

Implement the lifecycle as follows:

- Create the runtime once from `viewModelScope` during ViewModel
  initialization.
- Publish initialization, ready, and failure through a read-only `StateFlow`.
- Keep the mutable runtime reference private to the ViewModel. The ready UI
  state exposes the same owned runtime only so `GraphiteSurface` can attach it.
- Close the runtime and the current display list from `onCleared()`.
- Handle cancellation racing with `GraphiteRuntime.create()`. If creation
  completes after the ViewModel has cleared or its initialization coroutine has
  cancelled, close that new runtime instead of publishing it.
- Move presentation-generation tracking, display-list creation, recording
  target reuse, recording, frame construction, and presentation into a
  sequential suspend function on the ViewModel.
- Build the triangle display list and recording target once per presentation
  generation. Replace and close them when the generation changes.
- Keep terminal runtime failures in the UI state or the runtime's existing
  observable state. Do not leave an exception uncaught in `viewModelScope`.

`withFrameNanos` must remain in the composition because `viewModelScope` does
not own Compose's `MonotonicFrameClock`. Reduce `App.kt` to one frame-driving
effect:

```kotlin
@Composable
public fun App() {
    val viewModel = viewModel { GraphiteSampleViewModel() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        GraphiteSampleUiState.Initializing -> Unit
        is GraphiteSampleUiState.Failed -> Unit
        is GraphiteSampleUiState.Ready -> {
            GraphiteSurface(
                runtime = state.runtime,
                modifier = Modifier.fillMaxSize(),
            )
            LaunchedEffect(viewModel, state.runtime) {
                while (isActive) {
                    val frameTimeNanos = withFrameNanos { it }
                    viewModel.renderFrame(frameTimeNanos)
                }
            }
        }
    }
}
```

Do not launch one coroutine per frame. The single effect calls the suspend
render method sequentially, so recording backpressure remains visible and
cancelable.

Completion criteria:

- `App.kt` contains no `mutableStateOf<GraphiteRuntime?>`, runtime creation, or
  runtime cleanup.
- `App.kt` contains exactly one `LaunchedEffect` and no `DisposableEffect`.
- Removing and recreating the composable under the same ViewModel owner does
  not recreate the runtime.
- Clearing the ViewModel cancels active frame work and calls `close()` exactly
  once on every sample-owned display list and runtime.
- A presentation generation change rebuilds the target-sized triangle once,
  not once per frame.
- The sample demonstrates why a display list exists without implying that all
  drawing requires one.
- Common tests cover successful initialization, initialization failure, the
  cancellation race, generation replacement, and `onCleared()` cleanup.
- Android, JVM, Apple, JavaScript, and Wasm sample targets compile with the
  common ViewModel implementation.

## 7. Remove obsolete renderer-controlled public APIs

Remove the public APIs rejected by `SURFACE_AND_NATIVE_THREADS.md`:

- `GraphiteRenderer`
- `GraphiteDrawContext`
- `GraphiteRenderMode`
- `GraphiteSurfaceState`
- `rememberGraphiteSurfaceState`
- the renderer-based `GraphiteSurface` overload

Leave this as the public Compose entry point:

```kotlin
GraphiteSurface(
    runtime = runtime,
    modifier = Modifier,
)
```

Move the platform presentation bridge into the internal-facing package. If a
cross-module declaration must remain technically public, annotate it with
`@InternalGraphiteApi` at `RequiresOptIn.Level.ERROR` and expose only opaque
Graphite-owned types.

Completion criteria:

- Application code cannot provide a render callback or access the native draw
  context.
- The Compose module owns attachment only. The user-owned runtime owns queues,
  recordings, frames, state, metrics, and logging.
- Public API validation finds none of the removed renderer-controlled types.
- No public declaration exposes Skia, Skiko, native pointers, WebGPU handles,
  canvases, GPU surfaces, or platform backend objects.

## 8. Benchmark and verify every target

Measure these cases before and after points 2 through 4:

- direct `drawPath` encoding;
- one display list drawn once per frame;
- one display list drawn hundreds of times in one recording;
- nested display lists;
- first-use runtime registration;
- cached display-list use;
- worker transfer bytes;
- cancellation and resource retirement under sustained load.

Report encoded bytes, copied bytes, encoding time, validation time, first-use
registration time, cache-hit count, and resource-retirement count. Use the
measurements to decide whether a native cached representation is worth adding.

Run common tests and compile every supported KMP target. Run the repository's
formatting and static-analysis tasks when present. Repeat the existing browser
smoke validation for both Kotlin/JS and Kotlin/Wasm because their transfer
paths differ.

Final completion criteria:

- Display lists require no runtime to build.
- Repeated display-list draws do not copy nested command bytes.
- Each consuming worker receives a display-list payload at most once per
  runtime registration.
- Cross-runtime reuse works with independent resource IDs.
- Closing public handles cannot invalidate retained work.
- JS and Wasm preserve their documented transferable-buffer behavior.
- Common tests pass and every configured KMP target compiles.
- The sample runtime is owned and closed by `GraphiteSampleViewModel`, while
  Compose owns only attachment and frame-clock delivery.
- The exported API and `SURFACE_AND_NATIVE_THREADS.md` describe the same
  ownership and lifecycle contracts.

## Non-goals

This plan does not add a scene graph, generic scheduler, automatic device-loss
recovery, stable display-list serialization, content-addressed resources, or a
public Skia representation. Each would introduce a separate contract and needs
its own evidence and design decision.
