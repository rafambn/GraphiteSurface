package com.rafambn.graphitesurface

/** An axis-aligned rectangle in encoder-local coordinates. */
public data class GraphiteRect(
    public val left: Float,
    public val top: Float,
    public val right: Float,
    public val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            "rectangle coordinates must be finite"
        }
        require(left <= right) { "left must not exceed right" }
        require(top <= bottom) { "top must not exceed bottom" }
    }

    public val width: Float get() = right - left
    public val height: Float get() = bottom - top
}
