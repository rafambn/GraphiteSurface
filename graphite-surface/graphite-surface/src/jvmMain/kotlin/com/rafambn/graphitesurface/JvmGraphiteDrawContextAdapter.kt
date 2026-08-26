package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import com.rafambn.graphitesurface.engine.JvmGraphiteDrawContext

internal class JvmGraphiteDrawContextAdapter(
    private val context: JvmGraphiteDrawContext,
) : GraphiteDrawContext {
    override fun insertRecording(
        recording: PlatformRecording,
        program: GraphiteCommandProgram,
        translation: IntOffset,
        clip: IntRect?,
    ) {
        val native = recording.native
        if (native == null) {
            super.insertRecording(recording, program, translation, clip)
            return
        }
        context.insertRecording(
            recording = native,
            translationX = translation.x,
            translationY = translation.y,
            clipLeft = clip?.left ?: 0,
            clipTop = clip?.top ?: 0,
            clipRight = clip?.right ?: 0,
            clipBottom = clip?.bottom ?: 0,
            hasClip = clip != null,
        )
    }

    override fun clear(color: Long) = context.clear(color)

    override fun save() = context.save()

    override fun restore() = context.restore()

    override fun translate(x: Float, y: Float) = context.translate(x, y)

    override fun rotate(degrees: Float) = context.rotate(degrees)

    override fun concat(transform: GraphiteTransform) =
        context.concat(FloatArray(16) { index -> transform[index / 4, index % 4] })

    override fun clipRect(rect: Rect, antiAlias: Boolean) =
        context.clipRect(rect.left, rect.top, rect.right, rect.bottom, antiAlias)

    override fun drawPath(path: GraphitePathData, paint: GraphitePaintData) =
        context.drawPath(
            path.verbs,
            path.points,
            path.weights,
            path.fillType,
            paint.color.toArgbLong(),
            paint.strokeWidth != null,
            paint.strokeWidth ?: 0f,
            paint.strokeCapCode,
            paint.strokeJoinCode,
            paint.strokeMiter,
            paint.antiAlias,
        )

    override fun drawRect(rect: Rect, paint: GraphitePaintData) =
        context.drawRect(
            rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong(), paint.strokeWidth != null,
            paint.strokeWidth ?: 0f, paint.antiAlias,
        )

    override fun drawRoundRect(
        rect: Rect,
        radiusX: Float,
        radiusY: Float,
        paint: GraphitePaintData,
    ) = context.drawRoundRect(
        rect.left, rect.top, rect.right, rect.bottom, radiusX, radiusY,
        paint.color.toArgbLong(), paint.strokeWidth != null,
        paint.strokeWidth ?: 0f, paint.antiAlias,
    )

    override fun drawOval(rect: Rect, paint: GraphitePaintData) =
        context.drawOval(
            rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong(), paint.strokeWidth != null,
            paint.strokeWidth ?: 0f, paint.antiAlias,
        )

    override fun drawCircle(center: Offset, radius: Float, paint: GraphitePaintData) =
        context.drawCircle(
            center.x, center.y, radius,
            paint.color.toArgbLong(), paint.strokeWidth != null,
            paint.strokeWidth ?: 0f, paint.antiAlias,
        )

    override fun drawLine(start: Offset, end: Offset, paint: GraphitePaintData) =
        context.drawLine(
            start.x, start.y, end.x, end.y,
            paint.color.toArgbLong(), paint.strokeWidth ?: 0f, paint.antiAlias,
        )
}
