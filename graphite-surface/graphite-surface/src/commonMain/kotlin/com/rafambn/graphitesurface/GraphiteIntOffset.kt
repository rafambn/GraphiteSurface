package com.rafambn.graphitesurface

/** Integer target translation applied while composing a frame. */
public data class GraphiteIntOffset(public val x: Int, public val y: Int) {
    public companion object {
        public val Zero: GraphiteIntOffset = GraphiteIntOffset(0, 0)
    }
}
