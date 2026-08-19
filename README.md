# GraphiteSurface

Workspace for exposing a native GPU surface through a Compose Multiplatform
adapter.

## Layout

- `skiko-fork/skiko`: full Skiko fork tracked as a Git submodule. The upstream remote is `upstream`.
- `skiko-fork/skiko/skiko/skiko-graphite`: Graphite bindings from Skiko.
- `graphite-surface/graphite-surface`: public Compose Multiplatform adapter.
- `graphite-surface/graphite-engine`: iOS Graphite engine with private Skiko and Skia dependencies.
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

The Graphite binding is currently an experimental Skiko module. Android,
desktop, JS, and Wasm keep target stubs behind the same adapter contract until
their native engines are implemented.

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
)
```

Run the sample from Xcode:

```text
graphite-surface/sample/iosApp/iosApp.xcodeproj
```

The PoC renders a rotating red triangle through Graphite on the iOS Simulator.

## Compose and engine versions

`gradle/libs.versions.toml` keeps these owners separate:

- `composeAdapter = 1.12.0-beta03` controls Compose dependencies and the
  Compose Gradle plugin.
- `composeCompiler = 2.4.10` controls the Kotlin Compose compiler plugin.
- `graphiteEngineSkiko = 0.152.0-alpha01` controls Skiko and Skiko Graphite
  inside `:graphite-engine`.

Compose `1.12.0-beta03` resolves Skiko `0.150.1` transitively. The engine
resolves Skiko `0.152.0-alpha01`. The iOS sample keeps those native copies in
different Mach-O images. The engine framework is dynamic (`MH_DYLIB`) and
uses the Mach-O `TWOLEVEL` namespace, so the boundary is visible in the
linker output and not only in Gradle project structure.

Run `./gradlew :verifyGraphiteSurfaceBoundary` to prevent Skiko or Skia from
leaking back into the public adapter.
