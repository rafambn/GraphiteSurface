# graphite-surface

Compose Multiplatform wrapper that hosts the isolated `GraphiteEngine.framework` in a native surface.

The iOS PoC uses `UIKitView` and a small Objective-C interop header. The Compose framework does not link the Graphite Skia classes directly.
