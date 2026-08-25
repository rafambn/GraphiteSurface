package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Offset

internal fun GraphiteDrawContext.executeGraphiteCommands(
    program: GraphiteCommandProgram,
    maximumDepth: Int = 64,
) {
    require(maximumDepth > 0) { "display-list nesting exceeds 64 levels" }
    GraphiteCommandBuffer.validate(program.commands, program.resources.size)
    val reader = GraphiteCommandReader(program.commands)
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
                val index = payload.readInt()
                executeGraphiteCommands(program.resources[index], maximumDepth - 1)
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
                val center = Offset(payload.readFloat(), payload.readFloat())
                drawCircle(center, payload.readFloat(), payload.readPaint())
            }
            GraphiteCommandOpcode.DrawLine -> {
                val start = Offset(payload.readFloat(), payload.readFloat())
                val end = Offset(payload.readFloat(), payload.readFloat())
                drawLine(start, end, payload.readPaint())
            }
            GraphiteCommandOpcode.DrawPath -> drawPath(payload.readPath(), payload.readPaint())
        }
        payload.requireFinished()
    }
}
