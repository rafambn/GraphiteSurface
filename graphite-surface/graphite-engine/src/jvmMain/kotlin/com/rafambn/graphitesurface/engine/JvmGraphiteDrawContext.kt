package com.rafambn.graphitesurface.engine

/** Drawing operations exposed by the JVM Graphite frame. */
public interface JvmGraphiteDrawContext {
    public fun clear(color: Long)

    public fun save()

    public fun restore()

    public fun translate(x: Float, y: Float)

    public fun rotate(degrees: Float)

    public fun concat(columnMajor: FloatArray)

    public fun clipRect(left: Float, top: Float, right: Float, bottom: Float, antiAlias: Boolean)

    public fun beginPath()

    public fun moveTo(x: Float, y: Float)

    public fun lineTo(x: Float, y: Float)

    public fun closePath()

    public fun drawPath(color: Long, antiAlias: Boolean)

    public fun drawPath(
        verbs: ByteArray,
        points: FloatArray,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    )

    public fun drawRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    )

    public fun drawRoundRect(
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
    )

    public fun drawOval(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    )

    public fun drawCircle(
        x: Float,
        y: Float,
        radius: Float,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    )

    public fun drawLine(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: Long,
        strokeWidth: Float,
        antiAlias: Boolean,
    )
}
