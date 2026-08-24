package com.rafambn.graphitesurface

internal fun GraphiteDrawContext.executeGraphiteCommands(commands: ByteArray) {
    GraphiteCommandBuffer.validate(commands)
    val reader = GraphiteCommandReader(commands)
    check(reader.readInt() == GraphiteCommandBuffer.Magic)
    check(reader.readInt() == GraphiteCommandBuffer.Version)

    while (reader.remaining > 0) {
        val opcode = GraphiteCommandOpcode.fromCode(reader.readByte())
            ?: error("unknown command opcode")
        val payloadSize = reader.readInt()
        val payload = GraphiteCommandReader(reader.readBytes(payloadSize))

        when (opcode) {
            GraphiteCommandOpcode.Save -> save()
            GraphiteCommandOpcode.Restore -> restore()
            GraphiteCommandOpcode.Transform -> concat(payload.readTransform())
            GraphiteCommandOpcode.ClipRect -> {
                val rect = payload.readRect()
                val antiAlias = payload.readByte() != 0
                clipRect(rect, antiAlias)
            }
            GraphiteCommandOpcode.DrawDisplayList -> {
                val size = payload.readInt()
                executeGraphiteCommands(payload.readBytes(size))
            }
            GraphiteCommandOpcode.DrawRect -> drawRect(payload.readRect(), payload.readPaint())
            GraphiteCommandOpcode.DrawRoundRect -> {
                val rect = payload.readRect()
                val radiusX = payload.readFloat()
                val radiusY = payload.readFloat()
                drawRoundRect(rect, radiusX, radiusY, payload.readPaint())
            }
            GraphiteCommandOpcode.DrawOval -> drawOval(payload.readRect(), payload.readPaint())
            GraphiteCommandOpcode.DrawCircle -> {
                val center = GraphitePoint(payload.readFloat(), payload.readFloat())
                drawCircle(center, payload.readFloat(), payload.readPaint())
            }
            GraphiteCommandOpcode.DrawLine -> {
                val start = GraphitePoint(payload.readFloat(), payload.readFloat())
                val end = GraphitePoint(payload.readFloat(), payload.readFloat())
                drawLine(start, end, payload.readPaint())
            }
            GraphiteCommandOpcode.DrawPath -> drawPath(payload.readPath(), payload.readPaint())
        }
        payload.requireFinished()
    }
}
