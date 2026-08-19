package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
@ExperimentalGraphiteSurfaceApi
public actual fun GraphiteSurface(
    renderer: GraphiteRenderer,
    modifier: Modifier,
    renderMode: GraphiteRenderMode,
    controller: GraphiteSurfaceController?,
) {
    error("GraphiteSurface Wasm host is not implemented yet")
}