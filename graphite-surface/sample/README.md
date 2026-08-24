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
Graphite engine proof. The sample presents through the Vulkan swapchain. The
experimental AHardwareBuffer path remains internal while a portable zero-copy
API is designed.

The JVM/Desktop sample is built with:

```bash
./gradlew :sample:desktopApp:run
```

On macOS the host uses an AWT `CAMetalLayer` and the Skia Graphite Metal
backend. On Linux it uses an X11-backed Vulkan swapchain and the Skia Graphite
Vulkan backend. Both paths render the triangle through Graphite and do not fall
back to Compose Canvas rendering. Linux requires a Vulkan driver and an X11
display (including XWayland).

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

The web server must also send COOP `same-origin`, COEP `require-corp`, and a
WebAssembly MIME type. The repository's Bun experiment server supplies these
headers. Both browser executables have been exercised in windowed Chrome for
Testing 152 with WebGPU, two recorder Workers, the render Worker, animation,
and physical canvas resize.

To build and serve one directly, use its matching `jsBrowserDevelopmentRun` or
`wasmJsBrowserDevelopmentRun` task.
