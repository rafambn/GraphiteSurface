# GraphiteSurface Compose adapter

The primary API is a user-owned asynchronous runtime and a Compose-owned
presentation attachment:

```kotlin
val runtime = GraphiteRuntime.create(
    GraphiteRuntimeConfig(recorderCount = 4),
)

GraphiteSurface(runtime, Modifier.fillMaxSize())

val presentation = runtime.presentation
    .filterIsInstance<GraphitePresentationState.Attached>()
    .first()
    .info
val target = runtime.createRecordingTarget(presentation.pixelSize)
val recording = runtime.recorders[0].record(target) {
    draw(roads, transform = cameraMatrix)
    drawPath(route, routePaint)
}
val frame = runtime.createFrame(presentation, GraphiteColor.White) {
    insert(recording)
}
runtime.present(frame)
```

`record()` suspends for bounded queue capacity and completes asynchronously.
Recorders have stable indices and independent native threads or Web Workers.
`present()` uses a one-slot latest-wins mailbox and is the render request; the
runtime has no implicit continuous mode. Public types contain no Skia, Skiko,
WebGPU, or platform handles.

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

The older renderer callback overload remains temporarily for compatibility.
New applications should use `GraphiteRuntime`; the callback overload and its
continuous mode are not part of the intended version 1 runtime contract.

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
