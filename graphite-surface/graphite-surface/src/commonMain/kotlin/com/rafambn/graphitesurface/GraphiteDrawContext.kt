package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/**
 * Library-owned drawing operations for one frame.
 *
 * The implementation is provided by the platform engine. No renderer or
 * consumer code needs to depend on Skia, Skiko, or a platform GPU API.
 */
internal interface GraphiteDrawContext {
    /** Fills the whole surface with [color] in 0xAARRGGBB format. */
    fun clear(color: Long)

    /** Pushes a copy of the current drawing state. */
    fun save()

    /** Restores the most recently saved drawing state. */
    fun restore()

    /** Translates the drawing origin by [x] and [y] pixels. */
    fun translate(x: Float, y: Float)

    /** Rotates the drawing state by [degrees] around the current origin. */
    fun rotate(degrees: Float)

    /** Concatenates an arbitrary 4x4 transform. */
    fun concat(transform: GraphiteTransform) {
        throw UnsupportedOperationException("4x4 transforms are not supported by this backend")
    }

    /** Intersects the current clip with [rect]. */
    fun clipRect(rect: Rect, antiAlias: Boolean) {
        throw UnsupportedOperationException("rectangle clipping is not supported by this backend")
    }

    /** Draws an immutable path snapshot with [paint]. */
    fun drawPath(path: GraphitePathData, paint: GraphitePaint)

    /** Draws an axis-aligned rectangle. */
    fun drawRect(rect: Rect, paint: GraphitePaint) {
        drawPath(
            GraphitePathData.fromComposePath(Path().apply {
                moveTo(rect.left, rect.top)
                lineTo(rect.right, rect.top)
                lineTo(rect.right, rect.bottom)
                lineTo(rect.left, rect.bottom)
                close()
            }),
            paint,
        )
    }

    /** Draws a rounded rectangle. */
    fun drawRoundRect(
        rect: Rect,
        radiusX: Float,
        radiusY: Float,
        paint: GraphitePaint,
    ) {
        throw UnsupportedOperationException("rounded rectangles are not supported by this backend")
    }

    /** Draws an oval bounded by [rect]. */
    fun drawOval(rect: Rect, paint: GraphitePaint) {
        throw UnsupportedOperationException("ovals are not supported by this backend")
    }

    /** Draws a circle. */
    fun drawCircle(center: Offset, radius: Float, paint: GraphitePaint) {
        throw UnsupportedOperationException("circles are not supported by this backend")
    }

    /** Draws a line segment. */
    fun drawLine(start: Offset, end: Offset, paint: GraphitePaint) {
        drawPath(
            GraphitePathData.fromComposePath(Path().apply {
                moveTo(start.x, start.y)
                lineTo(end.x, end.y)
            }),
            paint,
        )
    }
}
