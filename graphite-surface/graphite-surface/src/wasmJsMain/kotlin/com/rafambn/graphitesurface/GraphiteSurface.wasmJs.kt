package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
@ExperimentalGraphiteSurfaceApi
public actual fun GraphiteSurface(
    modifier: Modifier,
    renderer: GraphiteRenderer,
) {
    error("GraphiteSurface Wasm host is not implemented yet")
}
