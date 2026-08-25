package com.rafambn.graphitesurface

/** Integer clip rectangle in target pixels. */
data class GraphiteIntRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left <= right) { "left must not exceed right" }
        require(top <= bottom) { "top must not exceed bottom" }
    }
}
