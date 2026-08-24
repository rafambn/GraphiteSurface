package com.rafambn.graphitesurface

/** Immutable path data copied from a [GraphitePathBuilder]. */
public class GraphitePath internal constructor(
    internal val verbs: ByteArray,
    internal val points: FloatArray,
) {
    init {
        var expectedPointValues = 0
        for (verb in verbs) {
            when (verb) {
                GraphitePathVerb.Move.code,
                GraphitePathVerb.Line.code,
                -> expectedPointValues += 2
                GraphitePathVerb.Close.code -> Unit
                else -> error("unknown path verb")
            }
        }
        require(points.size == expectedPointValues) { "path verb and point counts do not match" }
        require(points.all(Float::isFinite)) { "path coordinates must be finite" }
    }

    public companion object {
        public fun build(block: GraphitePathBuilder.() -> Unit): GraphitePath =
            GraphitePathBuilder().apply(block).build()
    }
}
