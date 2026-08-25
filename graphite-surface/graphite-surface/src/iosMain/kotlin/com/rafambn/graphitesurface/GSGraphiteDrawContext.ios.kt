@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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

    override fun clipRect(rect: Rect, antiAlias: Boolean) {
        GraphiteEngineGraphiteEngineView_iosKt.gsClipRectView(
            view,
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            antiAlias.toNativeInt(),
        )
    }

    private fun beginPath() {
        GraphiteEngineGraphiteEngineView_iosKt.gsBeginPathView(view)
    }

    private fun moveTo(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsMoveToView(view, x, y)
    }

    private fun lineTo(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsLineToView(view, x, y)
    }

    private fun closePath() {
        GraphiteEngineGraphiteEngineView_iosKt.gsClosePathView(view)
    }

    override fun drawPath(path: GraphitePathData, paint: GraphitePaintData) {
        beginPath()
        GraphiteEngineGraphiteEngineView_iosKt.gsSetPathFillTypeView(view, path.fillType)
        var pointIndex = 0
        path.verbs.forEachIndexed { index, verb ->
            when (verb.toInt()) {
                1 -> moveTo(path.points[pointIndex++], path.points[pointIndex++])
                2 -> lineTo(path.points[pointIndex++], path.points[pointIndex++])
                3 -> GraphiteEngineGraphiteEngineView_iosKt.gsQuadToView(
                    view,
                    path.points[pointIndex++], path.points[pointIndex++],
                    path.points[pointIndex++], path.points[pointIndex++],
                )
                4 -> GraphiteEngineGraphiteEngineView_iosKt.gsConicToView(
                    view,
                    path.points[pointIndex++], path.points[pointIndex++],
                    path.points[pointIndex++], path.points[pointIndex++],
                    path.weights[index],
                )
                5 -> GraphiteEngineGraphiteEngineView_iosKt.gsCubicToView(
                    view,
                    path.points[pointIndex++], path.points[pointIndex++],
                    path.points[pointIndex++], path.points[pointIndex++],
                    path.points[pointIndex++], path.points[pointIndex++],
                )
                6 -> closePath()
                else -> error("Unknown Graphite path verb: $verb")
            }
        }
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawStyledPathView(
            view,
            paint.color.toArgbLong().toUInt(),
            paint.isStroke.toNativeInt(),
            paint.strokeWidth ?: 0f,
            paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawRect(rect: Rect, paint: GraphitePaintData) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawRectView(
            view, rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong().toUInt(), paint.isStroke.toNativeInt(),
            paint.strokeWidth ?: 0f, paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawRoundRect(
        rect: Rect,
        radiusX: Float,
        radiusY: Float,
        paint: GraphitePaintData,
    ) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawRoundRectView(
            view, rect.left, rect.top, rect.right, rect.bottom, radiusX, radiusY,
            paint.color.toArgbLong().toUInt(), paint.isStroke.toNativeInt(),
            paint.strokeWidth ?: 0f, paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawOval(rect: Rect, paint: GraphitePaintData) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawOvalView(
            view, rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong().toUInt(), paint.isStroke.toNativeInt(),
            paint.strokeWidth ?: 0f, paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawCircle(center: Offset, radius: Float, paint: GraphitePaintData) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawCircleView(
            view, center.x, center.y, radius,
            paint.color.toArgbLong().toUInt(), paint.isStroke.toNativeInt(),
            paint.strokeWidth ?: 0f, paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawLine(start: Offset, end: Offset, paint: GraphitePaintData) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawLineView(
            view, start.x, start.y, end.x, end.y,
            paint.color.toArgbLong().toUInt(), paint.strokeWidth ?: 0f, paint.antiAlias.toNativeInt(),
        )
    }
}

private val GraphitePaintData.isStroke: Boolean
    get() = strokeWidth != null

private fun Boolean.toNativeInt(): Int = if (this) 1 else 0
