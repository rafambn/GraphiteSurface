package com.rafambn.graphitesurface

/**
 * Scope that writes portable drawing commands on the caller thread.
 *
 * Use the direct geometry functions for content that changes or is drawn once. Use [draw] with a
 * [GraphiteDisplayList] for an immutable command group that is reused across recordings,
 * transforms, workers, or runtimes. A recording retains every display list that it references.
 */
public interface GraphiteEncoder {
    public fun withTransform(transform: GraphiteTransform, block: GraphiteEncoder.() -> Unit)
    public fun withClip(rect: GraphiteRect, antiAlias: Boolean = false, block: GraphiteEncoder.() -> Unit)
    /** Retains and draws [displayList]; its command bytes are not copied into this recording. */
    public fun draw(
        displayList: GraphiteDisplayList,
        transform: GraphiteTransform = GraphiteTransform.Identity,
        clip: GraphiteRect? = null,
    )
    public fun drawRect(rect: GraphiteRect, paint: GraphitePaint)
    public fun drawRoundRect(rect: GraphiteRect, radiusX: Float, radiusY: Float, paint: GraphitePaint)
    public fun drawOval(rect: GraphiteRect, paint: GraphitePaint)
    public fun drawCircle(center: GraphitePoint, radius: Float, paint: GraphitePaint)
    public fun drawLine(start: GraphitePoint, end: GraphitePoint, paint: GraphitePaint)
    public fun drawPath(path: GraphitePath, paint: GraphitePaint)
}
