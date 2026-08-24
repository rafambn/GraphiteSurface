package com.rafambn.graphitesurface

import kotlin.math.cos
import kotlin.math.sin

/** An immutable column-major 4x4 transform. */
public class GraphiteTransform private constructor(values: FloatArray) {
    private val values: FloatArray = values.copyOf()

    init {
        require(values.size == ELEMENT_COUNT) { "a transform requires 16 values" }
        require(values.all(Float::isFinite)) { "transform values must be finite" }
    }

    public operator fun get(column: Int, row: Int): Float {
        require(column in 0..3 && row in 0..3) { "matrix indices must be in 0..3" }
        return values[column * 4 + row]
    }

    internal fun copyValues(): FloatArray = values.copyOf()

    /** Returns this transform composed with [other]. */
    public operator fun times(other: GraphiteTransform): GraphiteTransform {
        val result = FloatArray(ELEMENT_COUNT)
        for (column in 0..3) {
            for (row in 0..3) {
                var value = 0f
                for (index in 0..3) value += this[index, row] * other[column, index]
                result[column * 4 + row] = value
            }
        }
        return GraphiteTransform(result)
    }

    override fun equals(other: Any?): Boolean =
        other is GraphiteTransform && values.contentEquals(other.values)

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String = "GraphiteTransform(${values.joinToString()})"

    public companion object {
        private const val ELEMENT_COUNT: Int = 16

        public val Identity: GraphiteTransform = GraphiteTransform(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            ),
        )

        public fun fromColumnMajor(values: FloatArray): GraphiteTransform = GraphiteTransform(values)

        public fun translation(x: Float, y: Float): GraphiteTransform = GraphiteTransform(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                x, y, 0f, 1f,
            ),
        )

        public fun scale(x: Float, y: Float = x): GraphiteTransform = GraphiteTransform(
            floatArrayOf(
                x, 0f, 0f, 0f,
                0f, y, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            ),
        )

        public fun rotation(degrees: Float): GraphiteTransform {
            require(degrees.isFinite()) { "degrees must be finite" }
            val radians = degrees * (kotlin.math.PI.toFloat() / 180f)
            val cosine = cos(radians)
            val sine = sin(radians)
            return GraphiteTransform(
                floatArrayOf(
                    cosine, sine, 0f, 0f,
                    -sine, cosine, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f,
                ),
            )
        }

        public fun rotationDegrees(degrees: Float): GraphiteTransform = rotation(degrees)
    }
}
