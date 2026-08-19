# Samples

Platform samples for validating GraphiteSurface hosts and rendering behavior.

The Android sample is built with:

```bash
./gradlew :sample:androidApp:assembleDebug
```

The Android engine uses the optimized Skia archive by default. Use
`-PgraphiteSurfaceSkiaBuildType=Debug` when native Skia debug symbols are
needed.

It requires a Vulkan-capable `arm64-v8a` device or emulator for the current
Graphite engine proof. The sample uses `GraphiteOutputMode.Surface` for the
regular Vulkan swapchain path. Select `GraphiteOutputMode.HardwareBuffer`
explicitly to exercise the API 29+ AHardwareBuffer/SurfaceControl path;
unsupported devices automatically use the swapchain fallback.
