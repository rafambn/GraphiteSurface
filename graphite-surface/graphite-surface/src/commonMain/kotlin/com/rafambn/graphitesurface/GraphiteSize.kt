package com.rafambn.graphitesurface

/** Pixel dimensions reported by a native Graphite surface. */
public data class GraphiteSize(
    public val width: Int,
    public val height: Int,
) {
    init {
        require(width >= 0) { "width must be non-negative" }
        require(height >= 0) { "height must be non-negative" }
    }

    public companion object {
        /** An empty surface size. */
        public val Zero: GraphiteSize = GraphiteSize(0, 0)
    }
}
