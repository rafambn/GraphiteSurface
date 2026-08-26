package com.rafambn.graphitesurface.engine

/** Drawing operations exposed by the JVM Graphite frame. */
interface JvmGraphiteDrawContext {
    fun insertRecording(
        recording: JvmGraphiteRecording,
        translationX: Int,
        translationY: Int,
        clipLeft: Int,
        clipTop: Int,
        clipRight: Int,
        clipBottom: Int,
        hasClip: Boolean,
    ) {
        error("This Graphite draw context cannot insert recordings")
    }

    fun clear(color: Long)

    fun save()

    fun restore()

    fun translate(x: Float, y: Float)

    fun rotate(degrees: Float)

    fun concat(columnMajor: FloatArray)

    fun clipRect(left: Float, top: Float, right: Float, bottom: Float, antiAlias: Boolean)

    fun drawPath(
        verbs: ByteArray,
        points: FloatArray,
        weights: FloatArray,
        fillType: Int,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        strokeCap: Int,
        strokeJoin: Int,
        strokeMiter: Float,
        antiAlias: Boolean,
    )

    fun drawRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    )

    fun drawRoundRect(
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

    fun drawOval(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    )

    fun drawCircle(
        x: Float,
        y: Float,
        radius: Float,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    )

    fun drawLine(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: Long,
        strokeWidth: Float,
        antiAlias: Boolean,
    )
}
