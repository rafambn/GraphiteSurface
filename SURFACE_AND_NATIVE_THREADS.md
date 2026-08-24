# Graphite runtime, surface, and worker architecture

## Status and purpose

This is the authoritative decision log for the current GraphiteSurface
architecture session. Future design and implementation work must start here
instead of reconstructing the decisions from chat history.

The architecture was explicitly approved for implementation on 2026-08-22.
Items marked **accepted** remain the intended version 1 contract. The
implementation may use a documented platform adaptation when a backend makes
the literal topology impossible, but it must preserve the observable queue,
ordering, cancellation, ownership, and failure semantics.

## Implementation checkpoint (2026-08-22)

- `GraphiteRuntime`, explicit recorder handles, bounded suspending FIFO
  admission, portable immutable command buffers, display lists, recordings,
  frames, latest-wins presentation, state flows, events, metrics, and Scribe
  integration now exist in the common API.
- Android presentation remains on its dedicated `HandlerThread`; JVM uses a
  dedicated single native render thread; Apple uses a dedicated serial native
  dispatch queue; browser presentation transfers the `OffscreenCanvas` to one
  dedicated module Web Worker.
- The browser render Worker owns the WebGPU adapter/device, Emdawn handle
  registry, Skia Graphite Context, presentation Recorder, swapchain textures,
  command interpretation, submission, and cleanup. No Skia or WebGPU draw call
  runs on the browser main thread.
- User recorder workers currently validate and publish the portable command
  program asynchronously. `GraphiteRecording` therefore contains a validated
  immutable command program in this first working slice, and the render worker
  replays it into its private presentation Recorder. It is not yet a native
  `skgpu::graphite::Recording` created by each public worker.
- This adaptation is necessary in the browser because the measured Emdawn m152
  JavaScript WebGPU handle registry is worker-local. It keeps the public
  semantics common and avoids a main-thread fallback, at the cost of moving
  Skia recording itself to the render Worker on Web.
- Native targets deliberately use the same command-program behavior for this
  slice. A future native optimization may materialize true deferred Graphite
  Recordings on recorder threads without changing public API or browser
  behavior.
- Browser recorder jobs use transferable `Int8Array` messages. Kotlin/JS
  transfers its `ByteArray` buffer out and back without hexadecimal expansion.
  Kotlin/Wasm performs one copy on each side of the JavaScript interop boundary
  because its managed array cannot itself become a transferable JS buffer.
- The presentation worker uses a one-active, one-pending latest-wins pump and
  waits for `GPUQueue.onSubmittedWorkDone()` before starting its next frame.
  A small Skiko Graphite binding reports completion into the Context before
  normal destruction, so shutdown does not leave Graphite submissions pending.
- The Compose attachment currently owns the lifetime of its platform render
  worker. The user-owned runtime survives detachment and owns recorder queues,
  state, frames, and logging. This is the implemented hybrid from Q17; moving
  the presentation worker object into a later standalone runtime module must
  not change observable behavior.
- Cache limits are validated configuration placeholders in this slice. They
  are not enforced and `trimGpuCaches()` is not exposed. Metrics now report
  portable-resource registrations, publications, bytes, cache hits, and
  releases; native GPU-cache, submission, and GPU-completion metrics remain
  future work.
- Display lists are built without a runtime. Recordings store fixed-size local
  resource-table indices, runtimes assign their own monotonic resource IDs on
  first use, and each recorder worker receives a resource payload only once.
  Display lists, recordings, frames, and pending snapshots use retained
  closeable handles instead of copying nested command arrays.
- The implemented encoder covers transforms, clips, reusable display lists,
  rectangles, ovals, lines, paths, and basic fill/stroke paint. Images,
  prepared glyph runs, gradients, effects, and blends remain work after this
  first usable runtime rather than silently accepted no-ops.
- `GraphiteRuntime.create()` starts recorder workers but does not probe WebGPU
  while detached. Browser capability failure is currently reported through
  terminal runtime state when `GraphiteSurface` attaches.
- Scribe 0.7.0 is consumed from Maven Central for all requested targets.
- Chrome for Testing 152.0.7977.54 validates both JS and Wasm builds with two
  recorder Workers and one render Worker. The animated sample presents through
  Graphite, canvas resize updates the physical backing size, animation produces
  distinct screenshots, and no runtime or worker exception appears. Headless
  SwiftShader cannot create the required WebGPU swapchain in this environment,
  so the GPU validation used a windowed Chrome session and the installed GPU.

## Objectives

- Expose one Kotlin Multiplatform API with the same execution semantics on
  Android, JVM desktop, Apple, JavaScript, and Wasm.
- Keep all Skia Graphite ownership and presentation work away from the UI
  thread.
- Use real native threads on native targets and real Web Workers in browsers.
- Provide enough low-level infrastructure for advanced users while keeping the
  map-specific scene and scheduling policy in KMaP.
- Make resource ownership, backpressure, cancellation, resize, and device-loss
  behavior explicit.

## Terminology

- **Presentation target**: the platform-owned destination where a rendered
  image is shown: Android surface, Apple drawable/layer, desktop window, or
  browser canvas.
- **GraphiteSurface**: the Compose host that owns attachment to that platform
  presentation target. It does not own the runtime.
- **GraphiteRuntime**: the user-owned engine instance. It owns recorder
  workers, recorder queues, published recordings, frame admission, state,
  metrics, and logging. The attached Compose surface currently owns the
  platform render worker and Graphite Context; detaching it does not destroy
  the runtime.
- **Recorder worker**: a dedicated native thread or Web Worker that validates
  and publishes one immutable command program at a time. Materializing native
  deferred Graphite Recordings here is a future native optimization, not a
  version 1 observable guarantee.
- **Render worker**: the dedicated native thread, serial dispatch queue, or Web
  Worker that owns presentation-side Graphite objects, interprets published
  command programs, submits GPU work, presents, and retires resources.
- **GraphiteDisplayList**: an immutable, backend-independent, reusable CPU
  description of drawing commands.
- **GraphiteRecording**: an immutable, closeable, runtime-bound command result
  produced by a recorder.
- **GraphiteFrame**: an immutable, closeable ordered collection of completed
  recordings for one presentation generation.

## Public module boundaries

**Accepted**

The current dependency graph mixes the Compose host and the future runtime in
`:graphite-surface`:

```text
:sample -> :sharedUI -> :graphite-surface
                         -> :graphite-engine
                            -> skiko-graphite Android
                         -> GraphiteEngine.framework on Apple
```

Version 1 uses this split:

```text
:graphite-surface -> api(:graphite-runtime)
:graphite-runtime -> platform engine implementations
```

- `:graphite-runtime` contains the public non-Compose runtime, recorder,
  encoder, target, display-list, recording, frame, resource, event, metrics,
  and exception APIs.
- `:graphite-surface` contains only the Compose presentation adapter and
  Compose-specific conversions.
- The platform engines and the local Skiko fork remain implementation
  details. Public API would expose neither Skia nor native handles.
- Public declarations remain in the
  `com.rafambn.graphitesurface` package even though they live in two Gradle
  modules.
- The current boundary verification that prevents Skia/Skiko leaking through
  the Compose adapter should be retained and extended to the runtime API.

The cross-module presentation bridge is technically public because Kotlin
`internal` cannot cross a published Gradle module boundary. Every bridge
declaration:

- lives in an internal-facing package;
- is annotated with `@InternalGraphiteApi`, whose
  `@RequiresOptIn` level is `ERROR`;
- uses only opaque Graphite-owned types and exposes no Skia object, native
  pointer, or platform handle;
- carries no source or binary compatibility promise.

`:graphite-surface` opts in explicitly. Application code may also opt in, but
does so with the compatibility warning enforced by the compiler.

The runtime creates and implements the platform presentation host. The Compose
module only embeds its platform container:

```kotlin
@InternalGraphiteApi
public interface GraphitePresentationHost : AutoCloseable {
    public fun attach()
    public fun detach()
    override public fun close()
}
```

- Android hosts expose a `View`, JVM hosts a `Component`, Apple hosts a
  `UIView`, and browser hosts an `HTMLElement` through platform-specific
  opt-in bridge types.
- These containers are the only platform UI objects crossing the module
  boundary. GPU surfaces, layers, contexts, canvases, `OffscreenCanvas`, and
  native pointers remain internal.
- The common bridge has no `resize()` method. Each runtime-owned host observes
  the actual drawable or buffer dimensions and density from its platform
  callbacks. The Compose layout size is not treated as the presentation size.
- `attach()` and `detach()` are idempotent. `close()` detaches permanently and
  releases the host and its observers without closing the runtime.

## Public ownership and surface attachment

**Accepted**

- The user creates and owns the runtime:

  ```kotlin
  val runtime = GraphiteRuntime.create(config)
  ```

  Creation is suspending and completes only when initialization is ready. It
  throws a typed initialization exception if initialization fails.

- Compose owns only presentation attachment:

  ```kotlin
  GraphiteSurface(
      runtime = runtime,
      modifier = Modifier,
  )
  ```

- A runtime may exist without a surface and may create recordings for local
  targets while detached. Display lists are runtime-independent CPU values and
  may be built before a runtime exists or after one has closed.
- Version 1 permits at most one attached presentation target per runtime. A
  second simultaneous attachment is rejected. Recreating the same host target
  after resize or lifecycle changes is supported.
- A process or browser page may own multiple runtimes simultaneously. Each
  runtime has its own Context, workers, queues, resource namespace, state,
  metrics, and optional presentation target.
- Version 1 does not share workers, recordings, or registered resources across
  runtimes. Browser runtimes may share the loaded Emscripten module and Wasm
  heap, but their opaque handle namespaces remain separate.
- Resource exhaustion while creating another runtime fails that creation with
  `GraphiteInitializationException`; it does not impose a global singleton.
- The public API does not expose `GraphiteRenderer`, `GraphiteDrawContext`,
  `GraphiteOutputMode`, `GraphiteRenderMode`, `GraphiteSurfaceState`, or a
  renderer-controlled `GraphiteSurface` overload. These presentation bridge
  types are internal implementation details.
- Version 1 has no continuous mode. Calling `present(frame)` is the explicit
  render request.

## Runtime lifecycle

**Accepted**

- The public lifecycle state is observable:

  ```kotlin
  public val state: StateFlow<GraphiteRuntimeState>
  public val presentation: StateFlow<GraphitePresentationState>
  ```

- Runtime states are `Ready`, `DeviceLost`, `Failed`, `Closing`, and
  `Closed`. Absence of a presentation target is not a runtime failure.
- Device loss is terminal for a runtime in version 1. Display lists remain
  usable because they are backend-independent; recordings become invalid. The
  application creates a new runtime.
- Unexpected native-thread or Web Worker termination and internal invariant
  violations move the complete runtime to terminal `Failed`. Pending calls
  fail, the runtime shuts down its remaining workers, and recordings from that
  runtime become unusable. Runtime-independent CPU resources remain usable.
- `DeviceLost(error)` and `Failed(error)` remain the final observable
  states after automatic cleanup. `awaitClosed()` indicates that cleanup has
  completed. Calling `close()` remains idempotent and does not erase the
  terminal cause by replacing either state with `Closed`.
- `Closed` represents normal user-requested shutdown.
- `close()` is thread-safe, idempotent, and non-blocking.
- `awaitClosed()` waits for shutdown.
- `awaitIdle()` exists for tests, diagnostics, and readback workflows, not for
  the normal frame loop.
- Normal shutdown proceeds in this order:
  1. new `record()` calls are rejected;
  2. `present()` returns `RuntimeUnavailable`;
  3. queued recording calls resume with `GraphiteRuntimeClosedException`;
  4. running recordings receive cooperative cancellation and their results are
     discarded;
  5. pending frames are discarded;
  6. in-flight GPU work finishes or is abandoned according to backend
     guarantees;
  7. native caches and resources are freed on their owning workers;
  8. Scribe drains and closes last;
  9. `awaitClosed()` completes.
- Shutdown never forcefully terminates a native thread while it is inside a
  Skia call. A caller may apply its own timeout while awaiting closure, but
  cleanup continues after that caller stops waiting.

## Native execution model

**Accepted**

- Android uses native JVM threads, such as `Thread` or `HandlerThread`.
- JVM desktop uses native JVM threads.
- Apple targets use native platform threads, such as pthread/NSThread.
- Browser targets use real Web Workers.
- Coroutines are allowed at the public boundary for waiting and cancellation.
  They never substitute for recorder or render execution and therefore do not
  silently run that work on the browser main thread.
- The runtime owns exactly one render worker.
- The runtime owns a fixed, user-configurable number of recorder workers.
  `recorderCount` defaults to 1.
- Each recorder worker owns exactly one native Skia Recorder for its entire
  lifetime. The Recorder is never called concurrently and never migrates
  between workers.
- The public module may depend on `kotlinx-coroutines-core` to implement
  cancellation-aware suspension. Internal worker and message queues remain
  platform-native.
- No generic task scheduler, affinity abstraction, priority system, or load
  balancer is part of version 1. Advanced applications select a recorder
  explicitly, for example `runtime.recorders[index]`.
- Initial public configuration is intentionally small:

  ```kotlin
  GraphiteRuntimeConfig(
      recorderCount = 1,
      recorderQueueCapacity = 1,
      maxFramesInFlight = 2,
      gpuCache = GraphiteGpuCacheConfig.Default,
      maxCommandBufferBytes = GraphiteCommandBufferLimit.Default,
      archivist = null,
  )
  ```

- Configuration validates `recorderCount` in `1..64`,
  `recorderQueueCapacity` in `1..1024`, `maxFramesInFlight` in `1..8`, and
  byte limits as positive values.
- The pending-frame queue remains a fixed one-slot `ReplaceOldest` mailbox; it
  is not another public capacity setting.
- If any worker fails during `create()`, the runtime closes every worker that
  was already created and throws `GraphiteInitializationException`.
- Worker priority, CPU affinity, thread names, and automatic load balancing are
  not public version 1 configuration.

### Context and Recorder creation

**Accepted**

- The render worker creates the backend and Graphite Context first.
- The render worker owns one private presentation Recorder. It creates and
  updates presentation surfaces, clears the frame target, and records any
  render-worker-owned presentation commands.
- The presentation Recorder is infrastructure. It is not exposed through
  `runtime.recorders`, does not count toward `recorderCount`, and never runs
  user recording programs.
- While it exclusively owns the Context, it calls `Context::makeRecorder()`
  once for every configured recorder.
- It transfers each `std::unique_ptr<Recorder>` to one recorder worker before
  the Recorder's first use. The object stays at the same shared-heap address;
  only ownership moves.
- After transfer, the render worker never accesses that Recorder. Its recorder
  worker becomes the exclusive user and destroys it during shutdown.
- The Context and every call to it remain owned by the render worker.
  `SharedContext` is never accessed or exposed by GraphiteSurface.
- `ContextOptions.fClientWillExternallySynchronizeAllThreads` remains `false`
  because Context and Recorders execute concurrently.
- Each Recorder owns a separate image provider unless a future provider is
  explicitly thread-safe.
- `GraphiteRuntime.create()` returns only after the Context and every Recorder
  worker report readiness. The prototype includes a debug test for the
  supported sequential ownership transfer across pthreads.

## Recorder API, queues, and cancellation

**Accepted**

- Recording has only the suspending form:

  ```kotlin
  suspend fun GraphiteRecorder.record(
      target: GraphiteRecordingTarget,
      block: GraphiteEncoder.() -> Unit,
  ): GraphiteRecording
  ```

- A call first waits for capacity in that recorder's bounded FIFO queue, then
  waits for the recording result.
- Default pending capacity is 1 per recorder and is configurable.
- A full queue suspends the caller instead of dropping work or throwing.
- Calls submitted to the same recorder execute in FIFO order. Different
  recorder workers execute in parallel.
- The encoder lambda runs on the caller after queue admission and produces a
  portable command message. Arbitrary Kotlin functions and captured object
  graphs never cross a Web Worker boundary.
- Expensive tile decoding, geometry preparation, text shaping, and similar CPU
  preparation must not occur inside the encoder lambda.
- Cancellation is cooperative:
  - queued work is removed where possible;
  - running work checks safe cancellation points and discards its result;
  - native Skia threads are never forcefully terminated;
  - if completion races with cancellation, the undelivered recording is closed
    automatically.
- Cancellation remains `CancellationException`. Graphite failures use a
  typed `GraphiteException` hierarchy covering initialization, closed
  runtime, device loss, presentation mismatch, and recording failures.
- Native recorders are reusable/unordered. The ordered-recorder mode is removed
  from version 1. It may only return after profiling proves that atlas reuse or
  another measurable benefit outweighs its conflict with latest-wins work.
- `runtime.recorders: List<GraphiteRecorder>` is an immutable list whose
  recorder handles and indices remain stable for the runtime's lifetime.
- Each handle has its own queue and metrics, and submissions are thread-safe.
- A recorder cannot be closed, restarted, or replaced independently. Its
  lifecycle belongs to the runtime.

## Coordinate and transform model

**Accepted**

- Target dimensions are integer physical pixels.
- Encoder coordinates are local `Float` units. They are not implicitly dp and
  are not necessarily pixels.
- Density is explicit metadata and helper conversion APIs may be supplied.
- `GraphiteTransform` is a library-owned 4x4 transform representation with
  convenient 2D constructors and perspective support.
- A scoped transform affects only its scope:

  ```kotlin
  encoder.draw(displayList, transform = cameraMatrix)
  encoder.withTransform(cameraMatrix) {
      draw(displayList)
      draw(path, paint)
  }
  ```

- A transformed path is geometrically transformed by the matrix. Perspective
  can distort it; a normal affine camera matrix translates, rotates, scales, or
  skews it.

## Display lists

**Accepted**

- `GraphiteDisplayList` is public, immutable, closeable,
  backend-independent, and transferable between workers and runtimes.
- It is constructed with `GraphiteDisplayList.build { ... }`; construction
  does not consult runtime state. Its builder has an independent command-buffer
  limit.
- Its internal representation is opaque and has no stable serialized format in
  version 1.
- Direct recording and reusable display-list creation use the same drawing DSL.
- Direct encoder commands are the normal choice for one-off or changing
  content. Display lists are for immutable command groups reused across
  recordings, transforms, workers, or runtimes.
- A finalized display list may contain other finalized display lists. Cycles
  are impossible because only finalized immutable lists can be nested.
- Display-list values are immutable. Replay-time variation is limited to
  transform and clip in version 1; typed parameter slots are deferred.
- Caller-owned source objects may be mutated or discarded after list
  construction. The library snapshots required CPU data and internally may use
  copy-on-write, reference counting, interning, or deduplication.
- Version 1 snapshots CPU-backed resources:
  - paths;
  - paints and gradients;
  - image bytes or pixels;
  - font data;
  - prepared text layouts and glyph runs.
- External textures, mutable images, video frames, and other externally owned
  GPU resources are deferred because they require explicit lifetime and
  synchronization contracts.

### SkPicture research note

`SkPicture` is a candidate internal implementation technique, never a public
contract. It can record Canvas operations and replay them under a matrix before
`Recorder.snap()`, which fits reusable scene fragments.

Before adopting it, the browser prototype must validate:

- serialization or transfer between Web Workers;
- shared-memory behavior;
- embedded font and image resource ownership;
- lifecycle and close behavior;
- replay parity in the selected Skia Graphite/Canvas path.

No Skia or Skiko fork will be changed merely to make
`insertRecording(recording, arbitraryMatrix)` possible. Arbitrary transforms
belong at display-list replay time. A recording is already snapped GPU work.

## Recordings

**Accepted**

- A runtime establishes one non-mipmapped SDR sRGB target profile with a fixed
  sample count and backend-specific compatibility information.
- Version 1 fixes the sample count at 1.
- `runtime.createRecordingTarget(pixelSize)` creates an immutable,
  runtime-bound logical target and works while no presentation surface is
  attached.
- Recording-target creation is synchronous. The target is a cheap reusable
  descriptor, creates no native resource, and does not require `close()`.
- Target creation validates positive dimensions and requires runtime state
  `Ready`. Other runtime states throw
  `GraphiteRuntimeUnavailableException`.
- A recording target carries logical pixel dimensions and the runtime target
  profile. It does not refer to a concrete platform surface.
- `GraphiteRecording` is public, immutable, closeable, and bound to the
  runtime/device that created it.
- A different runtime rejects it.
- A frame and in-flight GPU submission retain their own safe references; the
  caller may close its handle without invalidating retained use.
- A recording may be inserted or replayed more than once, subject to backend
  prototype validation.
- After snapping, only integer target translation and clip are supported.
  Arbitrary scale, rotation, skew, or perspective must be applied while
  replaying a display list into a recorder.
- A recording carries its logical target metadata and compatibility profile.
  It is not bound to a presentation generation.
- Resize does not automatically throw away or fail an old-generation
  recording. The user may skip it, rebuild it, or reuse it in a new-generation
  frame when technically compatible.
- Each `record()` call creates exactly one deferred canvas, interprets one
  command buffer, and calls `snap()`.
- Recorder workers do not expose persistent canvases and never mix two public
  recording jobs in one native Recording.
- A presentation attachment must satisfy the runtime target profile. An
  incompatible attachment fails while the runtime remains `Ready` and
  detached.

### Deferred-target composition research

Skia Graphite's `Recorder.makeDeferredCanvas(imageInfo, textureInfo)` is the
mechanism that fits parallel recording for one presentation surface:

1. Each recorder worker creates one deferred canvas with logical dimensions,
   color information, and compatible texture information.
2. The worker draws and calls `snap()`.
3. The render worker inserts the resulting recordings into one concrete
   presentation surface in painter order.
4. One `Context.submit()` submits the complete frame.

A deferred recording is not bound to one concrete texture. It remains bound to
its runtime/backend and to a compatibility contract covering color type, alpha
type, color space, backend format, sample count, mipmapping, and protection.
Version 1 should use non-mipmapped deferred presentation recordings.

The replay API supports repeated insertion, integer translation, and integer
clip. It does not accept an arbitrary matrix. The render worker inserts an
explicit full-target clear before the frame's recordings.

The current Skiko fork does not expose the required API. Internal bindings are
needed for:

- `Recorder.makeDeferredCanvas()`;
- presentation `TextureInfo`;
- `Context.insertRecording()` with target surface, translation, and clip;
- insertion status;
- completion callbacks;
- unordered Context and Recorder options.

The fork currently forces ordered recordings. That setting conflicts with the
accepted reusable-recording model and must change to unordered.

Primary and local references:

- <https://skia.googlesource.com/skia.git/+/refs/heads/main/include/gpu/graphite/Recorder.h>
- <https://skia.googlesource.com/skia.git/+/refs/heads/main/tests/graphite/RecordingSurfacesTest.cpp>
- <https://skia.googlesource.com/skia.git/+/refs/heads/main/tests/graphite/RecordingOrderTest.cpp>
- `skiko-fork/skiko/skiko/skiko-graphite/src/commonMain/kotlin/org/jetbrains/skia/gpu/graphite/GraphiteContext.kt`

## Frames and presentation

**Accepted**

- `GraphiteFrame` is public, immutable, closeable, and built synchronously
  from already completed recordings.
- Frames are created with:

  ```kotlin
  runtime.createFrame(
      presentation = presentationInfo,
      clearColor = GraphiteColor.Transparent,
  ) {
      insert(recording, translation, clip)
  }
  ```

- The builder accepts zero recordings to create a clear-only frame.
- It validates runtime identity, target-profile compatibility, closed handles,
  integer translation, and integer clip.
- The builder does not require the presentation generation to remain current
  while building. `present()` performs the definitive generation check.
- A frame retains its recordings.
- Recording insertion order is visual order.
- The same recording may be inserted more than once with integer translation
  and clip.
- A frame is tied to one presentation generation. Presenting the frame directly
  to a different generation is rejected.
- Version 1 always clears the target before drawing a frame.
- Presentation is immediate and non-suspending:

  ```kotlin
  val result: GraphitePresentResult = runtime.present(frame)
  ```

- The result distinguishes at least `Accepted`, `ReplacedPending`,
  `NoPresentation`, `StalePresentation`, and `RuntimeUnavailable`.
- `present` does not consume the caller's frame reference and does not wait
  for GPU completion.
- `Accepted` means only that the frame entered the runtime queue. Later
  insertion, submission, or GPU-completion failure is observable through
  runtime state, `GraphiteEvent`, and Scribe.
- Version 1 has no per-frame completion callback or mutable frame status.
- With no attached surface, `present` immediately returns
  `NoPresentation`; it does not accumulate a hidden backlog.
- The default pending-frame capacity is 1 with latest-wins
  (`ReplaceOldest`) behavior.
- `maxFramesInFlight` is configurable and defaults to 2.
- At the in-flight limit, only the newest pending frame is retained.
- The runtime does not retain the last completed frame.
- All finalized public handles and runtime entry points are thread-safe.
  Builders and encoders are confined to their documented call scope.

## Surface lifecycle and presentation metadata

**Accepted**

- Presentation state is:
  - `Detached`;
  - `Attaching`;
  - `Attached(info)`;
  - `Failed(error)`.
- Presentation failure does not change a `Ready` runtime into runtime
  `Failed`.
- A new attachment attempt moves presentation state from `Failed` to
  `Attaching`.
- A second `GraphiteSurface` attachment does not disturb the already attached
  surface. The runtime rejects it, emits
  `GraphiteEvent.PresentationAttachRejected`, and writes one structured log.
  The rejected composable does not wait to acquire ownership later.
- Presentation metadata contains at least:
  - physical pixel size;
  - density;
  - monotonically changing generation.
- Each host receives an internal monotonically increasing `attachmentId`.
  Delayed callbacks from any older attachment ID are ignored.
- Presentation generation and attachment identity are separate. Generation is
  public metadata; attachment ID remains internal and may appear only in
  diagnostics.
- Initial attachment, physical-pixel resize, density change, and target
  recreation each create a new monotonically increasing generation. Target
  recreation changes it even if size and density stay equal.
- Repeated callbacks with the same target, size, and density do not change the
  generation.
- `GraphitePresentationInfo` is an immutable class with an internal constructor,
  not a data class. Alongside its public generation it carries an internal
  runtime validation token. Frame creation validates the token instead of
  trusting a user-supplied generation number.
- Resize does not recreate the runtime, Graphite Context, worker pool, display
  lists, or unrelated local recordings.
- An old frame is stale after a generation change. An old recording is not
  automatically invalidated solely because of resize; the application decides
  whether it remains useful.
- During target recreation, presentation moves to `Attaching`, pending frames
  from the old generation are discarded, and `present()` returns
  `NoPresentation`.
- Already submitted GPU work finishes or is abandoned according to backend
  guarantees.
- Successful recreation publishes `Attached` with a new generation while
  preserving the runtime, workers, display lists, and compatible recordings.
- Compose may replace the underlying canvas or native view. It first detaches
  the old presentation target and then attaches the replacement. Only
  target-bound presentation resources are recreated; the Context, worker
  group, resource registry, display lists, and compatible recordings survive.
- A backend that resizes its existing target safely may publish the new
  generation without an intermediate `Attaching` state.
- A Compose layout with zero width or height owns no presentation target and
  uses `Detached`, not `Failed`.
- Transition from zero to positive size starts attachment. Transition back to
  zero discards pending frames for the old generation and detaches while
  preserving a `Ready` runtime.

## Native failure research

The current Skiko bridge discards failure information needed by this
architecture:

- `Context.insertRecording()` returns an `InsertStatus`, but both native
  bridges discard it.
- `Context.submit()` returns a Boolean, but the Kotlin API exposes `Unit`.
- `Recorder.snap()` null becomes a generic `IllegalStateException`.

The m152 status contracts support a strict version 1 policy:

- invalid recording, out-of-order recording, deferred-target instantiation
  failure, command insertion failure, and shader compilation failure are fatal
  because the runtime owns all related inputs and sequencing;
- invalid font bytes, invalid pixel-buffer shape, unsupported dimensions, and
  other user input rejected before native mutation can fail only that
  operation;
- `snap() == null` is fatal because Skia does not preserve enough cause
  information to prove recovery is safe;
- `submit() == false` becomes `DeviceLost` when the backend confirms device
  loss and fatal backend failure otherwise;
- WebGPU validation, out-of-memory, and internal errors are fatal;
- unexpected `GPUDevice.lost` becomes `DeviceLost`.

Image materialization must never fail silently by omitting a draw. A native
image provider records a sticky error which the worker checks before returning
the Recording.

Primary references:

- <https://skia.googlesource.com/skia/+/refs/heads/main/include/gpu/graphite/GraphiteTypes.h>
- <https://skia.googlesource.com/skia/+/refs/heads/main/src/gpu/graphite/Recorder.cpp>
- <https://skia.googlesource.com/skia/+/refs/heads/main/src/gpu/graphite/QueueManager.cpp>
- <https://gpuweb.github.io/gpuweb/>
- `skiko-fork/skiko/skiko/skiko-graphite/src/commonMain/kotlin/org/jetbrains/skia/gpu/graphite/GraphiteContext.kt`

## Failure policy

**Accepted**

- Public fatal runtime failures are:
  - `InternalInvariant`;
  - `BackendValidation`;
  - `ResourceExhausted`;
  - `BackendFailure`;
  - `WorkerTerminated`.
- Each failure includes a stage. Stages cover at least resource
  materialization, deferred-canvas creation, snap, recording insertion,
  submission, GPU completion, and worker execution.
- The following fail only the current operation:
  - invalid arguments rejected before native work;
  - invalid font data;
  - invalid pixel-buffer layout;
  - unsupported dimensions;
  - coroutine cancellation.
- The following fail the entire runtime:
  - invalid internal command buffers;
  - deferred-canvas or snap failure after input validation;
  - every failed recording insertion status;
  - texture upload or creation failure after input validation;
  - WebGPU validation, out-of-memory, or internal error;
  - submission failure without confirmed device loss;
  - unexpected worker termination.
- Confirmed device loss uses terminal `GraphiteRuntimeState.DeviceLost`
  instead of `Failed`.
- Version 1 isolates a failure only when the library knows it happened before
  native state mutation. It performs no automatic retry of native recording or
  submission work.

## Initial encoder scope

**Accepted**

Version 1 exposes one encoder with:

- scoped 4x4 transforms;
- rectangle and path clipping;
- internal save/restore used by scoped APIs;
- nested display-list drawing;
- rectangle, rounded rectangle, oval, circle, line, and path geometry;
- image and source/destination image-rectangle drawing with sampling options;
- prepared text-layout and glyph-run drawing;
- immutable fill and stroke paints;
- color, alpha, antialiasing, stroke width, cap, join, and miter;
- gradients and image patterns;
- dash/path effects;
- blur sufficient for text halos;
- blend modes.

Deferred encoder features:

- custom SkSL;
- complex image filters;
- external textures and video.

## Resource and preparation contracts

**Accepted**

- Paints, paths, text layouts, glyph runs, images, display lists, recordings,
  and frames are immutable after their builders finish.
- The core API requires callers to provide prepared CPU inputs whenever the
  operation can reasonably happen outside GraphiteSurface.
- The core runtime has no preparation-worker pool.
- Shaping, line breaking, and image decoding happen before submission to a
  Graphite recorder.
- This decision supersedes the earlier assumption that version 1 includes
  built-in high-level shaping and encoded-image decoding.
- Version 1 has no optional preparation module. A later module may add
  Skia-backed helpers without changing the recorder contract.
- Fonts are supplied as font bytes. Version 1 does not query system fonts or
  perform implicit platform fallback.
- Public immutable text results expose read-only glyph IDs, clusters,
  positions, and bounds. They retain the font resource needed to interpret
  those glyph IDs.
- Version 1 text types are:
  - `GraphiteFontFace`, containing copied font bytes, face index, and
    variation coordinates;
  - `GraphiteFont`, containing a face, size, horizontal scale, and skew;
  - `GraphiteGlyphRun`, containing one font, glyph IDs, positions, clusters,
    and bounds;
  - `GraphiteTextLayout`, containing ordered glyph runs and aggregate bounds.
- One glyph run uses one font. Callers represent prepared fallback with
  multiple ordered runs.
- Public text types contain no Skia object or native pointer. They copy their
  input arrays and expose read-only data.
- Version 1 has no text-along-path type, layout helper, or encoder command. This
  supersedes Q66 and Q74. Curved-path text is deferred as one complete feature.
- KMaP owns text collision, anchor choice, visibility, layer policy, and other
  map-scene decisions.
- Prepared image pixels produce an immutable, runtime-independent image.
- Encoded image bytes are outside the core. Lazy Skia decoding during a
  recorder job is forbidden because it can stall recording.
- Recorder workers perform the Skia-dependent work: materialize local
  typefaces, fonts, and raster images; rasterize glyphs; manage glyph atlases;
  upload textures and mipmaps; draw into the Canvas; and snap recordings.
- Worker messages contain portable bytes, numbers, arrays, and resource IDs.
  They never contain native Skia objects or Wasm pointers.

### Resource registration and retirement

**Accepted**

- A public font, image, path, paint, or display list owns an immutable,
  runtime-independent CPU representation.
- A runtime assigns an internal resource ID on first use.
- Each recorder worker materializes and caches its own local Skia object.
- Later commands sent to that worker reference the resource ID.
- Display lists, recordings, frames, and in-flight GPU work retain the
  resources they use.
- After the last public and internal reference disappears, the runtime releases
  worker caches after dependent GPU work retires.
- Version 1 reuses resources by handle identity. It does not hash large payloads
  or deduplicate distinct handles by content.
- Resource IDs are unsigned 64-bit integers, monotonic within one runtime, and
  never reused before runtime shutdown.
- The same public resource receives independent IDs when registered with
  different runtimes.
- Before creating a deferred canvas, a recorder worker validates the complete
  command buffer, resolves its resource table, materializes every uncached font
  and image, creates the deferred canvas, interprets drawing commands, and
  calls `snap()`.
- Invalid font data fails only the current `record()` with
  `GraphiteResourceException.InvalidFont`. The runtime marks that resource ID
  invalid so later uses fail without repeating materialization.
- Pixel-buffer layout is validated before registration.
- GPU upload or texture creation failure after validation is fatal to the
  runtime.
- Native image-provider failure is sticky and checked before returning a
  Recording. A failed image draw is never silently omitted.

### Skia cache research

The Skia m152 fork gives the Context a default 256 MiB budget and gives each
public Recorder another independent 256 MiB budget. The nominal default is
therefore:

```text
256 MiB + recorderCount * 256 MiB
```

These are best-effort LRU budgets, not hard GPU memory ceilings. In-flight
recordings, client-owned resources, swapchain allocations, CPU text caches, and
driver allocations can remain outside or above them.

Both Context and Recorder expose native operations for current, purgeable, and
maximum budgeted bytes, changing the maximum, deferred cleanup, and freeing GPU
resources. The Kotlin fork does not expose those operations yet.

Each Recorder also owns text atlases and caches. The fork's current
`SkikoGraphiteImageProvider` retains up to 256 images per Recorder by object
count rather than byte size. The new runtime must replace that behavior with
the accepted resource-handle registry.

Primary references:

- <https://skia.googlesource.com/skia/+/refs/heads/main/include/gpu/graphite/Context.h>
- <https://skia.googlesource.com/skia/+/refs/heads/main/include/gpu/graphite/Recorder.h>
- <https://skia.googlesource.com/skia/+/refs/heads/main/include/gpu/graphite/ContextOptions.h>
- `skiko-fork/skiko/skiko/skiko-graphite/src/commonMain/cpp/common/GraphiteImageProvider.cc`

### GPU cache policy

**Accepted**

- `GraphiteGpuCacheConfig` contains a Context byte limit and one aggregate
  byte limit for all public Recorder caches.
- The runtime divides the Recorder total across `recorderCount`; adding
  Recorders does not multiply the configured total.
- Metrics expose limit, current budgeted bytes, and purgeable bytes for the
  Context and each Recorder.
- Prototype measurements choose the version 1 default byte values.
- `suspend GraphiteRuntime.trimGpuCaches()` schedules
  `freeGpuResources()` on the render worker and every recorder worker, then
  returns after all have executed it.
- Trimming is best-effort and retains resources used by frames or GPU work in
  flight. Users may call `awaitIdle()` first when they need the most complete
  cleanup.
- Version 1 does not run automatic periodic cache cleanup.

### Explicit and garbage-collected lifetimes

**Accepted**

- `GraphiteRuntime`, `GraphiteFontFace`, `GraphiteImage`,
  `GraphiteDisplayList`, `GraphiteRecording`, and `GraphiteFrame` require
  explicit, idempotent `close()`.
- `GraphiteFont`, `GraphitePaint`, `GraphitePath`,
  `GraphiteGlyphRun`, `GraphiteTextLayout`, `GraphiteColor`, and
  `GraphitePixelBuffer` are immutable garbage-collected values.
- A retained object owns an internal reference to every closeable resource it
  uses. Closing the caller's original handle never invalidates a display list,
  recording, frame, glyph run, or other object that still retains it.

### Glyph-run validation

**Accepted**

- Construction validates synchronously:
  - glyph IDs are in `0..65535`;
  - glyph and position counts agree;
  - cluster count agrees when clusters are present;
  - positions and bounds contain finite values;
  - bounds are ordered.
- Clusters are optional.
- The constructor does not materialize the font or check that each glyph exists
  in it.
- Caller-supplied bounds are conservative metadata. Version 1 does not use
  those bounds to skip the complete draw, so an inaccurate bound cannot hide
  otherwise valid text.

### Deferred curved-path text research note

The future feature can use these Skia operations without a Skia or Skiko patch:

- `SkContourMeasure.getPosTan()` produces a position and tangent at a distance
  along a contour.
- `SkCanvas.drawGlyphs()` and `SkTextBlobBuilder.allocRunRSXform()` accept
  one rotation, scale, and translation transform per glyph.

The intended future operation bends the baseline, not the glyph outlines.

Primary references:

- <https://api.skia.org/classSkContourMeasure.html>
- <https://api.skia.org/classSkCanvas.html>
- <https://api.skia.org/classSkTextBlobBuilder.html>

## Color contract

**Accepted**

- Version 1 presentation is standard dynamic range, 8-bit sRGB.
- Public paint colors are unpremultiplied sRGB values.
- `GraphiteColor` stores `0xRRGGBBAA` in a packed `UInt`.
- `GraphitePixelBuffer` contains width, height, row bytes, copied immutable
  bytes, an alpha type, and either `Rgba8888` or `Bgra8888` format.
- Pixel alpha types are `Opaque`, `Premultiplied`, and
  `Unpremultiplied`.
- The implementation performs premultiplication and conversion to the
  platform presentation format internally.
- Wide gamut, HDR, and floating-point presentation formats are deferred.
- The runtime API depends only on `GraphiteColor`. The Compose module provides
  conversions to and from `androidx.compose.ui.graphics.Color`.

## Diagnostics and logging

**Accepted**

- Persistent correctness state remains in `StateFlow`.
- Rare asynchronous runtime events use a typed `SharedFlow<GraphiteEvent>`.
- The event flow has capacity 64 with `DROP_OLDEST`. Workers never suspend to
  publish diagnostics.
- Runtime lifecycle and device loss remain observable through `StateFlow`;
  events are not a control protocol.
- A synchronous metrics snapshot exposes:
  - current depth and capacity for each queue;
  - submitted, completed, cancelled, and failed recording counts;
  - total and maximum queue-wait and recording times;
  - accepted, replaced, and rejected frame counts;
  - current pending and in-flight frame counts;
  - total and maximum submission and presentation times;
  - resource counts and estimated resource bytes;
  - dropped log count;
  - device-loss count.
- `metricsSnapshot()` is synchronous and best-effort. It reads atomic worker
  counters without pausing workers and includes a monotonic capture timestamp.
- Cumulative counters remain monotonic. Timing uses monotonic clocks only at
  job boundaries, never for individual drawing commands.
- Return values, typed exceptions, and runtime state remain authoritative.
- Internal logs use Rafael's Scribe library.
- Scribe's `Archivist` is a `fun interface` with
  `suspend fun write(event: Entry)`, so an archivist lambda is a natural
  configuration input.
- GraphiteSurface depends on the published Scribe 0.7.0 artifact, including its
  JS and Wasm targets.
- `GraphiteRuntimeConfig` accepts one optional `Archivist`. The runtime
  passes it directly to its internally owned Scribe instance.
- Runtime creation starts Scribe. Runtime shutdown retires it, and
  `awaitClosed()` waits for its queue to drain.
- Graphite never invokes, wraps, or transfers the archivist lambda.
- Recorder and render workers create portable structured log records. The
  runtime side that owns Scribe converts those records into `Scroll` values
  and seals them into Scribe.
- Scribe owns archivist invocation and its execution behavior.
- Logging is best-effort and never applies backpressure to recorder or render
  workers. Queue drops are counted in metrics.
- Graphite emits one structured completion event for a meaningful operation,
  such as runtime creation, a failed or unusually slow recording, device loss,
  or shutdown. It does not log every frame by default.
- `GraphiteEvent` and metrics remain independent of logs because Scribe may
  drop queued entries.
- Graphite adds no logging levels or filter abstraction in version 1. The
  supplied archivist controls filtering and destinations.
- An archivist failure increments `archiveFailures` and publishes
  `GraphiteEvent.ArchiveFailure`. It does not throw into rendering or change
  runtime state.

## Worker command buffers

**Accepted**

- `GraphiteEncoder` writes a compact immutable binary command buffer on the
  caller thread after recorder queue admission.
- Commands use an internal opcode, payload length, portable arguments, and
  resource IDs.
- All targets use the same logical command format and parser.
- Browser workers receive one transferable publication payload per immutable
  resource and later command messages reference cached runtime-local IDs.
- `GraphiteDisplayList` encapsulates commands but does not expose a stable
  binary format. Internal format changes do not create a persistence contract.
- `GraphiteRuntimeConfig.maxCommandBufferBytes` limits one recording command
  buffer. `GraphiteDisplayList.build(maxCommandBufferBytes = ...)` independently
  limits one display-list command buffer.
- Exceeding the limit throws
  `GraphiteEncodingException.CommandBufferTooLarge` before publication. It
  releases the recorder queue slot and leaves the runtime `Ready`.
- Font and image payloads use the separate resource registry and do not count
  toward command-buffer bytes.
- Prototype measurements choose the version 1 default byte limit.
- Recorder workers validate and interpret the buffer, perform Skia calls, and
  snap the result.
- The parser validates magic, internal version, total length, opcode, payload
  length, argument ranges, and resource IDs before accessing command data.
- Command buffers have no checksum. Immutable publication through the worker
  queue provides the synchronization boundary.
- An invalid internal command buffer indicates a Graphite bug, memory
  corruption, or broken invariant. It moves the entire runtime to
  `GraphiteRuntimeState.Failed`; it is not isolated to one recording job.
### Encoder block failure and cancellation

**Accepted**

- If the user's encoder block throws, no command buffer is published, the queue
  slot is released, temporary builder state is discarded, and the same
  exception returns to the caller.
- User-code failure does not change runtime state.
- Encoder methods check cancellation between commands.
- A non-suspending user loop that does not call the encoder cannot be
  interrupted by the runtime. Cancellation is observed when it next calls an
  encoder method or returns.
- Heavy computation remains outside the encoder block.

## Initialization failures

**Accepted**

- `GraphiteUnsupportedPlatformException` carries a
  `GraphiteSupportReport` with the platform, missing capabilities, and
  actionable details such as missing cross-origin isolation or adapter
  rejection.
- `GraphiteInitializationException` represents an unexpected failure at a
  typed initialization stage and retains its cause.
- Unsupported capability and unexpected initialization failure remain
  separate categories.

Scribe references:

- Published artifact:
  <https://central.sonatype.com/artifact/com.rafambn/scribe/0.6.0>
- Archivist API:
  <https://github.com/rafambn/Scribe/blob/0.6.0/scribe/src/commonMain/kotlin/com/rafambn/scribe/Archivist.kt>
- Supported targets:
  <https://github.com/rafambn/Scribe/blob/0.6.0/scribe/build.gradle.kts>

## KMaP boundary

**Accepted**

KMaP owns:

- networking and tile acquisition;
- tile parsing and map data;
- Mapbox style interpretation;
- scene, layer, and z-order planning;
- camera state;
- label candidates, collision, anchors, repetition, and visibility;
- scheduling policy and explicit selection of Graphite recorders.

GraphiteSurface owns:

- portable draw commands;
- immutable drawing resources and display lists;
- recorder workers and bounded queues;
- Graphite Context and render worker;
- frame assembly contracts;
- presentation and lifecycle metadata;
- resource retirement and diagnostics.

The core GraphiteSurface module does not schedule text shaping or image
decoding. A later preparation module may offer only explicitly defined
operations. It cannot be an arbitrary Kotlin-code executor because arbitrary
closures cannot be transferred to a Web Worker with common semantics.

## Browser feasibility gate

**Accepted**

- Browser support requires real Web Workers, not coroutine dispatch on the
  browser main thread.
- The prototype must validate the selected Skia/Graphite WebGPU stack with:
  - Graphite Context ownership on a render worker;
  - one native Recorder per recorder worker;
  - portable command transfer;
  - recording transfer or another viable handoff to the render worker;
  - Canvas/presentation transfer and resize;
  - shutdown, cancellation, and device loss.
- Shared memory designs require `SharedArrayBuffer` and the browser security
  isolation headers COOP/COEP.
- If WebGPU, real worker execution, or another mandatory prerequisite is
  unavailable, `GraphiteRuntime.create()` throws a typed unsupported-platform
  exception. It never falls back silently to main-thread rendering.
- Version 1 has no separate `checkSupport()` call. The exception from
  `create()` carries the support report because a reliable probe would already
  perform most asynchronous GPU initialization and could race with creation.
- No cross-platform promise about multiple Graphite recorders in browser
  workers is final until this prototype succeeds.
- The browser prototype passes only when it proves:
  - the Graphite Context lives on the render worker;
  - at least two Recorder workers execute concurrently;
  - Skia/Graphite work does not execute on the browser main thread;
  - portable commands and recording results cross worker boundaries correctly;
  - resize does not recreate the runtime;
  - cancellation and shutdown complete without leaked work;
  - pending queues and memory stay bounded under sustained camera movement.
- Runtime support is detected by capabilities, never by the browser user-agent.
- The first supported browser tier is Chrome/Edge 148 or newer on desktop.
- Safari 26 or newer, Firefox 142 or newer on Windows, Firefox 147 or newer on
  Apple Silicon macOS, Android browsers, and WebViews remain validation targets
  rather than version 1 compatibility promises.
- Browser deployment requires HTTPS or localhost,
  `crossOriginIsolated == true`, COOP `same-origin`, and COEP
  `require-corp`. COEP also affects cross-origin tile, font, and script
  requests, which must provide compatible CORS or CORP headers.
- The HTML canvas transfers to its OffscreenCanvas before any context is
  created and transfers only once.

Useful primary references:

- Emscripten pthreads:
  <https://emscripten.org/docs/porting/pthreads.html>
- Emscripten Wasm Workers:
  <https://emscripten.org/docs/api_reference/wasm_workers.html>
- MDN SharedArrayBuffer security requirements:
  <https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/SharedArrayBuffer>
- MDN Web Workers:
  <https://developer.mozilla.org/docs/Web/API/Web_Workers_API>
- Can I Use WebGPU:
  <https://caniuse.com/webgpu>
- Can I Use WebGPU in WorkerNavigator:
  <https://caniuse.com/mdn-api_workernavigator_gpu>
- Can I Use OffscreenCanvas WebGPU:
  <https://caniuse.com/mdn-api_offscreencanvas_getcontext_webgpu_context>
- Can I Use Wasm threads:
  <https://caniuse.com/wasm-threads>
- Can I Use cross-origin isolation:
  <https://caniuse.com/mdn-api_crossoriginisolated>

### Browser worker implementation

**Accepted as the primary experiment; prototype required**

- Use Emscripten pthreads for both recorder workers and the render worker.
  Emscripten implements these pthreads with real Web Workers, while all of them
  share the module's `WebAssembly.Memory`.
- Independent Kotlin Web Worker programs and Emscripten Wasm Workers are not
  the primary experiment. They do not provide the same native shared heap
  needed to hand a snapped native recording to the render worker without
  serializing it.
- Keep each completed native `Recording` in a shared native registry and pass
  only an opaque handle to the render worker. Ownership and destruction still
  occur on the designated native workers.
- The current Skiko/Wasm build uses dynamic linking. Emscripten documents
  pthread support with dynamic linking as experimental, while Wasm Workers do
  not support this dynamic-linking setup. This combination is therefore a
  prototype gate rather than an assumed capability.
- The prototype must additionally prove that `OffscreenCanvas`, WebGPU device
  state, and the current JavaScript-value bridge are usable from the render
  pthread.
- If the pthread prototype fails, preserve the earlier independent-worker and
  Wasm Worker designs as research alternatives and reassess the architecture.
  This is a design fallback, not an automatic runtime fallback to main-thread
  rendering.
- Browser worker startup is dynamic. The build does not reserve a fixed
  `PTHREAD_POOL_SIZE` because `recorderCount` is a runtime setting.
- Runtime creation starts exactly one render pthread. After it creates the
  Context and Recorders, it starts exactly `recorderCount` recorder pthreads.
- `GraphiteRuntime.create()` suspends and returns control to the browser event
  loop while pthreads start. It returns only after asynchronous Context and
  Recorder readiness acknowledgements.
- The Emscripten main module, Graphite side module, and all participating C++
  objects compile and link with `-pthread`.
- Browser builds use `ALLOW_BLOCKING_ON_MAIN_THREAD=0`. Browser-main code never
  calls `pthread_join()`, `Atomics.wait()`, or a blocking condition variable.
- Shutdown and `awaitClosed()` use asynchronous worker acknowledgements.

### Browser module ownership

**Accepted**

- A browser page loads one Emscripten main module and its Graphite side module.
  All runtimes on that page share that module instance and Wasm heap.
- Each runtime still owns a separate Context, worker group, registry namespace,
  queues, resources, state, and metrics.
- Closing a runtime releases its native allocations and workers but does not
  unload the shared module. Closing the last runtime also leaves the module
  loaded for reuse during the page lifetime.
- The Wasm heap maximum is consequently page-global, not per runtime.
- Logical runtime isolation cannot protect another runtime from native heap
  corruption that damages the shared module. Version 1 documents this rather
  than duplicating the complete Skia module per runtime.
- `INITIAL_MEMORY` and `MAXIMUM_MEMORY` are page-global build settings and are
  not fields in `GraphiteRuntimeConfig`.
- The prototype chooses their version 1 values. Metrics report memory growth
  and its duration. A separate web bootstrap configuration is deferred until
  real applications demonstrate a need for it.

### Browser module loading

**Accepted**

- Concurrent `GraphiteRuntime.create()` calls await the same module-loading
  operation and never observe partial initialization.
- A network or asset-loading failure fails all current callers with
  `GraphiteInitializationException`. The loader performs no hidden retry.
- A later explicit `create()` call may start a new loading attempt after such a
  transient failure.
- Missing WebGPU, cross-origin isolation, Wasm thread support, or another
  required capability throws `GraphiteUnsupportedPlatformException` instead.
- Once the shared module is ready, failure to initialize one runtime affects
  only that runtime unless the failure corrupts or terminates the shared
  module.
- An Emscripten abort, detected shared-heap corruption, or failure of global
  module infrastructure moves every runtime in that module to terminal
  `Failed`. Pending work fails and future `create()` calls fail immediately.
- The page must reload to obtain a fresh module after a shared-module terminal
  failure. The runtime never attempts to continue on or reinitialize a heap
  whose integrity is unknown.

### Browser queues, copies, and memory

**Accepted**

- The browser controller owns each bounded FIFO recorder queue and allows at
  most one active native job per recorder.
- It dispatches native work to the recorder pthread with
  `emscripten_proxy_callback()`. Completion delivers the result and permits the
  next queued job to start.
- Version 1 has no custom native shared-memory ring buffer. Add one only if
  measurements show that Emscripten proxying is a material bottleneck.
- Kotlin/JS performs one contiguous block copy of a command buffer into Wasm
  memory.
- Kotlin/Wasm initially performs two contiguous block copies because its
  `ByteArray` must first cross the Wasm JavaScript interop boundary.
- The implementation never copies command buffers byte by byte.
- Large immutable resources such as fonts and images copy into the runtime's
  shared native registry once and subsequent commands reference their IDs.
- Metrics report copied bytes and copy duration so a direct-to-shared-memory
  encoder can be justified by evidence later.
- Browser builds retain `ALLOW_MEMORY_GROWTH` with an explicit maximum.
- Native structures keep heap offsets rather than long-lived typed-array
  views. Code reacquires the current heap view after any operation that may
  grow memory.
- Runtime creation preallocates hot-path structures. The prototype compares
  growth against a fixed heap before choosing initial and maximum sizes.

### Browser native-job ownership

**Accepted**

- Each active recorder job is a private native structure in the shared heap.
  It contains a job ID, command-buffer offset and size, an atomic cooperative
  cancellation flag, result kind, and optional opaque Recording handle.
- After Kotlin queue admission and encoding, the bridge allocates the native
  job, copies the command buffer in one block, and dispatches it with
  `emscripten_proxy_callback()`.
- Kotlin and JavaScript never read or write the job's atomic fields directly.
- Cancellation after dispatch sets the native flag. The recorder checks it at
  safe command boundaries and discards any undeliverable result.
- Exactly one completion or cancellation callback releases the command buffer
  and job. A dead destination pthread produces `WorkerTerminated` and terminal
  runtime failure.
- On successful `snap()`, the recorder worker transfers the native
  `unique_ptr<Recording>` into that runtime's shared native registry and never
  accesses the Recording again.
- Kotlin receives only a runtime-scoped opaque Recording ID. The render worker
  is the sole native consumer and destructor after publication.
- Public Recording handles, frames, and in-flight submissions retain logical
  references. Closing a public handle from any thread schedules release rather
  than directly destroying native state.
- The render worker destroys the Recording only after every logical reference
  and GPU use retires. If cancellation races with delivery, the runtime closes
  the undelivered Recording automatically.

### Browser pthread prototype

**Accepted and authorized**

- The prototype lives in a non-published `:experiments:wasm-pthreads` module.
  Required fork and engine changes remain gated as experimental until the gate
  passes. Normal builds retain their existing behavior.
- Work proceeds in two stages: Kotlin/JS first for easier transport and heap
  debugging, then Kotlin/Wasm using the same native protocol.
- Browser architecture passes only when both Kotlin/JS and Kotlin/Wasm pass.
- The prototype must prove:
  - the Graphite Context and WebGPU presentation execute on the render pthread;
  - two recorder pthreads execute real Graphite work with measured temporal
    overlap;
  - completed Recordings cross through opaque handles in the shared native
    heap;
  - the render worker inserts them in deterministic order and presents the
    correct image;
  - no Skia work executes on the browser main thread;
  - resize preserves the Context and worker group;
  - cancellation and shutdown never block the browser main thread;
  - queues and memory remain bounded for at least 60 seconds of continuous
    load;
  - Emscripten main-module and Graphite side-module dynamic linking works;
  - metrics capture copy bytes and time, memory growth, latency, throughput,
    queue bounds, and worker overlap.
- If the experiment fails, it does not fall back to main-thread rendering.
  Changes that harm normal builds are removed or disabled, and a reproducible
  failure report records the stage and cause.
- Static linking, independent worker module instances, and Emscripten Wasm
  Workers remain documented research alternatives after a failure. The next
  architecture choice returns to the grilling session with the measurements.
- This experiment proves GraphiteSurface infrastructure, not final KMaP frame
  rate. KMaP integration and its map benchmark occur after the runtime exists.

### Browser pthread prototype findings

**Measured; the gate is still in progress**

- Linking the pthread-enabled Skiko bridge against the published Skia m152
  Wasm archives fails before runtime. `wasm-ld` rejects
  `libskia_graphite_dawn_ext` because its objects were not compiled with the
  `atomics` and `bulk-memory` target features. Enabling `-pthread` only in
  Skiko is therefore insufficient; the complete Skia Wasm archive set must be
  rebuilt with the same thread mode.
- A local exact-tag rebuild of Skia `m152-7bb45c7c26` with Emscripten 4.0.7
  and `-pthread` completed successfully. Inspection of an object from
  `libskia_graphite_dawn_ext` confirms both `atomics` and `bulk-memory` target
  features.
- With those rebuilt archives, the pthread-enabled Emscripten main module and
  Graphite side module compile and link successfully. Emscripten still marks
  `MAIN_MODULE`/`SIDE_MODULE` with pthreads as experimental; the successful
  link is evidence for compatibility, not a runtime pass.
- The property-gated main-module and Graphite side-module C++ probe both
  compile with Emscripten 4.0.7. Inspection of the generated probe object
  confirms `atomics` and `bulk-memory` target features.
- Both Kotlin/JS and Kotlin/Wasm experiment hosts compile and produce complete
  staged browser distributions containing their host bundle, the Skiko main
  module, the Graphite side module, and the probe resources. This verifies
  compilation and packaging only; neither counts as a browser-runtime pass.
- Skiko's D8-only artifact is excluded only while the experiment property is
  active because its `ENVIRONMENT=shell` target is incompatible with browser
  pthreads and is irrelevant to this browser gate. Normal builds retain the
  D8 artifact.
- The optimized Emscripten module originally retained a worker self-reference
  to `skiko.unoptimized.mjs`, which the Skiko Wasm JAR does not ship. The
  experimental packaging rewrites that self-reference to `skiko.mjs`; browser
  pthreads can therefore instantiate the same shipped module in each Worker.
- `GPUDevice` is not currently a WebGPU `Serializable` object, and arbitrary
  Emscripten `Module` properties are not inherited by pthread workers. The
  probe therefore requests the adapter and device inside the render pthread,
  then stores the device in that worker's own
  `Module.preinitializedWebGPUDevice` before creating the Graphite Context.
- Emscripten can transfer the Compose-owned HTML canvas only to the render
  pthread through `emscripten_pthread_attr_settransferredcanvases`. Recorder
  pthreads receive no canvas.
- Presentation requires a Recorder too: `SkSurfaces::WrapBackendTexture`
  receives a Recorder, while deferred Recordings need the target surface at
  insertion. The prototype consequently uses one internal presentation
  Recorder owned by the render pthread in addition to user-configured worker
  Recorders. Whether this remains a strictly internal implementation detail is
  an open API question after the browser gate.
- A Bun server supplies HTTPS-equivalent localhost delivery plus COOP
  `same-origin`, COEP `require-corp`, and CORP `same-origin`. Direct response
  inspection confirms all three headers and correct Wasm MIME types.
- A portable official Chrome for Testing 152.0.7977.54 run against the Bun
  server reports `crossOriginIsolated == true`, exposes `SharedArrayBuffer`,
  and provides WebGPU in both the page and an ordinary dedicated Worker. This
  removes the collaborative-browser limitation from the earlier attempt. The
  test browser required `--no-sandbox` only because the portable binary could
  not enter Chrome's namespace sandbox in this Linux environment; it visited
  only the localhost probe and was deleted after the test.
- Loading the Graphite side module with Skiko's internal JavaScript loader
  after pthread creation leaves each worker's Wasm table stale. The first side
  module entry then lies exactly one slot beyond the worker's table. Loading it
  through native `emscripten_dlopen()` before starting the runtime synchronizes
  the dynamic module, and real side-module entry points execute in the render
  and recorder pthreads. Main-module `EM_JS` services require exported C
  trampolines so the dynamic linker can resolve them from the side module.
- Pre-created Emscripten pool workers also execute Skiko's top-level
  `pre-setup.mjs`, which recursively calls `loadSkikoWASM()` inside a pthread
  and currently aborts while error reporting is not initialized. The workers
  remain usable by this probe, but this bootstrap defect must be fixed before
  the pool strategy can pass a production gate.
- The render pthread successfully receives the transferred OffscreenCanvas,
  requests a WebGPU adapter and device, imports that device into the native
  API, obtains its queue, and creates the Graphite Dawn Context. The canvas
  must be registered as the module canvas before thread creation, or the
  bridge must retrieve the transferred entry from `GL.offscreenCanvases`.
- Emscripten 4.0.7's legacy `-sUSE_WEBGPU=1` binding is incompatible with the
  current browser limits object. It reads the removed
  `maxInterStageShaderComponents` property, while Chrome 152 exposes
  `maxInterStageShaderVariables`. A probe-only compatibility value of 60 lets
  Graphite Context creation continue. This shim is diagnostic, not an accepted
  runtime solution.
- After that shim, two real recorder pthreads start Graphite drawing work
  about 1.35 ms apart. They do not finish: each pthread has a separate
  JavaScript `WebGPU.mgrDevice` registry, so the numeric device handle created
  in the render worker is unknown when a recorder worker reaches
  `wgpuDeviceCreateBuffer`. Shared Wasm memory does not make those per-worker
  JavaScript registries shared.
- The Kotlin/JS gate therefore fails at cross-worker WebGPU-handle ownership.
  Presentation, successful Recording publication, resize, cancellation,
  shutdown, and sustained bounded load remain unproven. Kotlin/Wasm was not
  run because it uses the same failing native layer and cannot provide new
  evidence at this stage.
- Current Emscripten documentation has replaced the legacy WebGPU binding with
  the Dawn-maintained Emdawnwebgpu port. The focused gate below confirms that
  replacing the binding does not make one device usable from multiple pthread
  realms.

### Emdawnwebgpu cross-pthread gate

The gate uses the exact dependency set already pinned by the project:

- Skia m152 commit `7bb45c7c26b10d7cb873f9e545c5602d6e97b510`;
- Dawn/Emdawnwebgpu commit `1e897275172a23f27b0022fa6beae3084ed54a9b`;
- Emscripten 4.0.7;
- Emdawnwebgpu port option `shared_memory=true`;
- Chrome for Testing 152.0.7977.54.

The minimal probe deliberately excludes Skia. A render pthread requests and
imports a WebGPU device, publishes the resulting native `WGPUDevice` handle
through shared Wasm memory, and two recorder pthreads inspect whether that same
handle resolves in their local Emdawn JavaScript object tables before buffer
creation. Chrome reports:

```text
[emdawn gate] render handle=145840 local-js-object=yes
[emdawn gate] recorder=1 handle=145840 local-js-object=no
[emdawn gate] recorder=0 handle=145840 local-js-object=no
```

The page's final state is `render=2`, `recorder0=-1`, and `recorder1=-1`.
Cross-origin isolation, `SharedArrayBuffer`, and WebGPU all pass. The handle
itself crosses the native shared heap, but its JavaScript object does not.
Calling `wgpuDeviceCreateBuffer` from either recorder worker therefore cannot
be valid for that handle.

This matches the Emdawn implementation: `WebGPU.Internals.jsObjects` is a
JavaScript array created independently in each worker realm. The
`shared_memory` port option makes native reference counts atomic; it does not
share this JavaScript registry or proxy WebGPU calls to the owning worker.

**Conclusion:** Emdawnwebgpu m152 fails the focused compatibility gate. A
browser implementation cannot place Graphite Recorders on independent pthreads
while all of them use a device imported only by the render pthread. No public
architecture change is accepted here yet. The next grilling decision must
define which behavior is required to be common across targets before choosing
the browser worker model.

### Decisions after the Chrome pthread probe

**Accepted**

- The next browser experiment migrates the fork from Emscripten 4.0.7's
  legacy `-sUSE_WEBGPU=1` binding to the Dawn-maintained Emdawnwebgpu port.
- That migration is a focused compatibility gate, not approval of a new
  public architecture. It must first prove whether one Graphite Context and
  its Recorders can perform GPU-backed recording work on separate pthreads
  without worker-local handle failures.
- If Emdawnwebgpu does not support that ownership pattern, the grilling
  session revisits the browser execution model. It does not silently move
  rendering to the browser main thread.
- The render worker's presentation Recorder remains a private implementation
  detail and does not count toward the user-configured recorder count.
- Replacing the Compose canvas detaches and recreates only presentation-bound
  state. It preserves the runtime, Context, workers, registry, display lists,
  and compatible recordings.
- The Emdawnwebgpu m152 migration gate has been executed. It fails because the
  render pthread owns the only JavaScript registry entry for the imported
  device; both recorder pthreads receive the native handle but cannot resolve
  it. Merely changing WebGPU bindings is rejected as a solution.

Additional primary references:

- Emscripten proxying API:
  <https://emscripten.org/docs/api_reference/proxying.h.html>
- Emscripten dynamic-linking pthread support:
  <https://emscripten.org/docs/compiling/Dynamic-Linking.html#pthreads-support>
- Kotlin/Wasm array interoperability:
  <https://kotlinlang.org/docs/wasm-js-interop.html#array-interoperability>
- Emscripten memory-growth setting:
  <https://emscripten.org/docs/tools_reference/settings_reference.html#allow-memory-growth>
- Current Emscripten WebGPU support:
  <https://emscripten.org/docs/porting/multimedia_and_graphics/WebGPU-support.html>
- Emdawnwebgpu integration and version requirements:
  <https://github.com/google/dawn/blob/main/src/emdawnwebgpu/pkg/README.md>
- Emdawnwebgpu m152 JavaScript object registry:
  <https://github.com/google/dawn/blob/1e897275172a23f27b0022fa6beae3084ed54a9b/third_party/emdawnwebgpu/pkg/webgpu/src/library_webgpu.js#L112-L163>
- Emdawnwebgpu m152 port implementation:
  <https://github.com/google/dawn/blob/1e897275172a23f27b0022fa6beae3084ed54a9b/src/emdawnwebgpu/pkg/emdawnwebgpu.port.py>
- WebGPU synchronous cross-thread object transfer limitation:
  <https://gpuweb.github.io/gpuweb/explainer/#synchronous-object-transfer>
- Emscripten pthread proxying rules:
  <https://emscripten.org/docs/porting/pthreads.html#proxying>

## Deferred work

- continuous render mode;
- dirty regions and retained rendering;
- ordered recorders;
- automatic device-loss recovery;
- more than one presentation target per runtime;
- external textures, mutable images, and video;
- stable display-list persistence format;
- typed display-list parameters;
- custom SkSL and complex image filters;
- per-frame GPU completion handles.
- text layout and drawing along a path;
- high-level text shaping, line breaking, and image-decoding helpers.

Dirty regions should later be designed as a retained offscreen scene or another
explicit preservation mechanism, not by assuming swapchain contents survive.

## Rejected designs

- A public `GraphiteOutputMode` selecting platform implementation details.
- A renderer callback whose lifecycle is controlled by the surface.
- A surface-owned runtime.
- Coroutines masquerading as native worker execution on web.
- Sending arbitrary Kotlin lambdas to Web Workers.
- An unbounded recorder or frame queue.
- Dropping recorder submissions silently.
- A second non-suspending recording API in version 1.
- Generic scheduler and affinity policy inside GraphiteSurface.
- Ordered recorders without benchmark evidence.
- dp-only or pixel-only encoder coordinates.
- Public exposure of `SkPicture`.
- Patching Skia/Skiko to transform an already snapped recording arbitrarily.
- Automatically invalidating all recordings on every browser resize.
- Retaining and replaying the last completed frame implicitly.
- Falling back to browser main-thread rendering when worker prerequisites are
  missing.
- Implicit platform font discovery or fallback in version 1.
- Warping glyph outlines to follow a path.

## Resolved implementation frontier

- The common contract requires identical observable semantics, not identical
  internal topology. This resolves the Emdawn cross-worker restriction without
  weakening native implementations or moving browser rendering to main.
- The first implementation defaults are a 4 MiB command buffer, 128 MiB
  Context cache budget, 128 MiB aggregate recorder budget, one recorder, one
  queued recorder job, and two frames in flight. Cache limits remain pending
  backend plumbing and must not be described as enforced until that bridge is
  complete.
- The explicit implementation request supersedes the earlier instruction to
  wait for another approval round. Remaining naming and lifecycle corrections
  may be made from build and runtime evidence without reopening settled API
  questions.
