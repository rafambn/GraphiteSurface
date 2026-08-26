package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

internal class GraphiteCommandReader(
    private val bytes: ByteArray,
    start: Int = 0,
    private val end: Int = bytes.size,
) {
    internal var position = start
        private set

    internal val remaining: Int get() = end - position

    internal fun readByte(): Int {
        requireAvailable(1)
        return bytes[position++].toInt() and 0xFF
    }

    internal fun readInt(): Int {
        val value = GraphiteCommandBuffer.readInt(bytes, position)
        position += Int.SIZE_BYTES
        if (position > end) error("command payload is truncated")
        return value
    }

    internal fun readLong(): Long {
        requireAvailable(Long.SIZE_BYTES)
        var value = 0L
        repeat(Long.SIZE_BYTES) { index ->
            value = value or ((bytes[position++].toLong() and 0xFFL) shl (index * 8))
        }
        return value
    }

    internal fun readFloat(): Float = Float.fromBits(readInt()).also {
        if (!it.isFinite()) error("command contains a non-finite value")
    }

    internal fun readBytes(size: Int): ByteArray {
        require(size >= 0) { "byte count must be non-negative" }
        requireAvailable(size)
        return bytes.copyOfRange(position, position + size).also { position += size }
    }

    internal fun readRect(): Rect {
        val rect = Rect(
            left = readFloat(),
            top = readFloat(),
            right = readFloat(),
            bottom = readFloat(),
        )
        require(rect.left <= rect.right) { "left must not exceed right" }
        require(rect.top <= rect.bottom) { "top must not exceed bottom" }
        return rect
    }

    internal fun readTransform(): GraphiteTransform = GraphiteTransform.fromColumnMajor(
        FloatArray(16) { readFloat() },
    )

    internal fun readPaint(): GraphitePaintData {
        val color = readInt().toLong().toComposeColor()
        val isStroke = when (val value = readByte()) {
            0 -> false
            1 -> true
            else -> error("invalid paint style: $value")
        }
        val strokeWidth = readFloat()
        val strokeCap = when (val value = readByte()) {
            0 -> StrokeCap.Butt
            1 -> StrokeCap.Round
            2 -> StrokeCap.Square
            else -> error("invalid stroke cap: $value")
        }
        val strokeJoin = when (val value = readByte()) {
            0 -> StrokeJoin.Miter
            1 -> StrokeJoin.Round
            2 -> StrokeJoin.Bevel
            else -> error("invalid stroke join: $value")
        }
        val strokeMiter = readFloat()
        if (strokeMiter < 0f) error("stroke miter must be non-negative")
        return GraphitePaintData(
            color = color,
            strokeWidth = if (isStroke) strokeWidth else null,
            strokeCap = strokeCap,
            strokeJoin = strokeJoin,
            strokeMiter = strokeMiter,
            antiAlias = when (val value = readByte()) {
                0 -> false
                1 -> true
                else -> error("invalid antialias flag: $value")
            },
        )
    }

    internal fun readPath(): GraphitePathData {
        val verbCount = readInt()
        if (verbCount < 0) error("negative path verb count")
        val verbs = readBytes(verbCount)
        val pointCount = readInt()
        if (pointCount < 0) error("negative path point count")
        val points = FloatArray(pointCount) { readFloat() }
        val weightCount = readInt()
        if (weightCount < 0) error("negative path weight count")
        val weights = FloatArray(weightCount) { readFloat() }
        val fillType = readByte()
        return GraphitePathData(verbs, points, weights, fillType)
    }

    internal fun requireFinished() {
        if (position != end) error("command payload has trailing bytes")
    }

    private fun requireAvailable(size: Int) {
        if (size < 0 || end - position < size) error("command payload is truncated")
    }
}
