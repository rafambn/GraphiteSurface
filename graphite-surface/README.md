# graphite-surface

Compose Multiplatform surface with an internal iOS Metal and Skia Graphite renderer.

The iOS PoC uses `UIKitView` and keeps the engine implementation in the same Kotlin Multiplatform module as the public surface API. The sample integrates one Compose framework and does not need a separate engine framework.
