package com.rafambn.graphitesurface

internal object GraphiteCommandBuffer {
    internal const val Magic: Int = 0x47534631
    internal const val Version: Int = 2
    private const val HeaderBytes: Int = Int.SIZE_BYTES * 2
    private const val CommandHeaderBytes: Int = 1 + Int.SIZE_BYTES

    internal fun validate(bytes: ByteArray, resourceCount: Int = 0) {
        require(resourceCount >= 0) { "resource count must be non-negative" }
        if (bytes.size < HeaderBytes) error("command buffer is truncated")
        if (readInt(bytes, 0) != Magic) error("invalid command-buffer magic")
        if (readInt(bytes, Int.SIZE_BYTES) != Version) error("unsupported command-buffer version")

        var offset = HeaderBytes
        var saveDepth = 0
        while (offset < bytes.size) {
            if (bytes.size - offset < CommandHeaderBytes) error("truncated command header")
            val opcode = GraphiteCommandOpcode.fromCode(bytes[offset].toInt() and 0xFF)
                ?: error("unknown command opcode")
            val payloadSize = readInt(bytes, offset + 1)
            if (payloadSize < 0) error("negative command payload")
            val payloadOffset = offset + CommandHeaderBytes
            val nextOffset = payloadOffset.toLong() + payloadSize.toLong()
            if (nextOffset > bytes.size) error("truncated command payload")
            val payload = GraphiteCommandReader(bytes, payloadOffset, nextOffset.toInt())

            when (opcode) {
                GraphiteCommandOpcode.Save -> saveDepth += 1
                GraphiteCommandOpcode.Restore -> {
                    if (saveDepth == 0) error("restore without matching save")
                    saveDepth -= 1
                }
                GraphiteCommandOpcode.DrawDisplayList -> {
                    val index = payload.readInt()
                    if (index !in 0 until resourceCount) error("invalid display-list resource index")
                }
                GraphiteCommandOpcode.Transform -> payload.readTransform()
                GraphiteCommandOpcode.ClipRect -> {
                    payload.readRect()
                    payload.readBoolean("clip antialias")
                }
                GraphiteCommandOpcode.DrawRect -> {
                    payload.readRect()
                    payload.readPaint()
                }
                GraphiteCommandOpcode.DrawRoundRect -> {
                    payload.readRect()
                    requireNonNegative(payload.readFloat(), "radiusX")
                    requireNonNegative(payload.readFloat(), "radiusY")
                    payload.readPaint()
                }
                GraphiteCommandOpcode.DrawOval -> {
                    payload.readRect()
                    payload.readPaint()
                }
                GraphiteCommandOpcode.DrawCircle -> {
                    payload.readFloat()
                    payload.readFloat()
                    requireNonNegative(payload.readFloat(), "radius")
                    payload.readPaint()
                }
                GraphiteCommandOpcode.DrawLine -> {
                    repeat(4) { payload.readFloat() }
                    payload.readPaint()
                }
                GraphiteCommandOpcode.DrawPath -> {
                    payload.readPath()
                    payload.readPaint()
                }
            }
            payload.requireFinished()
            offset = nextOffset.toInt()
        }
        if (saveDepth != 0) error("unbalanced save and restore commands")
    }

    internal fun readInt(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || bytes.size - offset < Int.SIZE_BYTES) error("truncated integer")
        var value = 0
        repeat(Int.SIZE_BYTES) { index ->
            value = value or ((bytes[offset + index].toInt() and 0xFF) shl (index * 8))
        }
        return value
    }

    private fun GraphiteCommandReader.readBoolean(label: String) {
        when (val value = readByte()) {
            0, 1 -> Unit
            else -> error("invalid $label flag: $value")
        }
    }

    private fun requireNonNegative(value: Float, label: String) {
        if (value < 0f) error("$label must be non-negative")
    }
}
