package com.rafambn.graphitesurface

internal class GraphiteCommandWriter(private val limitBytes: Int) {
    private var bytes: ByteArray = ByteArray(minOf(INITIAL_CAPACITY, limitBytes))
    private var size: Int = 0
    private val resources: MutableList<GraphiteCommandProgram> = mutableListOf()
    private val resourceIndices: MutableMap<GraphiteCommandProgram, Int> = mutableMapOf()
    private var finished: Boolean = false

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

    internal fun writePaint(paint: GraphitePaint) {
        writeInt(paint.color.rgba.toInt())
        writeByte(paint.style.ordinal)
        writeFloat(paint.strokeWidth)
        writeByte(if (paint.antiAlias) 1 else 0)
    }

    internal fun writeRect(rect: GraphiteRect) {
        writeFloat(rect.left)
        writeFloat(rect.top)
        writeFloat(rect.right)
        writeFloat(rect.bottom)
    }

    internal fun writePath(path: GraphitePath) {
        writeInt(path.verbs.size)
        writeBytes(path.verbs)
        writeInt(path.points.size)
        path.points.forEach(::writeFloat)
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
