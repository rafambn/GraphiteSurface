package com.rafambn.graphitesurface

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.PathSegment

/**
 * Snapshot of a Compose path used only after the DSL has compiled it.
 *
 * The arrays are owned by Graphite and never reference the mutable Compose [Path].
 */
internal class GraphitePathData(
    internal val verbs: ByteArray,
    internal val points: FloatArray,
    internal val weights: FloatArray,
    internal val fillType: Int,
) {
    init {
        require(verbs.size == weights.size) { "path verb and weight counts do not match" }
        require(fillType == FILL_NON_ZERO || fillType == FILL_EVEN_ODD) {
            "invalid path fill type"
        }

        var expectedPointValues = 0
        verbs.forEachIndexed { index, verb ->
            expectedPointValues += when (verb) {
                VERB_MOVE -> 2
                VERB_LINE -> 2
                VERB_QUADRATIC -> 4
                VERB_CONIC -> {
                    val weight = weights[index]
                    require(weight.isFinite() && weight > 0f) {
                        "conic weight must be finite and positive"
                    }
                    4
                }
                VERB_CUBIC -> 6
                VERB_CLOSE -> 0
                else -> error("unknown path verb: $verb")
            }
            if (verb != VERB_CONIC) {
                require(weights[index] == 0f) { "non-conic path weight must be zero" }
            }
        }
        require(points.size == expectedPointValues) {
            "path verb and point counts do not match"
        }
        require(points.all(Float::isFinite)) { "path coordinates must be finite" }
    }

    internal companion object {
        const val VERB_MOVE: Byte = 1
        const val VERB_LINE: Byte = 2
        const val VERB_QUADRATIC: Byte = 3
        const val VERB_CONIC: Byte = 4
        const val VERB_CUBIC: Byte = 5
        const val VERB_CLOSE: Byte = 6

        const val FILL_NON_ZERO: Int = 0
        const val FILL_EVEN_ODD: Int = 1

        internal fun fromComposePath(
            path: Path,
            cancellationProbe: () -> Unit = {},
        ): GraphitePathData {
            val iterator = PathIterator(path, PathIterator.ConicEvaluation.AsConic)
            val verbs = mutableListOf<Byte>()
            val points = mutableListOf<Float>()
            val weights = mutableListOf<Float>()
            val segmentPoints = FloatArray(8)

            while (iterator.hasNext()) {
                cancellationProbe()
                when (iterator.next(segmentPoints)) {
                    PathSegment.Type.Move -> {
                        verbs += VERB_MOVE
                        weights += 0f
                        points += segmentPoints[0]
                        points += segmentPoints[1]
                    }
                    PathSegment.Type.Line -> {
                        verbs += VERB_LINE
                        weights += 0f
                        points += segmentPoints[2]
                        points += segmentPoints[3]
                    }
                    PathSegment.Type.Quadratic -> {
                        verbs += VERB_QUADRATIC
                        weights += 0f
                        repeat(4) { points += segmentPoints[it + 2] }
                    }
                    PathSegment.Type.Conic -> {
                        verbs += VERB_CONIC
                        weights += segmentPoints[6]
                        repeat(4) { points += segmentPoints[it + 2] }
                    }
                    PathSegment.Type.Cubic -> {
                        verbs += VERB_CUBIC
                        weights += 0f
                        repeat(6) { points += segmentPoints[it + 2] }
                    }
                    PathSegment.Type.Close -> {
                        verbs += VERB_CLOSE
                        weights += 0f
                    }
                    PathSegment.Type.Done -> break
                }
            }

            return GraphitePathData(
                verbs = ByteArray(verbs.size) { verbs[it] },
                points = FloatArray(points.size) { points[it] },
                weights = FloatArray(weights.size) { weights[it] },
                fillType = if (path.fillType == PathFillType.EvenOdd) FILL_EVEN_ODD else FILL_NON_ZERO,
            )
        }
    }
}
