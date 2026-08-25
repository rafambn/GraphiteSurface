package com.rafambn.graphitesurface

/** Integer target translation applied while composing a frame. */
data class GraphiteIntOffset(val x: Int, val y: Int) {
    companion object {
        val Zero: GraphiteIntOffset = GraphiteIntOffset(0, 0)
    }
}
