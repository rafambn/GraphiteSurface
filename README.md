# GraphiteSurface

Asynchronous Skia Graphite runtime and Compose Multiplatform presentation host.
Applications own the runtime and recorder workers; Compose owns only surface
attachment.

## Layout

- `skiko-fork/skiko`: full Skiko fork tracked as a Git submodule. The upstream remote is `upstream`.
- `skiko-fork/skiko/skiko/skiko-graphite`: Graphite bindings from Skiko.
- `graphite-surface/graphite-surface`: public runtime, drawing DSL, and Compose Multiplatform adapter.
- `graphite-surface/graphite-engine`: iOS and Web Graphite engines with private Skiko and Skia dependencies.
- `graphite-surface/graphite-engine-android`: Android Vulkan/Graphite engine with its own pinned
  Skia archive and JNI boundary.
- `graphite-surface/sample`: platform samples.
- `build-logic`: shared Gradle conventions.

## Checkout

```bash
git clone --recurse-submodules https://github.com/rafambn/GraphiteSurface.git
```

The Skiko submodule is pinned by the root repository. To update it from JetBrains:

```bash
git -C skiko-fork/skiko fetch upstream
git -C skiko-fork/skiko switch master
git -C skiko-fork/skiko merge upstream/master
git add skiko-fork/skiko
```

The Graphite binding is currently an experimental Skiko module. macOS JVM has
a Metal host, Linux JVM has an X11/Vulkan host, and JS/Wasm use WebGPU from a
dedicated render Worker.

## Runtime API

The common API uses explicit asynchronous recording and presentation:

```kotlin
val runtime = GraphiteRuntime.create(GraphiteRuntimeConfig(recorderCount = 4))
GraphiteSurface(runtime, Modifier.fillMaxSize())

val recording = runtime.recorders[0].record(target) {
    draw(roads, transform = cameraMatrix)
}
runtime.present(runtime.createFrame(presentation) { insert(recording) })
```

Recorder queues are bounded and suspending. Calls to distinct recorder handles
can proceed in parallel. Presentation is explicit and latest-wins.

The current portable slice publishes validated immutable command programs from
the recorder workers. The platform render worker replays those commands into
its private Graphite Recorder. This preserves one API on JS, Wasm, Android,
JVM, and Apple after Emdawn proved that a WebGPU device handle cannot be used
by independent browser worker realms. Native deferred Graphite Recordings are
a later optimization behind the same public handles.

## iOS

The iOS engine renders into a `CAMetalLayer` owned by a `UIView` hosted through
Compose's `UIKitView`. Graphite calls run on a dedicated serial native queue.

Run the sample from Xcode:

```text
graphite-surface/sample/iosApp/iosApp.xcodeproj
```

The PoC renders a rotating red triangle through Graphite on the iOS Simulator.

## Android Vulkan PoC

The Android host follows the same public renderer contract:

```text
Compose AndroidView -> SurfaceView -> ANativeWindow -> Vulkan swapchain
                    -> Skia Graphite -> SurfaceView presentation
```

`graphite-engine-android` owns the Vulkan instance/device, swapchain, acquire
and present semaphores, and the Skia Graphite context. The Compose adapter only
depends on its small Java/JNI bridge; it does not import Skia or Skiko. The
Android renderer runs on a dedicated display-priority `HandlerThread`, keeps
three recorders/frame slots in flight, submits Graphite with
`SyncToCpu::kNo`, and uses a separate Vulkan completion fence for reuse
tracking. The normal frame path does not call `vkQueueWaitIdle`.

The public adapter uses the platform's native presentation path. The Android
engine presents through a Vulkan swapchain. Its experimental AHardwareBuffer
path remains an internal engine implementation while a portable zero-copy API
is designed.

It uses the engine-owned `m152-7bb45c7c26` Android debug Skia archive and
currently packages `arm64-v8a`, which matches the attached emulator.

Install Android CMake 3.22.1 and Android NDK 27.1.12297006 through the SDK manager,
then build and install the sample:

```bash
./gradlew :sample:androidApp:assembleDebug
adb install -r graphite-surface/sample/androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

The first engine build downloads the pinned Skia archive into the module build
directory.

## Web POCs

The sample shared UI also builds as separate Kotlin/JS and Kotlin/Wasm browser
executables:

```bash
source /path/to/emsdk/emsdk_env.sh   # Emscripten 4.0.7
./gradlew :sample:sharedUI:jsBrowserDevelopmentWebpack
./gradlew :sample:sharedUI:wasmJsBrowserDevelopmentWebpack
```

These are separate web implementations over the same browser engine. The HTML
canvas is transferred to an `OffscreenCanvas` owned by a module Web Worker.
That Worker owns WebGPU, Emdawn's handle registry, and Skia Graphite. Frames are acquired with
`GPUCanvasContext.getCurrentTexture()`, wrapped as a Skia Graphite Dawn
`BackendTexture`, recorded with a Graphite `Recorder`, and submitted to the
same texture. No Skia or WebGPU drawing executes on browser main. The JS build uses Kotlin/JS; the second uses Kotlin/Wasm. Both
link the local Skiko fork and its WebGPU-enabled Emscripten module, and require
a browser with WebGPU enabled. There is no WebGL or Compose Canvas fallback.

## Compose and engine versions

`gradle/libs.versions.toml` keeps these owners separate:

- `composeAdapter = 1.12.0-beta03` controls Compose dependencies and the
  Compose Gradle plugin.
- `composeCompiler = 2.4.10` controls the Kotlin Compose compiler plugin.
- `graphiteEngineSkiko = 0.152.0-alpha01` controls Skiko and Skiko Graphite
  inside `:graphite-engine`.

Compose `1.12.0-beta03` resolves Skiko `0.150.1` transitively. The iOS engine
resolves Skiko `0.152.0-alpha01` in its own project and is loaded as a separate
dynamic framework. The Android POC pins the native Skia archive independently
inside `graphite-engine-android`; its JNI boundary means the Compose adapter
does not inherit that engine implementation detail.

Run `./gradlew :verifyGraphiteSurfaceBoundary` to prevent Skiko or Skia from
leaking back into the public adapter.
