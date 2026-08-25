package com.rafambn.graphitesurface

/** An axis-aligned rectangle in encoder-local coordinates. */
data class GraphiteRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            "rectangle coordinates must be finite"
        }
        require(left <= right) { "left must not exceed right" }
        require(top <= bottom) { "top must not exceed bottom" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
}
