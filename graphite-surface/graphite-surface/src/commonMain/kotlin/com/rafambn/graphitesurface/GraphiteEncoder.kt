package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/**
 * Scope that writes portable drawing commands on the caller thread.
 *
 * Use the direct geometry functions for content that changes or is drawn once. Use [draw] with a
 * [GraphiteDisplayList] for an immutable command group that is reused across recordings,
 * transforms, workers, or engines. A recording strongly references every display list it draws.
 */
interface GraphiteEncoder {
    fun withTransform(transform: GraphiteTransform, block: GraphiteEncoder.() -> Unit)
    fun withClip(rect: Rect, antiAlias: Boolean = false, block: GraphiteEncoder.() -> Unit)
    /** References and draws [displayList]; its command bytes are not copied into this recording. */
    fun draw(
        displayList: GraphiteDisplayList,
        transform: GraphiteTransform = GraphiteTransform.Identity,
        clip: Rect? = null,
    )
    fun drawRect(rect: Rect, paint: GraphitePaint)
    fun drawRoundRect(rect: Rect, radiusX: Float, radiusY: Float, paint: GraphitePaint)
    fun drawOval(rect: Rect, paint: GraphitePaint)
    fun drawCircle(center: Offset, radius: Float, paint: GraphitePaint)
    fun drawLine(start: Offset, end: Offset, paint: GraphitePaint)
    fun drawPath(path: Path, paint: GraphitePaint)
}
