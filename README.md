# GraphiteSurface

Workspace for exposing a native GPU surface through a Compose Multiplatform
adapter.

## Layout

- `skiko-fork/skiko`: full Skiko fork tracked as a Git submodule. The upstream remote is `upstream`.
- `skiko-fork/skiko/skiko/skiko-graphite`: Graphite bindings from Skiko.
- `graphite-surface/graphite-surface`: public Compose Multiplatform adapter.
- `graphite-surface/graphite-engine`: iOS Graphite engine with private Skiko and Skia dependencies.
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

The Graphite binding is currently an experimental Skiko module. Desktop, JS,
and Wasm still keep target stubs behind the same adapter contract.

## iOS PoC

The iOS engine renders into a `CAMetalLayer` owned by a `UIView` hosted through
Compose's `UIKitView`. The public API mirrors `GLSurfaceView.Renderer`:

```kotlin
@Composable
fun GraphiteSurface(
    renderer: GraphiteRenderer,                    // onSurfaceCreated / onSurfaceChanged / onDrawFrame(context)
    modifier: Modifier = Modifier,
    renderMode: GraphiteRenderMode = Continuously, // or WhenDirty + controller.requestRender()
    controller: GraphiteSurfaceController? = null,
    outputMode: GraphiteOutputMode = Surface,
)
```

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

`GraphiteOutputMode.Surface` uses the Vulkan swapchain. On API 29+, selecting
`GraphiteOutputMode.HardwareBuffer` asks the engine to render into a three-buffer
`AHardwareBuffer` ring and publish each completed buffer through
`ASurfaceControl` with an acquire fence. If the device, driver, Skia build, or
SurfaceControl API cannot support that path, it falls back to the swapchain.
This proves the buffer and fence ownership path; it is not yet a Compose
sampler that imports the same buffer as a zero-copy image.

It uses the engine-owned `m152-7bb45c7c26` Android debug Skia archive and
currently packages `arm64-v8a`, which matches the attached emulator.

Install Android CMake 3.31.6 and an Android NDK 28.x through the SDK manager,
then build and install the sample:

```bash
./gradlew :sample:androidApp:assembleDebug
adb install -r graphite-surface/sample/androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

The first engine build downloads the pinned Skia archive into the module build
directory. The sample uses `Surface` for the regular Vulkan swapchain path.
Select `HardwareBuffer` explicitly to exercise the optional API 29+
AHardwareBuffer/SurfaceControl path while integrating a consumer-side zero-copy
bridge.

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
