package com.rafambn.graphitesurface

/** A point in encoder-local floating-point coordinates. */
public data class GraphitePoint(public val x: Float, public val y: Float) {
    init {
        require(x.isFinite()) { "x must be finite" }
        require(y.isFinite()) { "y must be finite" }
    }
}
