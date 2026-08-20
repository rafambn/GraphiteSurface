package com.rafambn.graphitesurface.engine

/** Drawing operations exposed by the JVM Graphite frame. */
public interface JvmGraphiteDrawContext {
    public fun clear(color: Long)

    public fun save()

    public fun restore()

    public fun translate(x: Float, y: Float)

    public fun rotate(degrees: Float)

    public fun beginPath()

    public fun moveTo(x: Float, y: Float)

    public fun lineTo(x: Float, y: Float)

    public fun closePath()

    public fun drawPath(color: Long, antiAlias: Boolean)
}
