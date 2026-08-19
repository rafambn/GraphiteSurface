# GraphiteSurface workspace

This workspace contains a Compose-facing adapter and separate platform
Graphite engines. The adapter is intentionally independent from each engine's
Skiko/Skia version.

## Modules

- `graphite-surface/graphite-surface` is the public Compose Multiplatform
  adapter. It does not declare or link the engine's Skiko/Skia artifacts and
  exposes only library-owned renderer and drawing types.
- `graphite-surface/graphite-engine` is the private iOS engine. It owns the
  Skiko/Skia dependency and publishes a dynamic `GraphiteEngine.framework`
  through a small Objective-C ABI.
- `graphite-surface/graphite-engine-android` is the private Android engine. It
  owns Vulkan, the native Skia Graphite archive, and the JNI bridge.
- `graphite-surface/sample` contains the iOS, Android, and desktop samples.

## Version ownership

`composeAdapter` controls the Compose line used by the adapter and sample.
`graphiteEngineSkiko` controls the Skiko and Graphite artifacts linked into
the engine. They are deliberately different names. The current proof build
uses Compose `1.12.0-beta03` and engine Skiko `0.152.0-alpha01`.

The Compose renderer resolves Skiko transitively. With the current Compose
version it resolves `0.150.1`. That dependency belongs to Compose and does
not appear in the adapter's Gradle declarations. The engine resolves
`0.152.0-alpha01` in its own project and is loaded as a separate dynamic
iOS framework.

## iOS proof

The engine renders a rotating red triangle into a `CAMetalLayer` owned by a
`UIView`. Compose hosts that view with `UIKitView`; the sample keeps the
surface centered with a full-size centered `Box`.

Build the engine and sample framework with:

```bash
./gradlew :verifyGraphiteSurfaceBoundary
./gradlew :graphite-engine:linkDebugFrameworkIosSimulatorArm64
./gradlew :sample:sharedUI:linkDebugFrameworkIosSimulatorArm64
```

Open `graphite-surface/sample/iosApp/iosApp.xcodeproj` to build and launch the
sample. Its Xcode script embeds the matching simulator or device engine
framework beside the static `ComposeApp` framework.

The Android proof build uses a `SurfaceView` and a dedicated display-priority
render thread. It keeps three Graphite recorders/frame slots in flight and
submits with `SyncToCpu::kNo`; a separate Vulkan completion fence tracks when a
slot can be recycled. `GraphiteOutputMode.Surface` presents the Vulkan
swapchain. API 29+ also exposes the experimental
`GraphiteOutputMode.HardwareBuffer` mode, which renders to an asynchronous
three-buffer AHardwareBuffer ring and publishes through SurfaceControl, falling
back to the swapchain when capabilities are missing. The current hardware mode
validates direct buffer ownership and fences; it is not yet a Compose sampler
for the same buffer. The other Compose targets keep the same public contract,
but their native GPU hosts are explicit unsupported stubs until their platform
engines are added.
