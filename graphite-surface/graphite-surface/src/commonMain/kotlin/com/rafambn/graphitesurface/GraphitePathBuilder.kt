package com.rafambn.graphitesurface

/** Mutable builder confined to one path-construction call. */
class GraphitePathBuilder {
    private val verbs = mutableListOf<Byte>()
    private val points = mutableListOf<Float>()

    fun moveTo(x: Float, y: Float): GraphitePathBuilder = apply {
        appendPoint(GraphitePathVerb.Move, x, y)
    }

    fun lineTo(x: Float, y: Float): GraphitePathBuilder = apply {
        appendPoint(GraphitePathVerb.Line, x, y)
    }

    fun close(): GraphitePathBuilder = apply {
        verbs += GraphitePathVerb.Close.code
    }

    fun build(): GraphitePath = GraphitePath(
        verbs = ByteArray(verbs.size) { verbs[it] },
        points = FloatArray(points.size) { points[it] },
    )

    private fun appendPoint(verb: GraphitePathVerb, x: Float, y: Float) {
        require(x.isFinite() && y.isFinite()) { "path coordinates must be finite" }
        verbs += verb.code
        points += x
        points += y
    }
}
