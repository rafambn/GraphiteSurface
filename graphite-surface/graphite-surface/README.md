# GraphiteSurface Compose adapter

```kotlin
@Composable
@ExperimentalGraphiteSurfaceApi
fun GraphiteSurface(
    renderer: GraphiteRenderer,
    modifier: Modifier = Modifier,
    renderMode: GraphiteRenderMode = GraphiteRenderMode.Continuously,
    controller: GraphiteSurfaceController? = null,
)

interface GraphiteRenderer {
    fun onSurfaceCreated()
    fun onSurfaceChanged(size: GraphiteSize)
    fun onDrawFrame(context: GraphiteDrawContext)
}
```

The API mirrors `GLSurfaceView.Renderer`. The Compose adapter owns only the
Compose lifecycle and the platform-view host. `GraphiteSize` and
`GraphiteDrawContext` belong to this library, so the public API contains no
Skia, Skiko, or platform GPU types.

On iOS, the adapter calls a small Objective-C ABI and hosts the returned
`UIView` through `UIKitView`. The implementation lives in the separate
`:graphite-engine` module. That module is the only module that imports Skiko
or Skia.

`GraphiteRenderMode.WhenDirty` only renders when
`GraphiteSurfaceController.requestRender()` is called.

Android, JVM/Desktop, JS, and Wasm keep the same API and currently fail fast
with an explicit unsupported-host error. Their native GPU hosts can be added
without changing the Compose-facing contract.

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
