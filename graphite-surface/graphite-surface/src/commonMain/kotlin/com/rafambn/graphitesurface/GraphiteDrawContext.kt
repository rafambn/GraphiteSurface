package com.rafambn.graphitesurface

/**
 * Library-owned drawing operations for one frame.
 *
 * The implementation is provided by the platform engine. No renderer or
 * consumer code needs to depend on Skia, Skiko, or a platform GPU API.
 */
public interface GraphiteDrawContext {
    /** Fills the whole surface with [color] in 0xAARRGGBB format. */
    public fun clear(color: Long)

    /** Pushes a copy of the current drawing state. */
    public fun save()

    /** Restores the most recently saved drawing state. */
    public fun restore()

    /** Translates the drawing origin by [x] and [y] pixels. */
    public fun translate(x: Float, y: Float)

    /** Rotates the drawing state by [degrees] around the current origin. */
    public fun rotate(degrees: Float)

    /** Starts a new path. */
    public fun beginPath()

    /** Moves the current path to [x] and [y] without drawing. */
    public fun moveTo(x: Float, y: Float)

    /** Draws a line from the current point to [x] and [y]. */
    public fun lineTo(x: Float, y: Float)

    /** Closes the current path with a line back to its start. */
    public fun closePath()

    /** Fills and strokes the current path with [color]. */
    public fun drawPath(color: Long, antiAlias: Boolean)
}
