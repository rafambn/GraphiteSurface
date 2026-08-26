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
import androidx.compose.ui.unit.IntSize
import com.rafambn.graphitesurface.engine.JvmGraphiteDrawContext
import com.rafambn.graphitesurface.engine.JvmGraphiteRenderer
import com.rafambn.graphitesurface.engine.JvmGraphiteSurface
import kotlinx.coroutines.isActive
import javax.swing.SwingUtilities

@Composable
@ExperimentalGraphiteSurfaceApi
internal actual fun PlatformGraphiteSurface(
    runtime: GraphiteEngine,
    renderer: GraphitePresentationRenderer,
    modifier: Modifier,
    renderMode: GraphiteRenderMode,
    state: GraphiteSurfaceState,
) {
    val adapter = remember(runtime, renderer, renderMode) {
        JvmGraphiteSurfaceAdapter(runtime, renderer)
    }
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
    runtime: GraphiteEngine,
    renderer: GraphitePresentationRenderer,
) {
    private val workers = runtime.recorders.map(GraphiteRecorder::worker)
    private val surface = JvmGraphiteSurface(
        object : JvmGraphiteRenderer {
            override fun onSurfaceCreated(
                recordingContext: com.rafambn.graphitesurface.engine.JvmGraphiteRecordingContext,
            ) {
                workers.forEach { worker -> worker.bind(recordingContext) }
                renderer.onSurfaceCreated()
            }

            override fun onSurfaceDestroyed(
                recordingContext: com.rafambn.graphitesurface.engine.JvmGraphiteRecordingContext,
            ) {
                workers.forEach(PlatformRecorderWorker::unbind)
            }

            override fun onSurfaceChanged(width: Int, height: Int) {
                renderer.onSurfaceChanged(IntSize(width, height))
            }

            override fun onDrawFrame(context: JvmGraphiteDrawContext) {
                renderer.onDrawFrame(JvmGraphiteDrawContextAdapter(context))
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
