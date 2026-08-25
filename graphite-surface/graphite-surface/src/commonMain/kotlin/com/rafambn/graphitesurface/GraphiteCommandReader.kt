package com.rafambn.graphitesurface

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

    internal fun readRect(): GraphiteRect = GraphiteRect(
        left = readFloat(),
        top = readFloat(),
        right = readFloat(),
        bottom = readFloat(),
    )

    internal fun readTransform(): GraphiteTransform = GraphiteTransform.fromColumnMajor(
        FloatArray(16) { readFloat() },
    )

    internal fun readPaint(): GraphitePaint {
        val color = GraphiteColor(readInt().toUInt())
        val style = GraphitePaint.Style.entries.getOrNull(readByte())
            ?: error("invalid paint style")
        return GraphitePaint(
            color = color,
            style = style,
            strokeWidth = readFloat(),
            antiAlias = when (val value = readByte()) {
                0 -> false
                1 -> true
                else -> error("invalid antialias flag: $value")
            },
        )
    }

    internal fun readPath(): GraphitePath {
        val verbCount = readInt()
        if (verbCount < 0) error("negative path verb count")
        val verbs = readBytes(verbCount)
        val pointCount = readInt()
        if (pointCount < 0) error("negative path point count")
        return GraphitePath(verbs, FloatArray(pointCount) { readFloat() })
    }

    internal fun requireFinished() {
        if (position != end) error("command payload has trailing bytes")
    }

    private fun requireAvailable(size: Int) {
        if (size < 0 || end - position < size) error("command payload is truncated")
    }
}
