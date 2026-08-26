@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import com.rafambn.graphitesurface.engine.GraphiteEngineGraphiteEngineView_iosKt
import platform.UIKit.UIView

internal class GSGraphiteDrawContext(
    private val view: UIView,
    private val target: ULong = 0uL,
) : GraphiteDrawContext {
    override fun insertRecording(
        recording: PlatformRecording,
        program: GraphiteCommandProgram,
        translation: IntOffset,
        clip: IntRect?,
    ) {
        val native = recording.handle
        if (native == 0uL) {
            super.insertRecording(recording, program, translation, clip)
            return
        }
        GraphiteEngineGraphiteEngineView_iosKt.gsInsertRecordingView(
            view,
            native,
            translation.x,
            translation.y,
            clip?.left ?: 0,
            clip?.top ?: 0,
            clip?.right ?: 0,
            clip?.bottom ?: 0,
            (clip != null).toNativeInt(),
        )
    }

    override fun clear(color: Long) {
        GraphiteEngineGraphiteEngineView_iosKt.gsClearView(view, target, color.toUInt())
    }

    override fun save() {
        GraphiteEngineGraphiteEngineView_iosKt.gsSaveView(view, target)
    }

    override fun restore() {
        GraphiteEngineGraphiteEngineView_iosKt.gsRestoreView(view, target)
    }

    override fun translate(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsTranslateView(view, target, x, y)
    }

    override fun rotate(degrees: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsRotateView(view, target, degrees)
    }

    override fun concat(transform: GraphiteTransform) {
        GraphiteEngineGraphiteEngineView_iosKt.gsConcatView(
            view,
            target,
            transform[0, 0], transform[0, 1], transform[0, 2], transform[0, 3],
            transform[1, 0], transform[1, 1], transform[1, 2], transform[1, 3],
            transform[2, 0], transform[2, 1], transform[2, 2], transform[2, 3],
            transform[3, 0], transform[3, 1], transform[3, 2], transform[3, 3],
        )
    }

    override fun clipRect(rect: Rect, antiAlias: Boolean) {
        GraphiteEngineGraphiteEngineView_iosKt.gsClipRectView(
            view,
            target,
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            antiAlias.toNativeInt(),
        )
    }

    private fun beginPath() {
        GraphiteEngineGraphiteEngineView_iosKt.gsBeginPathView(view, target)
    }

    private fun moveTo(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsMoveToView(view, target, x, y)
    }

    private fun lineTo(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsLineToView(view, target, x, y)
    }

    private fun closePath() {
        GraphiteEngineGraphiteEngineView_iosKt.gsClosePathView(view, target)
    }

    override fun drawPath(path: GraphitePathData, paint: GraphitePaintData) {
        beginPath()
        GraphiteEngineGraphiteEngineView_iosKt.gsSetPathFillTypeView(view, target, path.fillType)
        var pointIndex = 0
        path.verbs.forEachIndexed { index, verb ->
            when (verb.toInt()) {
                1 -> moveTo(path.points[pointIndex++], path.points[pointIndex++])
                2 -> lineTo(path.points[pointIndex++], path.points[pointIndex++])
                3 -> GraphiteEngineGraphiteEngineView_iosKt.gsQuadToView(
                    view,
                    target,
                    path.points[pointIndex++], path.points[pointIndex++],
                    path.points[pointIndex++], path.points[pointIndex++],
                )
                4 -> GraphiteEngineGraphiteEngineView_iosKt.gsConicToView(
                    view,
                    target,
                    path.points[pointIndex++], path.points[pointIndex++],
                    path.points[pointIndex++], path.points[pointIndex++],
                    path.weights[index],
                )
                5 -> GraphiteEngineGraphiteEngineView_iosKt.gsCubicToView(
                    view,
                    target,
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
            target,
            paint.color.toArgbLong().toUInt(),
            paint.isStroke.toNativeInt(),
            paint.strokeWidth ?: 0f,
            paint.strokeCapCode,
            paint.strokeJoinCode,
            paint.strokeMiter,
            paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawRect(rect: Rect, paint: GraphitePaintData) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawRectView(
            view, target, rect.left, rect.top, rect.right, rect.bottom,
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
            view, target, rect.left, rect.top, rect.right, rect.bottom, radiusX, radiusY,
            paint.color.toArgbLong().toUInt(), paint.isStroke.toNativeInt(),
            paint.strokeWidth ?: 0f, paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawOval(rect: Rect, paint: GraphitePaintData) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawOvalView(
            view, target, rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong().toUInt(), paint.isStroke.toNativeInt(),
            paint.strokeWidth ?: 0f, paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawCircle(center: Offset, radius: Float, paint: GraphitePaintData) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawCircleView(
            view, target, center.x, center.y, radius,
            paint.color.toArgbLong().toUInt(), paint.isStroke.toNativeInt(),
            paint.strokeWidth ?: 0f, paint.antiAlias.toNativeInt(),
        )
    }

    override fun drawLine(start: Offset, end: Offset, paint: GraphitePaintData) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawLineView(
            view, target, start.x, start.y, end.x, end.y,
            paint.color.toArgbLong().toUInt(), paint.strokeWidth ?: 0f, paint.antiAlias.toNativeInt(),
        )
    }
}

private val GraphitePaintData.isStroke: Boolean
    get() = strokeWidth != null

private fun Boolean.toNativeInt(): Int = if (this) 1 else 0
