# Android Graphite engine

This module is the Android counterpart of the private iOS engine. It is a
regular Android library so the Compose Multiplatform adapter can consume it
without exposing Skia or Skiko types.

The native proof path is:

```text
SurfaceView -> ANativeWindow -> Vulkan/Graphite render thread
           -> swapchain present or AHardwareBuffer -> SurfaceControl
```

The module downloads the pinned JetBrains Skia `m152-7bb45c7c26` Android debug
archive during `prepareSkiaAndroid`. The first proof targets `arm64-v8a`, uses
Vulkan 1.1, and links the C++ runtime statically. The swapchain is synchronized
with Graphite `BackendSemaphore` wait/signal semaphores. Three independent
recorders and frame slots allow CPU recording, GPU submission, and presentation
to overlap. Graphite submits with `SyncToCpu::kNo`; a follow-up Vulkan submit
signals a per-slot fence so the render thread can recycle resources without
blocking the Compose/UI thread.

On API 29+, the optional hardware-buffer mode allocates three compatible
AHardwareBuffers, imports each through Skia Graphite, and publishes them using
SurfaceControl transactions with Vulkan acquire fences. Capability checks and
Skia's Vulkan extension table are required; unsupported combinations fall back
to the swapchain. The current mode validates direct hardware-buffer ownership
and asynchronous presentation. A future Compose zero-copy consumer still needs
to import the same buffer ring rather than copying it through CPU memory.
