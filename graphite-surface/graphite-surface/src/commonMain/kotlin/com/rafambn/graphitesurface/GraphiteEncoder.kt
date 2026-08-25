package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
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
    fun draw(displayList: GraphiteDisplayList)
    fun drawRect(
        rect: Rect,
        color: Color,
        style: GraphiteDrawStyle = GraphiteDrawStyle.Fill,
        antiAlias: Boolean = true,
    )
    fun drawRoundRect(
        roundRect: RoundRect,
        color: Color,
        style: GraphiteDrawStyle = GraphiteDrawStyle.Fill,
        antiAlias: Boolean = true,
    )
    fun drawOval(
        rect: Rect,
        color: Color,
        style: GraphiteDrawStyle = GraphiteDrawStyle.Fill,
        antiAlias: Boolean = true,
    )
    fun drawCircle(
        center: Offset,
        radius: Float,
        color: Color,
        style: GraphiteDrawStyle = GraphiteDrawStyle.Fill,
        antiAlias: Boolean = true,
    )
    fun drawLine(
        start: Offset,
        end: Offset,
        color: Color,
        strokeWidth: Float = 1f,
        antiAlias: Boolean = true,
    )
    fun drawPath(
        path: Path,
        color: Color,
        style: GraphiteDrawStyle = GraphiteDrawStyle.Fill,
        antiAlias: Boolean = true,
    )
}
