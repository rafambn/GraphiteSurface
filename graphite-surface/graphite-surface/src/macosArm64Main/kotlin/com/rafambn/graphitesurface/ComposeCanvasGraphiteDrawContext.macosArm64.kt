package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType

internal class ComposeCanvasGraphiteDrawContext(
    private val canvas: Canvas,
    private val size: Size,
) : GraphiteDrawContext {
    override fun clear(color: Long) {
        canvas.drawRect(
            rect = Rect(Offset.Zero, size),
            paint = Paint().apply {
                this.color = color.toComposeColor()
                blendMode = BlendMode.Src
                isAntiAlias = false
            },
        )
    }

    override fun save() = canvas.save()

    override fun restore() = canvas.restore()

    override fun translate(x: Float, y: Float) = canvas.translate(x, y)

    override fun rotate(degrees: Float) = canvas.rotate(degrees)

    override fun concat(transform: GraphiteTransform) {
        canvas.concat(Matrix(transform.copyValues()))
    }

    override fun clipRect(rect: Rect, antiAlias: Boolean) {
        canvas.clipRect(rect)
    }

    override fun drawPath(path: GraphitePathData, paint: GraphitePaintData) {
        canvas.drawPath(path.toComposePath(), paint.toComposePaint())
    }

    override fun drawRect(rect: Rect, paint: GraphitePaintData) {
        canvas.drawRect(rect, paint.toComposePaint())
    }

    override fun drawRoundRect(
        rect: Rect,
        radiusX: Float,
        radiusY: Float,
        paint: GraphitePaintData,
    ) {
        canvas.drawRoundRect(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            radiusX,
            radiusY,
            paint.toComposePaint(),
        )
    }

    override fun drawOval(rect: Rect, paint: GraphitePaintData) {
        canvas.drawOval(rect, paint.toComposePaint())
    }

    override fun drawCircle(center: Offset, radius: Float, paint: GraphitePaintData) {
        canvas.drawCircle(center, radius, paint.toComposePaint())
    }

    override fun drawLine(start: Offset, end: Offset, paint: GraphitePaintData) {
        canvas.drawLine(start, end, paint.toComposePaint())
    }
}

private fun GraphitePaintData.toComposePaint(): Paint = Paint().also { paint ->
    paint.color = color
    paint.isAntiAlias = antiAlias
    paint.style = if (strokeWidth == null) PaintingStyle.Fill else PaintingStyle.Stroke
    paint.strokeWidth = strokeWidth ?: 0f
    paint.strokeCap = strokeCap
    paint.strokeJoin = strokeJoin
    paint.strokeMiterLimit = strokeMiter
}

private fun GraphitePathData.toComposePath(): Path {
    val path = Path().apply {
        fillType = if (this@toComposePath.fillType == GraphitePathData.FILL_EVEN_ODD) {
            PathFillType.EvenOdd
        } else {
            PathFillType.NonZero
        }
    }
    var pointIndex = 0
    verbs.forEach { verb ->
        when (verb) {
            GraphitePathData.VERB_MOVE -> {
                path.moveTo(points[pointIndex], points[pointIndex + 1])
                pointIndex += 2
            }

            GraphitePathData.VERB_LINE -> {
                path.lineTo(points[pointIndex], points[pointIndex + 1])
                pointIndex += 2
            }

            GraphitePathData.VERB_QUADRATIC -> {
                path.quadraticTo(
                    points[pointIndex],
                    points[pointIndex + 1],
                    points[pointIndex + 2],
                    points[pointIndex + 3],
                )
                pointIndex += 4
            }

            GraphitePathData.VERB_CONIC -> {
                path.quadraticTo(
                    points[pointIndex],
                    points[pointIndex + 1],
                    points[pointIndex + 2],
                    points[pointIndex + 3],
                )
                pointIndex += 4
            }

            GraphitePathData.VERB_CUBIC -> {
                path.cubicTo(
                    points[pointIndex],
                    points[pointIndex + 1],
                    points[pointIndex + 2],
                    points[pointIndex + 3],
                    points[pointIndex + 4],
                    points[pointIndex + 5],
                )
                pointIndex += 6
            }

            GraphitePathData.VERB_CLOSE -> path.close()
        }
    }
    return path
}
