package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
@ExperimentalGraphiteSurfaceApi
internal actual fun PlatformGraphiteSurface(
    runtime: GraphiteEngine,
    renderer: GraphitePresentationRenderer,
    modifier: Modifier,
    renderMode: GraphiteRenderMode,
    state: GraphiteSurfaceState,
) {
    WebGraphiteSurface(
        renderer = renderer,
        modifier = modifier,
        renderMode = renderMode,
        state = state,
    )
}
