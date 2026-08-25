package com.rafambn.graphitesurface

/** Pixel dimensions reported by a native Graphite surface. */
data class GraphiteSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width >= 0) { "width must be non-negative" }
        require(height >= 0) { "height must be non-negative" }
    }

    companion object {
        /** An empty surface size. */
        val Zero: GraphiteSize = GraphiteSize(0, 0)
    }
}
