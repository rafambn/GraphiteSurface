package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Hosts the platform GPU surface inside Compose. */
@Composable
@ExperimentalGraphiteSurfaceApi
public expect fun GraphiteSurface(
    renderer: GraphiteRenderer,
    modifier: Modifier = Modifier,
    renderMode: GraphiteRenderMode = GraphiteRenderMode.Continuously,
    controller: GraphiteSurfaceController? = null,
    outputMode: GraphiteOutputMode = GraphiteOutputMode.Surface,
)
