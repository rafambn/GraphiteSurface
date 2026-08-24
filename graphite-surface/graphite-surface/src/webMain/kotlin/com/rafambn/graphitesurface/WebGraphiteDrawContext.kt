package com.rafambn.graphitesurface

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

    override fun clipRect(rect: GraphiteRect, antiAlias: Boolean) {
        delegate.clipRect(rect.left, rect.top, rect.right, rect.bottom, antiAlias)
    }

    override fun beginPath() {
        delegate.beginPath()
    }

    override fun moveTo(x: Float, y: Float) {
        delegate.moveTo(x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        delegate.lineTo(x, y)
    }

    override fun closePath() {
        delegate.closePath()
    }

    override fun drawPath(color: Long, antiAlias: Boolean) {
        delegate.drawPath(color, antiAlias)
    }

    override fun drawPath(path: GraphitePath, paint: GraphitePaint) {
        delegate.drawPath(
            path.verbs,
            path.points,
            paint.color.toArgbLong(),
            paint.style == GraphitePaint.Style.Stroke,
            paint.strokeWidth,
            paint.antiAlias,
        )
    }

    override fun drawRect(rect: GraphiteRect, paint: GraphitePaint) {
        delegate.drawRect(
            rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong(), paint.style == GraphitePaint.Style.Stroke,
            paint.strokeWidth, paint.antiAlias,
        )
    }

    override fun drawRoundRect(
        rect: GraphiteRect,
        radiusX: Float,
        radiusY: Float,
        paint: GraphitePaint,
    ) {
        delegate.drawRoundRect(
            rect.left, rect.top, rect.right, rect.bottom, radiusX, radiusY,
            paint.color.toArgbLong(), paint.style == GraphitePaint.Style.Stroke,
            paint.strokeWidth, paint.antiAlias,
        )
    }

    override fun drawOval(rect: GraphiteRect, paint: GraphitePaint) {
        delegate.drawOval(
            rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong(), paint.style == GraphitePaint.Style.Stroke,
            paint.strokeWidth, paint.antiAlias,
        )
    }

    override fun drawCircle(center: GraphitePoint, radius: Float, paint: GraphitePaint) {
        delegate.drawCircle(
            center.x, center.y, radius,
            paint.color.toArgbLong(), paint.style == GraphitePaint.Style.Stroke,
            paint.strokeWidth, paint.antiAlias,
        )
    }

    override fun drawLine(start: GraphitePoint, end: GraphitePoint, paint: GraphitePaint) {
        delegate.drawLine(
            start.x, start.y, end.x, end.y,
            paint.color.toArgbLong(), paint.strokeWidth, paint.antiAlias,
        )
    }
}
