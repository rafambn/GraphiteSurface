package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Rect

internal class GraphiteCommandWriter(private val limitBytes: Int) {
    private var bytes = ByteArray(minOf(INITIAL_CAPACITY, limitBytes))
    private var size = 0
    private val resources = mutableListOf<GraphiteCommandProgram>()
    private val resourceIndices = mutableMapOf<GraphiteCommandProgram, Int>()
    private var finished = false

    init {
        writeInt(GraphiteCommandBuffer.Magic)
        writeInt(GraphiteCommandBuffer.Version)
    }

    internal fun command(opcode: GraphiteCommandOpcode, block: GraphiteCommandWriter.() -> Unit = {}) {
        writeByte(opcode.code)
        val lengthOffset = size
        writeInt(0)
        val payloadOffset = size
        block()
        setInt(lengthOffset, size - payloadOffset)
    }

    internal fun writePaint(paint: GraphitePaintData) {
        writeInt(paint.color.toArgbLong().toInt())
        writeByte(if (paint.strokeWidth == null) 0 else 1)
        writeFloat(paint.strokeWidth ?: 0f)
        writeByte(paint.strokeCapCode)
        writeByte(paint.strokeJoinCode)
        writeFloat(paint.strokeMiter)
        writeByte(if (paint.antiAlias) 1 else 0)
    }

    internal fun writeRect(rect: Rect) {
        require(rect.left.isFinite() && rect.top.isFinite() &&
            rect.right.isFinite() && rect.bottom.isFinite()) {
            "rectangle coordinates must be finite"
        }
        require(rect.left <= rect.right) { "left must not exceed right" }
        require(rect.top <= rect.bottom) { "top must not exceed bottom" }
        writeFloat(rect.left)
        writeFloat(rect.top)
        writeFloat(rect.right)
        writeFloat(rect.bottom)
    }

    internal fun writePath(path: GraphitePathData) {
        writeInt(path.verbs.size)
        writeBytes(path.verbs)
        writeInt(path.points.size)
        path.points.forEach(::writeFloat)
        writeInt(path.weights.size)
        path.weights.forEach(::writeFloat)
        writeByte(path.fillType)
    }

    internal fun writeTransform(transform: GraphiteTransform) {
        transform.copyValues().forEach(::writeFloat)
    }

    internal fun writeByte(value: Int) {
        ensureCapacity(1)
        bytes[size++] = value.toByte()
    }

    internal fun writeInt(value: Int) {
        ensureCapacity(Int.SIZE_BYTES)
        repeat(Int.SIZE_BYTES) { index ->
            bytes[size++] = (value ushr (index * 8)).toByte()
        }
    }

    internal fun writeFloat(value: Float) {
        require(value.isFinite()) { "command values must be finite" }
        writeInt(value.toRawBits())
    }

    internal fun writeBytes(value: ByteArray) {
        ensureCapacity(value.size)
        value.copyInto(bytes, destinationOffset = size)
        size += value.size
    }

    internal fun addDisplayList(displayList: GraphiteDisplayList): Int {
        val program = displayList.program
        val existing = resourceIndices[program]
        if (existing != null) return existing
        val index = resources.size
        resources += program
        resourceIndices[program] = index
        return index
    }

    internal fun finish(): GraphiteCommandProgram {
        check(!finished) { "command writer has already finished" }
        finished = true
        return GraphiteCommandProgram(bytes.copyOf(size), resources.toList())
    }

    private fun setInt(offset: Int, value: Int) {
        repeat(Int.SIZE_BYTES) { index ->
            bytes[offset + index] = (value ushr (index * 8)).toByte()
        }
    }

    private fun ensureCapacity(additionalBytes: Int) {
        val required = size.toLong() + additionalBytes.toLong()
        if (required > limitBytes.toLong()) {
            throw GraphiteEncodingException.CommandBufferTooLarge(limitBytes)
        }
        if (required <= bytes.size) return
        var newSize = maxOf(bytes.size * 2, 16)
        while (newSize < required) newSize = minOf(newSize * 2, limitBytes)
        bytes = bytes.copyOf(newSize)
    }

    private companion object {
        const val INITIAL_CAPACITY: Int = 256
    }
}
