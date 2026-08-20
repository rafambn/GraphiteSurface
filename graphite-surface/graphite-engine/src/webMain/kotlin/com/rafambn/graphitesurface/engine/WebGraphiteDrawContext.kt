@file:OptIn(org.jetbrains.skiko.ExperimentalSkikoApi::class)

package com.rafambn.graphitesurface.engine

import org.jetbrains.skia.Paint
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.impl.use
import org.jetbrains.skia.Canvas

/** Graphite drawing operations backed by a Skia Graphite canvas. */
public class WebGraphiteDrawContext internal constructor(
    private val canvas: Canvas,
) {
    private var path = PathBuilder()

    public fun clear(color: Long) {
        canvas.clear(color.toInt())
    }

    public fun save() {
        canvas.save()
    }

    public fun restore() {
        canvas.restore()
    }

    public fun translate(x: Float, y: Float) {
        canvas.translate(x, y)
    }

    public fun rotate(degrees: Float) {
        canvas.rotate(degrees)
    }

    public fun beginPath() {
        path = PathBuilder()
    }

    public fun moveTo(x: Float, y: Float) {
        path.moveTo(x, y)
    }

    public fun lineTo(x: Float, y: Float) {
        path.lineTo(x, y)
    }

    public fun closePath() {
        path.closePath()
    }

    public fun drawPath(color: Long, antiAlias: Boolean) {
        val path = path.detach()
        val paint = Paint().apply {
            this.color = color.toInt()
            isAntiAlias = antiAlias
        }
        try {
            canvas.drawPath(path, paint)
        } finally {
            path.close()
            paint.close()
        }
    }
}
