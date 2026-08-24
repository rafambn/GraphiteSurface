package com.rafambn.graphitesurface

/** Mutable builder confined to one path-construction call. */
public class GraphitePathBuilder {
    private val verbs: MutableList<Byte> = mutableListOf()
    private val points: MutableList<Float> = mutableListOf()

    public fun moveTo(x: Float, y: Float): GraphitePathBuilder = apply {
        appendPoint(GraphitePathVerb.Move, x, y)
    }

    public fun lineTo(x: Float, y: Float): GraphitePathBuilder = apply {
        appendPoint(GraphitePathVerb.Line, x, y)
    }

    public fun close(): GraphitePathBuilder = apply {
        verbs += GraphitePathVerb.Close.code
    }

    public fun build(): GraphitePath = GraphitePath(
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
