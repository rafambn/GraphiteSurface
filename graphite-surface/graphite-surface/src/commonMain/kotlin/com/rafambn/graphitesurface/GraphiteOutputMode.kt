package com.rafambn.graphitesurface

/** Selects how a platform engine exposes the rendered image. */
public enum class GraphiteOutputMode {
    /** Presents through the platform window/surface API. */
    Surface,

    /** Uses a shared GPU-backed hardware-buffer ring when the platform supports it. */
    HardwareBuffer,
}
