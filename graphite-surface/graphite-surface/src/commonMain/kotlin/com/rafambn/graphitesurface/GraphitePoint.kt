package com.rafambn.graphitesurface

/** A point in encoder-local floating-point coordinates. */
data class GraphitePoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite()) { "x must be finite" }
        require(y.isFinite()) { "y must be finite" }
    }
}
