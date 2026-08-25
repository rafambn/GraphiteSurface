@file:OptIn(
    com.rafambn.graphitesurface.ExperimentalGraphiteSurfaceApi::class,
)

package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.rafambn.graphitesurface.engine.JvmGraphiteDrawContext
import com.rafambn.graphitesurface.engine.JvmGraphiteRenderer
import com.rafambn.graphitesurface.engine.JvmGraphiteSurface
import kotlinx.coroutines.isActive
import javax.swing.SwingUtilities

@Composable
@ExperimentalGraphiteSurfaceApi
internal actual fun PlatformGraphiteSurface(
    renderer: GraphitePresentationRenderer,
    modifier: Modifier,
    renderMode: GraphiteRenderMode,
    state: GraphiteSurfaceState,
) {
    val adapter = remember(renderer, renderMode) { JvmGraphiteSurfaceAdapter(renderer) }
    var frameToken by remember { mutableStateOf(0) }

    fun requestFrame() {
        if (SwingUtilities.isEventDispatchThread()) {
            frameToken += 1
        } else {
            SwingUtilities.invokeLater { frameToken += 1 }
        }
    }

    LaunchedEffect(renderMode) {
        if (renderMode == GraphiteRenderMode.Continuous) {
            while (isActive) {
                withFrameNanos { }
                requestFrame()
            }
        }
    }

    DisposableEffect(state, renderMode) {
        val requestFrameHandler = { requestFrame() }
        if (renderMode == GraphiteRenderMode.OnDemand) {
            state.setRequestFrameHandler(requestFrameHandler)
        }
        onDispose {
            state.clearRequestFrameHandler(requestFrameHandler)
        }
    }

    DisposableEffect(adapter) {
        onDispose { adapter.close() }
    }

    SwingPanel(
        factory = { adapter.component },
        modifier = modifier,
        update = {
            frameToken
            adapter.drawFrame()
        },
    )
}

private class JvmGraphiteSurfaceAdapter(
    renderer: GraphitePresentationRenderer,
) {
    private val surface = JvmGraphiteSurface(
        object : JvmGraphiteRenderer {
            override fun onSurfaceCreated() {
                renderer.onSurfaceCreated()
            }

            override fun onSurfaceChanged(width: Int, height: Int) {
                renderer.onSurfaceChanged(GraphiteSize(width, height))
            }

            override fun onDrawFrame(context: JvmGraphiteDrawContext) {
                renderer.onDrawFrame(
                    object : GraphiteDrawContext {
                        override fun clear(color: Long) = context.clear(color)

                        override fun save() = context.save()

                        override fun restore() = context.restore()

                        override fun translate(x: Float, y: Float) = context.translate(x, y)

                        override fun rotate(degrees: Float) = context.rotate(degrees)

                        override fun concat(transform: GraphiteTransform) =
                            context.concat(FloatArray(16) { index -> transform[index / 4, index % 4] })

                        override fun clipRect(rect: Rect, antiAlias: Boolean) =
                            context.clipRect(rect.left, rect.top, rect.right, rect.bottom, antiAlias)

                        override fun drawPath(path: GraphitePathData, paint: GraphitePaint) =
                            context.drawPath(
                                path.verbs,
                                path.points,
                                path.weights,
                                path.fillType,
                                paint.color.toArgbLong(),
                                paint.style == GraphitePaint.Style.Stroke,
                                paint.strokeWidth,
                                paint.antiAlias,
                            )

                        override fun drawRect(rect: Rect, paint: GraphitePaint) =
                            context.drawRect(
                                rect.left, rect.top, rect.right, rect.bottom,
                                paint.color.toArgbLong(),
                                paint.style == GraphitePaint.Style.Stroke,
                                paint.strokeWidth,
                                paint.antiAlias,
                            )

                        override fun drawRoundRect(
                            rect: Rect,
                            radiusX: Float,
                            radiusY: Float,
                            paint: GraphitePaint,
                        ) = context.drawRoundRect(
                            rect.left, rect.top, rect.right, rect.bottom, radiusX, radiusY,
                            paint.color.toArgbLong(),
                            paint.style == GraphitePaint.Style.Stroke,
                            paint.strokeWidth,
                            paint.antiAlias,
                        )

                        override fun drawOval(rect: Rect, paint: GraphitePaint) =
                            context.drawOval(
                                rect.left, rect.top, rect.right, rect.bottom,
                                paint.color.toArgbLong(),
                                paint.style == GraphitePaint.Style.Stroke,
                                paint.strokeWidth,
                                paint.antiAlias,
                            )

                        override fun drawCircle(
                            center: Offset,
                            radius: Float,
                            paint: GraphitePaint,
                        ) = context.drawCircle(
                            center.x, center.y, radius,
                            paint.color.toArgbLong(),
                            paint.style == GraphitePaint.Style.Stroke,
                            paint.strokeWidth,
                            paint.antiAlias,
                        )

                        override fun drawLine(
                            start: Offset,
                            end: Offset,
                            paint: GraphitePaint,
                        ) = context.drawLine(
                            start.x, start.y, end.x, end.y,
                            paint.color.toArgbLong(),
                            paint.strokeWidth,
                            paint.antiAlias,
                        )
                    },
                )
            }

            override fun onSurfaceError(error: Throwable) {
                renderer.onSurfaceError(error)
            }
        },
    )

    val component get() = surface.component

    fun drawFrame() {
        surface.render()
    }

    fun close() {
        surface.close()
    }
}
