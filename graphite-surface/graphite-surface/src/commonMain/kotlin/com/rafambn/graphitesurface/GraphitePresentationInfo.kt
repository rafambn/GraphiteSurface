package com.rafambn.graphitesurface

/** Immutable metadata for the currently attached presentation target. */
public class GraphitePresentationInfo internal constructor(
    public val pixelSize: GraphiteSize,
    public val density: Float,
    public val generation: Long,
    internal val runtimeToken: Any,
) {
    init {
        require(pixelSize.width > 0 && pixelSize.height > 0) { "presentation size must be positive" }
        require(density.isFinite() && density > 0f) { "density must be finite and positive" }
        require(generation > 0) { "generation must be positive" }
    }
}
