# graphite-surface API

The current PoC exposes:

```kotlin
@Composable
fun GraphiteSurface(modifier: Modifier = Modifier)
```

On iOS it hosts the isolated Graphite engine through `UIKitView`. The engine currently renders a rotating red triangle as its smoke test.

Implementation is pending.
