# graphite-surface API

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
    fun onSurfaceChanged(size: IntSize)
    fun onDrawFrame(canvas: Canvas)
}
```

The API mirrors `GLSurfaceView.Renderer`: the surface owns the native render loop (Metal + Skia Graphite on iOS), the renderer owns the scene. The drawing DSL is backed by a Graphite recorder that is submitted and presented after `onDrawFrame` returns. The engine implementation lives in this module's iOS source set and remains internal.

`GraphiteRenderMode.WhenDirty` only renders when `GraphiteSurfaceController.requestRender()` is called.

Implementation is pending on Android, JVM, JS, and Wasm.
