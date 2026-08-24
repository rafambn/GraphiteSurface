package com.rafambn.graphitesurface

/** Integer clip rectangle in target pixels. */
public data class GraphiteIntRect(
    public val left: Int,
    public val top: Int,
    public val right: Int,
    public val bottom: Int,
) {
    init {
        require(left <= right) { "left must not exceed right" }
        require(top <= bottom) { "top must not exceed bottom" }
    }
}
