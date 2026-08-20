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

The browser POCs use the same sample UI but expose separate Kotlin/JS and
Kotlin/Wasm executables from `:sample:sharedUI`. They use the local Skiko fork
to link Skia Graphite's Dawn backend into the WebGPU module; the HTML canvas is
only the swapchain host.

```bash
source /path/to/emsdk/emsdk_env.sh   # Emscripten 4.0.7
./gradlew :sample:sharedUI:jsBrowserDevelopmentWebpack
./gradlew :sample:sharedUI:wasmJsBrowserDevelopmentWebpack
```

The browser must support WebGPU. These targets do not fall back to WebGL or
Compose Canvas rendering.

To build and serve one directly, use its matching `jsBrowserDevelopmentRun` or
`wasmJsBrowserDevelopmentRun` task.
