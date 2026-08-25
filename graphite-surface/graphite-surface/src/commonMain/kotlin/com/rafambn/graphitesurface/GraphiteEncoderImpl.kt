package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

internal class GraphiteEncoderImpl(
    private val writer: GraphiteCommandWriter,
    private val cancellationProbe: () -> Unit,
) : GraphiteEncoder {
    override fun withTransform(transform: GraphiteTransform, block: GraphiteEncoder.() -> Unit) {
        scoped(GraphiteCommandOpcode.Transform, { writeTransform(transform) }, block)
    }

    override fun withClip(rect: Rect, antiAlias: Boolean, block: GraphiteEncoder.() -> Unit) {
        scoped(GraphiteCommandOpcode.ClipRect, {
            writeRect(rect)
            writeByte(if (antiAlias) 1 else 0)
        }, block)
    }

    override fun draw(displayList: GraphiteDisplayList, transform: GraphiteTransform, clip: Rect?) {
        cancellationProbe()
        withTransform(transform) {
            if (clip == null) {
                writeDisplayList(displayList)
            } else {
                withClip(clip) { writeDisplayList(displayList) }
            }
        }
    }

    override fun drawRect(rect: Rect, paint: GraphitePaint) {
        geometry(GraphiteCommandOpcode.DrawRect, paint) { writeRect(rect) }
    }

    override fun drawRoundRect(
        rect: Rect,
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

    override fun drawOval(rect: Rect, paint: GraphitePaint) {
        geometry(GraphiteCommandOpcode.DrawOval, paint) { writeRect(rect) }
    }

    override fun drawCircle(center: Offset, radius: Float, paint: GraphitePaint) {
        require(center.x.isFinite() && center.y.isFinite()) { "center must be finite" }
        require(radius.isFinite() && radius >= 0f) { "radius must be finite and non-negative" }
        geometry(GraphiteCommandOpcode.DrawCircle, paint) {
            writeFloat(center.x)
            writeFloat(center.y)
            writeFloat(radius)
        }
    }

    override fun drawLine(start: Offset, end: Offset, paint: GraphitePaint) {
        require(start.x.isFinite() && start.y.isFinite()) { "start must be finite" }
        require(end.x.isFinite() && end.y.isFinite()) { "end must be finite" }
        geometry(GraphiteCommandOpcode.DrawLine, paint) {
            writeFloat(start.x)
            writeFloat(start.y)
            writeFloat(end.x)
            writeFloat(end.y)
        }
    }

    override fun drawPath(path: Path, paint: GraphitePaint) {
        val snapshot = GraphitePathData.fromComposePath(path, cancellationProbe)
        geometry(GraphiteCommandOpcode.DrawPath, paint) { writePath(snapshot) }
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
