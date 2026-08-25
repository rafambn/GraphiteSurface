package com.rafambn.graphitesurface

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
    fun clipRect(rect: GraphiteRect, antiAlias: Boolean) {
        throw UnsupportedOperationException("rectangle clipping is not supported by this backend")
    }

    /** Starts a new path. */
    fun beginPath()

    /** Moves the current path to [x] and [y] without drawing. */
    fun moveTo(x: Float, y: Float)

    /** Draws a line from the current point to [x] and [y]. */
    fun lineTo(x: Float, y: Float)

    /** Closes the current path with a line back to its start. */
    fun closePath()

    /** Fills and strokes the current path with [color]. */
    fun drawPath(color: Long, antiAlias: Boolean)

    /** Draws an immutable path with [paint]. */
    fun drawPath(path: GraphitePath, paint: GraphitePaint) {
        beginPath()
        var pointIndex = 0
        path.verbs.forEach { code ->
            when (code) {
                GraphitePathVerb.Move.code -> {
                    moveTo(path.points[pointIndex], path.points[pointIndex + 1])
                    pointIndex += 2
                }
                GraphitePathVerb.Line.code -> {
                    lineTo(path.points[pointIndex], path.points[pointIndex + 1])
                    pointIndex += 2
                }
                GraphitePathVerb.Close.code -> closePath()
            }
        }
        drawPath(paint.color.toArgbLong(), paint.antiAlias)
    }

    /** Draws an axis-aligned rectangle. */
    fun drawRect(rect: GraphiteRect, paint: GraphitePaint) {
        drawPath(
            GraphitePath.build {
                moveTo(rect.left, rect.top)
                lineTo(rect.right, rect.top)
                lineTo(rect.right, rect.bottom)
                lineTo(rect.left, rect.bottom)
                close()
            },
            paint,
        )
    }

    /** Draws a rounded rectangle. */
    fun drawRoundRect(
        rect: GraphiteRect,
        radiusX: Float,
        radiusY: Float,
        paint: GraphitePaint,
    ) {
        throw UnsupportedOperationException("rounded rectangles are not supported by this backend")
    }

    /** Draws an oval bounded by [rect]. */
    fun drawOval(rect: GraphiteRect, paint: GraphitePaint) {
        throw UnsupportedOperationException("ovals are not supported by this backend")
    }

    /** Draws a circle. */
    fun drawCircle(center: GraphitePoint, radius: Float, paint: GraphitePaint) {
        throw UnsupportedOperationException("circles are not supported by this backend")
    }

    /** Draws a line segment. */
    fun drawLine(start: GraphitePoint, end: GraphitePoint, paint: GraphitePaint) {
        drawPath(
            GraphitePath.build {
                moveTo(start.x, start.y)
                lineTo(end.x, end.y)
            },
            paint,
        )
    }
}
