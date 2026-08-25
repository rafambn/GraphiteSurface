package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.rafambn.graphitesurface.engine.WebGraphiteDrawContext as EngineDrawContext

internal class WebGraphiteDrawContext(
    private val delegate: EngineDrawContext,
) : GraphiteDrawContext {
    override fun clear(color: Long) {
        delegate.clear(color)
    }

    override fun save() {
        delegate.save()
    }

    override fun restore() {
        delegate.restore()
    }

    override fun translate(x: Float, y: Float) {
        delegate.translate(x, y)
    }

    override fun rotate(degrees: Float) {
        delegate.rotate(degrees)
    }

    override fun concat(transform: GraphiteTransform) {
        delegate.concat(FloatArray(16) { index -> transform[index / 4, index % 4] })
    }

    override fun clipRect(rect: Rect, antiAlias: Boolean) {
        delegate.clipRect(rect.left, rect.top, rect.right, rect.bottom, antiAlias)
    }

    override fun drawPath(path: GraphitePathData, paint: GraphitePaintData) {
        delegate.drawPath(
            path.verbs,
            path.points,
            path.weights,
            path.fillType,
            paint.color.toArgbLong(),
            paint.strokeWidth != null,
            paint.strokeWidth ?: 0f,
            paint.antiAlias,
        )
    }

    override fun drawRect(rect: Rect, paint: GraphitePaintData) {
        delegate.drawRect(
            rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong(), paint.strokeWidth != null,
            paint.strokeWidth ?: 0f, paint.antiAlias,
        )
    }

    override fun drawRoundRect(
        rect: Rect,
        radiusX: Float,
        radiusY: Float,
        paint: GraphitePaintData,
    ) {
        delegate.drawRoundRect(
            rect.left, rect.top, rect.right, rect.bottom, radiusX, radiusY,
            paint.color.toArgbLong(), paint.strokeWidth != null,
            paint.strokeWidth ?: 0f, paint.antiAlias,
        )
    }

    override fun drawOval(rect: Rect, paint: GraphitePaintData) {
        delegate.drawOval(
            rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong(), paint.strokeWidth != null,
            paint.strokeWidth ?: 0f, paint.antiAlias,
        )
    }

    override fun drawCircle(center: Offset, radius: Float, paint: GraphitePaintData) {
        delegate.drawCircle(
            center.x, center.y, radius,
            paint.color.toArgbLong(), paint.strokeWidth != null,
            paint.strokeWidth ?: 0f, paint.antiAlias,
        )
    }

    override fun drawLine(start: Offset, end: Offset, paint: GraphitePaintData) {
        delegate.drawLine(
            start.x, start.y, end.x, end.y,
            paint.color.toArgbLong(), paint.strokeWidth ?: 0f, paint.antiAlias,
        )
    }
}
