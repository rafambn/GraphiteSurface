package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
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

    override fun draw(displayList: GraphiteDisplayList) {
        cancellationProbe()
        writeDisplayList(displayList)
    }

    override fun drawRect(
        rect: Rect,
        color: Color,
        style: GraphiteDrawStyle,
        antiAlias: Boolean,
    ) {
        geometry(GraphiteCommandOpcode.DrawRect, paint(color, style, antiAlias)) { writeRect(rect) }
    }

    override fun drawRoundRect(
        roundRect: RoundRect,
        color: Color,
        style: GraphiteDrawStyle,
        antiAlias: Boolean,
    ) {
        drawPath(Path().apply { addRoundRect(roundRect) }, color, style, antiAlias)
    }

    override fun drawOval(
        rect: Rect,
        color: Color,
        style: GraphiteDrawStyle,
        antiAlias: Boolean,
    ) {
        geometry(GraphiteCommandOpcode.DrawOval, paint(color, style, antiAlias)) { writeRect(rect) }
    }

    override fun drawCircle(
        center: Offset,
        radius: Float,
        color: Color,
        style: GraphiteDrawStyle,
        antiAlias: Boolean,
    ) {
        require(center.x.isFinite() && center.y.isFinite()) { "center must be finite" }
        require(radius.isFinite() && radius >= 0f) { "radius must be finite and non-negative" }
        geometry(GraphiteCommandOpcode.DrawCircle, paint(color, style, antiAlias)) {
            writeFloat(center.x)
            writeFloat(center.y)
            writeFloat(radius)
        }
    }

    override fun drawLine(
        start: Offset,
        end: Offset,
        color: Color,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        require(start.x.isFinite() && start.y.isFinite()) { "start must be finite" }
        require(end.x.isFinite() && end.y.isFinite()) { "end must be finite" }
        require(strokeWidth.isFinite() && strokeWidth >= 0f) {
            "stroke width must be finite and non-negative"
        }
        geometry(GraphiteCommandOpcode.DrawLine, GraphitePaintData(color, strokeWidth, antiAlias)) {
            writeFloat(start.x)
            writeFloat(start.y)
            writeFloat(end.x)
            writeFloat(end.y)
        }
    }

    override fun drawPath(
        path: Path,
        color: Color,
        style: GraphiteDrawStyle,
        antiAlias: Boolean,
    ) {
        val snapshot = GraphitePathData.fromComposePath(path, cancellationProbe)
        geometry(GraphiteCommandOpcode.DrawPath, paint(color, style, antiAlias)) {
            writePath(snapshot)
        }
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
        paint: GraphitePaintData,
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

    private fun paint(
        color: Color,
        style: GraphiteDrawStyle,
        antiAlias: Boolean,
    ): GraphitePaintData = GraphitePaintData(
        color = color,
        strokeWidth = (style as? GraphiteDrawStyle.Stroke)?.width,
        antiAlias = antiAlias,
    )
}
