package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntSize

/** Immutable metadata for the currently attached presentation target. */
class GraphitePresentationInfo internal constructor(
    val pixelSize: IntSize,
    val density: Float,
    internal val generation: Long,
    internal val runtimeToken: Any,
) {
    init {
        require(pixelSize.width > 0 && pixelSize.height > 0) { "presentation size must be positive" }
        require(density.isFinite() && density > 0f) { "density must be finite and positive" }
        require(generation > 0) { "generation must be positive" }
    }
}
