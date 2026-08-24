@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rafambn.graphitesurface

import com.rafambn.graphitesurface.engine.GraphiteEngineGraphiteEngineView_iosKt
import platform.UIKit.UIView

internal class GSGraphiteDrawContext(
    private val view: UIView,
) : GraphiteDrawContext {
    override fun clear(color: Long) {
        GraphiteEngineGraphiteEngineView_iosKt.gsClearView(view, color.toUInt())
    }

    override fun save() {
        GraphiteEngineGraphiteEngineView_iosKt.gsSaveView(view)
    }

    override fun restore() {
        GraphiteEngineGraphiteEngineView_iosKt.gsRestoreView(view)
    }

    override fun translate(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsTranslateView(view, x, y)
    }

    override fun rotate(degrees: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsRotateView(view, degrees)
    }

    override fun concat(transform: GraphiteTransform) {
        GraphiteEngineGraphiteEngineView_iosKt.gsConcatView(
            view,
            transform[0, 0], transform[0, 1], transform[0, 2], transform[0, 3],
            transform[1, 0], transform[1, 1], transform[1, 2], transform[1, 3],
            transform[2, 0], transform[2, 1], transform[2, 2], transform[2, 3],
            transform[3, 0], transform[3, 1], transform[3, 2], transform[3, 3],
        )
    }

    override fun clipRect(rect: GraphiteRect, antiAlias: Boolean) {
        GraphiteEngineGraphiteEngineView_iosKt.gsClipRectView(
            view,
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            antiAlias.toNativeInt(),
        )
    }

    override fun beginPath() {
        GraphiteEngineGraphiteEngineView_iosKt.gsBeginPathView(view)
    }

    override fun moveTo(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsMoveToView(view, x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsLineToView(view, x, y)
    }

    override fun closePath() {
        GraphiteEngineGraphiteEngineView_iosKt.gsClosePathView(view)
    }

    override fun drawPath(color: Long, antiAlias: Boolean) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawPathView(
            view,
            color.toUInt(),
            if (antiAlias) 1 else 0,
        )
    }

    override fun drawPath(path: GraphitePath, paint: GraphitePaint) {
        beginPath()
        var pointIndex = 0
        path.verbs.forEach { verb ->
            when (verb.toInt()) {
                1 -> moveTo(path.points[pointIndex++], path.points[pointIndex++])
                2 -> lineTo(path.points[pointIndex++], path.points[pointIndex++])
                3 -> closePath()
                else -> error("Unknown Graphite path verb: $verb")
            }
        }
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawStyledPathView(
            view,
            paint.color.toArgbLong().toUInt(),
            paint.isStroke.toNativeInt(),
            paint.strokeWidth,
            paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawRect(rect: GraphiteRect, paint: GraphitePaint) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawRectView(
            view, rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong().toUInt(), paint.isStroke.toNativeInt(),
            paint.strokeWidth, paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawRoundRect(
        rect: GraphiteRect,
        radiusX: Float,
        radiusY: Float,
        paint: GraphitePaint,
    ) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawRoundRectView(
            view, rect.left, rect.top, rect.right, rect.bottom, radiusX, radiusY,
            paint.color.toArgbLong().toUInt(), paint.isStroke.toNativeInt(),
            paint.strokeWidth, paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawOval(rect: GraphiteRect, paint: GraphitePaint) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawOvalView(
            view, rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong().toUInt(), paint.isStroke.toNativeInt(),
            paint.strokeWidth, paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawCircle(center: GraphitePoint, radius: Float, paint: GraphitePaint) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawCircleView(
            view, center.x, center.y, radius,
            paint.color.toArgbLong().toUInt(), paint.isStroke.toNativeInt(),
            paint.strokeWidth, paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawLine(start: GraphitePoint, end: GraphitePoint, paint: GraphitePaint) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawLineView(
            view, start.x, start.y, end.x, end.y,
            paint.color.toArgbLong().toUInt(), paint.strokeWidth, paint.antiAlias.toNativeInt(),
        )
    }
}

private val GraphitePaint.isStroke: Boolean
    get() = style == GraphitePaint.Style.Stroke

private fun Boolean.toNativeInt(): Int = if (this) 1 else 0
