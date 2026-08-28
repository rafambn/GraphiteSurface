# GraphiteSurface workspace

This workspace contains a Compose-facing adapter and a private multiplatform
Graphite engine. The adapter is intentionally independent from the engine's
Skiko/Skia version.

## Modules

- `graphite-surface/graphite-surface` currently contains the public
  multiplatform runtime plus the Compose adapter. It does not expose the
  engine's Skiko/Skia artifacts; all public drawing and ownership types belong
  to this library.
- `graphite-surface/graphite-engine` owns the Android, JVM, Apple, and Web
  engines. It keeps the Skiko/Skia dependency private and publishes the iOS
  engine as a dynamic `GraphiteEngine.framework`. Android delegates to the
  official Android target in our `skiko-graphite` fork.
- `graphite-surface/sample` exercises asynchronous recorders and explicit
  latest-wins presentation on Android, JVM, iOS, JS, and Wasm.

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

The Android build uses a `SurfaceView` and a dedicated display-priority render
thread. The JVM host uses Metal on macOS and Vulkan/X11 on Linux. Apple uses a
`CAMetalLayer` whose Graphite work runs on a serial native queue. Browser
targets transfer an `OffscreenCanvas` to a module Worker that exclusively owns
WebGPU and Graphite. The Kotlin/Native macOS Arm64 adapter currently replays
immutable Graphite command programs through Compose Canvas until an AppKit
Metal presentation bridge is available. Other JVM operating systems remain
unsupported.
