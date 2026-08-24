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
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/** Internal Compose bridge to the platform GPU host. */
@Composable
@ExperimentalGraphiteSurfaceApi
internal expect fun PlatformGraphiteSurface(
    renderer: GraphitePresentationRenderer,
    modifier: Modifier = Modifier,
    renderMode: GraphiteRenderMode = GraphiteRenderMode.Continuous,
    state: GraphiteSurfaceState = rememberGraphiteSurfaceState(),
)

/** Attaches a user-owned [runtime] to one Compose presentation target. */
@Composable
@ExperimentalGraphiteSurfaceApi
public fun GraphiteSurface(
    runtime: GraphiteRuntime,
    modifier: Modifier = Modifier,
) {
    GraphiteSurfaceHost(runtime, frameRenderer = null, modifier)
}

/** Attaches [renderer] to one Compose presentation target and drives its configured render mode. */
@Composable
@ExperimentalGraphiteSurfaceApi
public fun GraphiteSurface(
    renderer: GraphiteRenderer,
    modifier: Modifier = Modifier,
) {
    GraphiteSurfaceHost(renderer.runtime, renderer, modifier)
}

@Composable
@ExperimentalGraphiteSurfaceApi
private fun GraphiteSurfaceHost(
    runtime: GraphiteRuntime,
    frameRenderer: GraphiteRenderer?,
    modifier: Modifier,
) {
    val density = LocalDensity.current.density
    val surfaceState = rememberGraphiteSurfaceState()
    val presentationRenderer = remember(runtime) { GraphiteRuntimeRenderer(runtime) }
    var ownsAttachment by remember(runtime) { mutableStateOf(false) }

    DisposableEffect(runtime, presentationRenderer, density) {
        val attachmentId = runtime.attachPresentation(surfaceState::requestFrame)
        ownsAttachment = attachmentId != null
        if (attachmentId != null) {
            presentationRenderer.bind(attachmentId, density)
            surfaceState.requestFrame()
        }
        onDispose {
            ownsAttachment = false
            if (attachmentId != null) runtime.detachPresentation(attachmentId)
            presentationRenderer.unbind(attachmentId)
        }
    }

    if (ownsAttachment && frameRenderer != null) {
        DriveGraphiteRenderer(frameRenderer)
    }

    PlatformGraphiteSurface(
        renderer = presentationRenderer,
        modifier = modifier,
        renderMode = GraphiteRenderMode.OnDemand,
        state = surfaceState,
    )
}

@Composable
@ExperimentalGraphiteSurfaceApi
private fun DriveGraphiteRenderer(renderer: GraphiteRenderer) {
    LaunchedEffect(renderer) {
        renderer.renderModes.collectLatest { renderMode ->
            if (renderMode == GraphiteRenderMode.Manual) return@collectLatest
            renderer.runtime.presentation.collectLatest presentation@{ state ->
                val info = (state as? GraphitePresentationState.Attached)?.info
                    ?: return@presentation
                when (renderMode) {
                    GraphiteRenderMode.Continuous -> {
                        while (isActive) {
                            val frameTimeNanos = withFrameNanos { it }
                            renderer.renderScheduled(frameTimeNanos, renderMode, info)
                        }
                    }

                    GraphiteRenderMode.OnDemand -> {
                        renderer.requestAttachedFrame()
                        while (isActive) {
                            renderer.awaitRenderRequest()
                            val frameTimeNanos = withFrameNanos { it }
                            renderer.renderScheduled(frameTimeNanos, renderMode, info)
                        }
                    }

                    GraphiteRenderMode.Manual -> Unit
                }
            }
        }
    }
}
