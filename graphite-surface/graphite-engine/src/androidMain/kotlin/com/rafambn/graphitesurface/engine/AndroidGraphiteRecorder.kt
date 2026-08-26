@file:OptIn(org.jetbrains.skiko.ExperimentalSkikoApi::class)

package com.rafambn.graphitesurface.engine

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Matrix44
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.gpu.graphite.Recorder
import org.jetbrains.skia.gpu.graphite.TextureInfo
import org.jetbrains.skia.impl.use

/** Thread-confined native Android Graphite recorder. */
class AndroidGraphiteRecorder internal constructor(
    private val native: Recorder,
    private val textureInfo: TextureInfo,
) : AutoCloseable {
    fun record(
        width: Int,
        height: Int,
        block: AndroidGraphiteDrawContext.() -> Unit,
    ): AndroidGraphiteRecording {
        val canvas = native.makeDeferredCanvas(
            ImageInfo.makeN32Premul(width, height, ColorSpace.sRGB),
            textureInfo,
        )
        SkiaAndroidGraphiteDrawContext(canvas).block()
        return AndroidGraphiteRecording(native.snap())
    }

    override fun close() {
        native.close()
    }
}

private class SkiaAndroidGraphiteDrawContext(
    private val canvas: Canvas,
) : AndroidGraphiteDrawContext {
    override fun clear(color: Long) {
        canvas.clear(color.toInt())
    }

    override fun save() {
        canvas.save()
    }

    override fun restore() {
        canvas.restore()
    }

    override fun translate(x: Float, y: Float) {
        canvas.translate(x, y)
    }

    override fun rotate(degrees: Float) {
        canvas.rotate(degrees)
    }

    override fun concat(columnMajor: FloatArray) {
        require(columnMajor.size == 16)
        canvas.concat(
            Matrix44(
                columnMajor[0], columnMajor[4], columnMajor[8], columnMajor[12],
                columnMajor[1], columnMajor[5], columnMajor[9], columnMajor[13],
                columnMajor[2], columnMajor[6], columnMajor[10], columnMajor[14],
                columnMajor[3], columnMajor[7], columnMajor[11], columnMajor[15],
            ),
        )
    }

    override fun clipRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        antiAlias: Boolean,
    ) {
        canvas.clipRect(left, top, right, bottom, antiAlias)
    }

    override fun drawPath(
        verbs: ByteArray,
        points: FloatArray,
        weights: FloatArray,
        fillType: Int,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        val builder = PathBuilder().setFillType(
            if (fillType == 1) org.jetbrains.skia.PathFillMode.EVEN_ODD
            else org.jetbrains.skia.PathFillMode.WINDING,
        )
        var pointIndex = 0
        verbs.forEachIndexed { index, verb ->
            when (verb.toInt()) {
                1 -> builder.moveTo(points[pointIndex++], points[pointIndex++])
                2 -> builder.lineTo(points[pointIndex++], points[pointIndex++])
                3 -> builder.quadTo(
                    points[pointIndex++], points[pointIndex++],
                    points[pointIndex++], points[pointIndex++],
                )
                4 -> builder.conicTo(
                    points[pointIndex++], points[pointIndex++],
                    points[pointIndex++], points[pointIndex++], weights[index],
                )
                5 -> builder.cubicTo(
                    points[pointIndex++], points[pointIndex++],
                    points[pointIndex++], points[pointIndex++],
                    points[pointIndex++], points[pointIndex++],
                )
                6 -> builder.closePath()
                else -> error("Unknown Graphite path verb: $verb")
            }
        }
        builder.detach().use { path ->
            makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
                canvas.drawPath(path, paint)
            }
        }
    }

    override fun drawRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }

    override fun drawRoundRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radiusX: Float,
        radiusY: Float,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
            canvas.drawRRect(
                left,
                top,
                right,
                bottom,
                floatArrayOf(radiusX, radiusY, radiusX, radiusY, radiusX, radiusY, radiusX, radiusY),
                paint,
            )
        }
    }

    override fun drawOval(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
            canvas.drawOval(left, top, right, bottom, paint)
        }
    }

    override fun drawCircle(
        x: Float,
        y: Float,
        radius: Float,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    override fun drawLine(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: Long,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        makePaint(color, true, strokeWidth, antiAlias).use { paint ->
            canvas.drawLine(x0, y0, x1, y1, paint)
        }
    }

    private fun makePaint(
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    ): Paint = Paint().apply {
        this.color = color.toInt()
        mode = if (stroke) PaintMode.STROKE else PaintMode.FILL
        this.strokeWidth = strokeWidth
        isAntiAlias = antiAlias
    }
}
