package com.rafambn.graphitesurface

internal class GraphiteEncoderImpl(
    private val writer: GraphiteCommandWriter,
    private val cancellationProbe: () -> Unit,
) : GraphiteEncoder {
    override fun withTransform(transform: GraphiteTransform, block: GraphiteEncoder.() -> Unit) {
        scoped(GraphiteCommandOpcode.Transform, { writeTransform(transform) }, block)
    }

    override fun withClip(rect: GraphiteRect, antiAlias: Boolean, block: GraphiteEncoder.() -> Unit) {
        scoped(GraphiteCommandOpcode.ClipRect, {
            writeRect(rect)
            writeByte(if (antiAlias) 1 else 0)
        }, block)
    }

    override fun draw(displayList: GraphiteDisplayList, transform: GraphiteTransform, clip: GraphiteRect?) {
        cancellationProbe()
        withTransform(transform) {
            if (clip == null) {
                writeDisplayList(displayList)
            } else {
                withClip(clip) { writeDisplayList(displayList) }
            }
        }
    }

    override fun drawRect(rect: GraphiteRect, paint: GraphitePaint) {
        geometry(GraphiteCommandOpcode.DrawRect, paint) { writeRect(rect) }
    }

    override fun drawRoundRect(
        rect: GraphiteRect,
        radiusX: Float,
        radiusY: Float,
        paint: GraphitePaint,
    ) {
        require(radiusX.isFinite() && radiusX >= 0f) { "radiusX must be finite and non-negative" }
        require(radiusY.isFinite() && radiusY >= 0f) { "radiusY must be finite and non-negative" }
        geometry(GraphiteCommandOpcode.DrawRoundRect, paint) {
            writeRect(rect)
            writeFloat(radiusX)
            writeFloat(radiusY)
        }
    }

    override fun drawOval(rect: GraphiteRect, paint: GraphitePaint) {
        geometry(GraphiteCommandOpcode.DrawOval, paint) { writeRect(rect) }
    }

    override fun drawCircle(center: GraphitePoint, radius: Float, paint: GraphitePaint) {
        require(radius.isFinite() && radius >= 0f) { "radius must be finite and non-negative" }
        geometry(GraphiteCommandOpcode.DrawCircle, paint) {
            writeFloat(center.x)
            writeFloat(center.y)
            writeFloat(radius)
        }
    }

    override fun drawLine(start: GraphitePoint, end: GraphitePoint, paint: GraphitePaint) {
        geometry(GraphiteCommandOpcode.DrawLine, paint) {
            writeFloat(start.x)
            writeFloat(start.y)
            writeFloat(end.x)
            writeFloat(end.y)
        }
    }

    override fun drawPath(path: GraphitePath, paint: GraphitePaint) {
        geometry(GraphiteCommandOpcode.DrawPath, paint) { writePath(path) }
    }

    private fun scoped(
        opcode: GraphiteCommandOpcode,
        payload: GraphiteCommandWriter.() -> Unit,
        block: GraphiteEncoder.() -> Unit,
    ) {
        cancellationProbe()
        writer.command(GraphiteCommandOpcode.Save)
        writer.command(opcode, payload)
        try {
            block()
        } finally {
            writer.command(GraphiteCommandOpcode.Restore)
        }
    }

    private fun geometry(
        opcode: GraphiteCommandOpcode,
        paint: GraphitePaint,
        geometry: GraphiteCommandWriter.() -> Unit,
    ) {
        cancellationProbe()
        writer.command(opcode) {
            geometry()
            writePaint(paint)
        }
    }

    private fun writeDisplayList(displayList: GraphiteDisplayList) {
        val resourceIndex = writer.addDisplayList(displayList)
        writer.command(GraphiteCommandOpcode.DrawDisplayList) {
            writeInt(resourceIndex)
        }
    }
}
