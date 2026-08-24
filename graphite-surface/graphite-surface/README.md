# GraphiteSurface Compose adapter

The primary API combines a user-owned asynchronous runtime and renderer with a
Compose-owned presentation attachment:

```kotlin
val runtime = GraphiteEngine(recorderCount = 4)
val renderer = GraphiteRenderer(
    runtime = runtime,
    renderMode = GraphiteRenderMode.Continuous,
) { frameTimeNanos, presentation ->
    val recording = recorders[0].record {
        draw(roads, transform = cameraAt(frameTimeNanos))
        drawPath(route, routePaint)
    }
    val frame = createFrame(presentation, GraphiteColor.White) {
        insert(recording)
    }
    try {
        present(frame)
    } finally {
        frame.close()
        recording.close()
    }
}

GraphiteSurface(renderer, Modifier.fillMaxSize())
```

`record()` suspends for bounded queue capacity and completes asynchronously.
Recorders have stable indices and independent native threads or Web Workers.
`present()` uses a one-slot latest-wins mailbox. Public types contain no Skia,
Skiko, WebGPU, or platform handles.

`GraphiteRenderer` offers three scheduling policies:

- `Continuous` invokes the serialized callback once per available display
  frame. A slow callback naturally skips display opportunities instead of
  overlapping work.
- `OnDemand` invokes it after `requestRender()`. Requests are conflated and an
  attached surface automatically requests its first frame.
- `Manual` invokes it only through the suspending `render()` or
  `render(frameTimeNanos)` functions. The call returns `false` if no target is
  attached or the runtime is unavailable.

Changing `renderer.renderMode` takes effect on the attached surface. The
renderer callback runs only while attached and receives its runtime and the
matching `GraphitePresentationInfo`; the renderer never owns or closes its runtime.
Low-level callers may continue using `GraphiteSurface(runtime)` and schedule
their own calls to `present()`.

In this first implementation, recorder workers validate and publish the
immutable portable command program. The dedicated platform render worker owns
Skia Graphite and replays the program. The handle model permits native targets
to materialize deferred Graphite Recordings later without changing callers.

On iOS, the adapter calls a small Objective-C ABI and hosts the returned
`UIView` through `UIKitView`. The implementation lives in the separate
`:graphite-engine` module. That module is the only module that imports Skiko
or Skia.

On JS and Wasm, `GraphiteSurface` transfers its canvas to an
`OffscreenCanvas`. A dedicated module Web Worker owns WebGPU, Skia Graphite,
the swapchain, command execution, and submission. The browser main thread only
hosts Compose, observes layout, encodes portable commands, and posts messages.
JS and Wasm share the same WebGPU/Dawn ownership model.

The web POC requires Emscripten 4.0.7, the checked-out `skiko-fork`, and a
browser with WebGPU enabled. It intentionally has no WebGL or Compose Canvas
fallback.

Android, JS, Wasm, iOS, and supported JVM desktops have hosts behind the same
runtime API. Android uses a `HandlerThread`, Apple a serial native dispatch
queue, JVM a dedicated native thread, and browsers a dedicated Web Worker.

## Version ownership

The Compose adapter and the Graphite engine have separate version names in
`gradle/libs.versions.toml`:

| Owner | Catalog version | Current value |
| --- | --- | --- |
| Compose adapter and Compose plugin | `composeAdapter` | `1.12.0-beta03` |
| Compose compiler plugin | `composeCompiler` | `2.4.10` |
| Graphite engine Skiko and Skiko Graphite | `graphiteEngineSkiko` | `0.152.0-alpha01` |

Compose `1.12.0-beta03` brings its own renderer Skiko line, currently
`0.150.1`. The engine is compiled against `0.152.0-alpha01`. The adapter
does not declare either engine dependency.

## Why the iOS boundary is real

The iOS engine uses `isStatic = false` and produces
`GraphiteEngine.framework`. The sample links the Compose app as a static
framework and embeds `GraphiteEngine.framework` as a separate dynamic image.
The engine artifact is a Mach-O `MH_DYLIB` with the `TWOLEVEL` namespace and
an `@rpath/GraphiteEngine.framework/GraphiteEngine` install name. Its native
Skiko/Skia symbols live in that dynamic image. This is a native ABI and
platform-view boundary, not merely two Gradle modules with a shared static
link.

Verify the boundary with:

```bash
./gradlew :graphite-engine:linkDebugFrameworkIosSimulatorArm64
file graphite-surface/graphite-engine/build/bin/iosSimulatorArm64/debugFramework/GraphiteEngine.framework/GraphiteEngine
otool -hv graphite-surface/graphite-engine/build/bin/iosSimulatorArm64/debugFramework/GraphiteEngine.framework/GraphiteEngine
otool -L graphite-surface/graphite-engine/build/bin/iosSimulatorArm64/debugFramework/GraphiteEngine.framework/GraphiteEngine
```

The dynamic framework is required for iOS version isolation. Turning the
engine back into a static Kotlin/Native framework, or merging its native
objects into `ComposeApp`, removes the boundary and is unsupported when the
two Skiko lines differ.
