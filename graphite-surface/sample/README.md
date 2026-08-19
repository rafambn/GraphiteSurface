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
Graphite engine proof. The sample opts into `GraphiteOutputMode.HardwareBuffer`
to exercise the API 29+ AHardwareBuffer/SurfaceControl path; unsupported
devices automatically use the Vulkan swapchain fallback.
