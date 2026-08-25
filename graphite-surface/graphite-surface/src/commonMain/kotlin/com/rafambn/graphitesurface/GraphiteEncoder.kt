package com.rafambn.graphitesurface

/**
 * Scope that writes portable drawing commands on the caller thread.
 *
 * Use the direct geometry functions for content that changes or is drawn once. Use [draw] with a
 * [GraphiteDisplayList] for an immutable command group that is reused across recordings,
 * transforms, workers, or engines. A recording strongly references every display list it draws.
 */
interface GraphiteEncoder {
    fun withTransform(transform: GraphiteTransform, block: GraphiteEncoder.() -> Unit)
    fun withClip(rect: GraphiteRect, antiAlias: Boolean = false, block: GraphiteEncoder.() -> Unit)
    /** References and draws [displayList]; its command bytes are not copied into this recording. */
    fun draw(
        displayList: GraphiteDisplayList,
        transform: GraphiteTransform = GraphiteTransform.Identity,
        clip: GraphiteRect? = null,
    )
    fun drawRect(rect: GraphiteRect, paint: GraphitePaint)
    fun drawRoundRect(rect: GraphiteRect, radiusX: Float, radiusY: Float, paint: GraphitePaint)
    fun drawOval(rect: GraphiteRect, paint: GraphitePaint)
    fun drawCircle(center: GraphitePoint, radius: Float, paint: GraphitePaint)
    fun drawLine(start: GraphitePoint, end: GraphitePoint, paint: GraphitePaint)
    fun drawPath(path: GraphitePath, paint: GraphitePaint)
}
