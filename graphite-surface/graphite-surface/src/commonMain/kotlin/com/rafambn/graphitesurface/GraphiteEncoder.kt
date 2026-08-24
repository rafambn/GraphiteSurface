package com.rafambn.graphitesurface

/** Scope that writes portable drawing commands on the caller thread. */
public interface GraphiteEncoder {
    public fun withTransform(transform: GraphiteTransform, block: GraphiteEncoder.() -> Unit)
    public fun withClip(rect: GraphiteRect, antiAlias: Boolean = false, block: GraphiteEncoder.() -> Unit)
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
