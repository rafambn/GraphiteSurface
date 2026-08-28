@file:OptIn(com.rafambn.graphitesurface.ExperimentalGraphiteSurfaceApi::class)

package com.rafambn.graphitesurface

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@Composable
@ExperimentalGraphiteSurfaceApi
internal actual fun PlatformGraphiteSurface(
    runtime: GraphiteEngine,
    renderer: GraphitePresentationRenderer,
    modifier: Modifier,
    renderMode: GraphiteRenderMode,
    state: GraphiteSurfaceState,
) {
    val frameRequests = remember(runtime, renderer) { MutableStateFlow(0L) }
    val frameRequest by frameRequests.collectAsState()

    DisposableEffect(state, renderer, renderMode) {
        renderer.onSurfaceCreated()
        val requestFrameHandler = { frameRequests.update { it + 1L } }
        if (renderMode == GraphiteRenderMode.OnDemand) {
            state.setRequestFrameHandler(requestFrameHandler)
        }
        onDispose {
            state.clearRequestFrameHandler(requestFrameHandler)
        }
    }

    LaunchedEffect(renderMode) {
        if (renderMode != GraphiteRenderMode.Continuous) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            frameRequests.update { it + 1L }
        }
    }

    Canvas(
        modifier = modifier.onSizeChanged { size ->
            renderer.onSurfaceChanged(size)
            frameRequests.update { it + 1L }
        }
    ) {
        frameRequest
        try {
            renderer.onDrawFrame(
                ComposeCanvasGraphiteDrawContext(
                    canvas = drawContext.canvas,
                    size = size,
                )
            )
        } catch (error: Throwable) {
            renderer.onSurfaceError(error)
        }
    }
}
