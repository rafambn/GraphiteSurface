package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

/** Attaches [renderer] to one Compose presentation target and drives its configured render mode. */
@Composable
@ExperimentalGraphiteSurfaceApi
fun GraphiteSurface(
    renderer: GraphiteRenderer,
    modifier: Modifier = Modifier,
) {
    val runtime = renderer.runtime
    val density = LocalDensity.current.density
    val surfaceState = rememberGraphiteSurfaceState()
    val presentationRenderer = remember(runtime) { GraphiteEngineRenderer(runtime) }

    DisposableEffect(runtime, presentationRenderer, density) {
        val attachmentId = runtime.attachPresentation(surfaceState::requestFrame)
        presentationRenderer.bind(attachmentId, density)
        surfaceState.requestFrame()
        onDispose {
            runtime.detachPresentation(attachmentId)
            presentationRenderer.unbind(attachmentId)
        }
    }

    DriveGraphiteRenderer(renderer)

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
        val renderMode = renderer.renderMode
        if (renderMode == GraphiteRenderMode.Manual) return@LaunchedEffect
        renderer.runtime.presentation.collectLatest presentation@{ info ->
            info ?: return@presentation
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
