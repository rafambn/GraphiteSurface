package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/** Internal Compose bridge to the platform GPU host. */
@Composable
@ExperimentalGraphiteSurfaceApi
internal expect fun PlatformGraphiteSurface(
    renderer: GraphiteRenderer,
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
    val density = LocalDensity.current.density
    val surfaceState = rememberGraphiteSurfaceState()
    val renderer = remember(runtime) { GraphiteRuntimeRenderer(runtime) }

    DisposableEffect(runtime, renderer, density) {
        val attachmentId = runtime.attachPresentation(surfaceState::requestFrame)
        if (attachmentId != null) {
            renderer.bind(attachmentId, density)
            surfaceState.requestFrame()
        }
        onDispose {
            if (attachmentId != null) runtime.detachPresentation(attachmentId)
            renderer.unbind(attachmentId)
        }
    }

    PlatformGraphiteSurface(
        renderer = renderer,
        modifier = modifier,
        renderMode = GraphiteRenderMode.OnDemand,
        state = surfaceState,
    )
}
