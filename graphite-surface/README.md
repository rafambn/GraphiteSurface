# GraphiteSurface workspace

This workspace contains a Compose-facing adapter and a separate iOS Graphite
engine. The adapter is intentionally independent from the engine's Skiko
version.

## Modules

- `graphite-surface/graphite-surface` is the public Compose Multiplatform
  adapter. It does not declare or link the engine's Skiko/Skia artifacts and
  exposes only library-owned renderer and drawing types.
- `graphite-surface/graphite-engine` is the private iOS engine. It owns the
  Skiko/Skia dependency and publishes a dynamic `GraphiteEngine.framework`
  through a small Objective-C ABI.
- `graphite-surface/sample` contains the iOS and desktop samples.

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

The other Compose targets keep the same public contract, but their native GPU
hosts are explicit unsupported stubs until their platform engines are added.
