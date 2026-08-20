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
import com.rafambn.graphitesurface.engine.JvmGraphiteDrawContext
import com.rafambn.graphitesurface.engine.JvmGraphiteRenderer
import com.rafambn.graphitesurface.engine.JvmGraphiteSurface
import kotlinx.coroutines.isActive

@Composable
@ExperimentalGraphiteSurfaceApi
public actual fun GraphiteSurface(
    renderer: GraphiteRenderer,
    modifier: Modifier,
    renderMode: GraphiteRenderMode,
    controller: GraphiteSurfaceController?,
    outputMode: GraphiteOutputMode,
) {
    check(outputMode == GraphiteOutputMode.Surface) {
        "JVM Graphite supports GraphiteOutputMode.Surface only"
    }

    val adapter = remember(renderer, renderMode) { JvmGraphiteSurfaceAdapter(renderer) }
    var frameToken by remember { mutableStateOf(0) }

    fun requestFrame() {
        frameToken += 1
    }

    LaunchedEffect(renderMode) {
        if (renderMode == GraphiteRenderMode.Continuously) {
            while (isActive) {
                withFrameNanos { }
                requestFrame()
            }
        }
    }

    DisposableEffect(controller) {
        controller?.setRequestRenderHandler { requestFrame() }
        onDispose {
            controller?.setRequestRenderHandler(null)
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
    renderer: GraphiteRenderer,
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

                        override fun beginPath() = context.beginPath()

                        override fun moveTo(x: Float, y: Float) = context.moveTo(x, y)

                        override fun lineTo(x: Float, y: Float) = context.lineTo(x, y)

                        override fun closePath() = context.closePath()

                        override fun drawPath(color: Long, antiAlias: Boolean) =
                            context.drawPath(color, antiAlias)
                    },
                )
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
