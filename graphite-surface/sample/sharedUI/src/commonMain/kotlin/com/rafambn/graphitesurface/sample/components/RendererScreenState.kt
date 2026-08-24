package com.rafambn.graphitesurface.sample.components

import com.rafambn.graphitesurface.GraphiteRenderer

internal sealed interface RendererScreenState {
    data object Initializing : RendererScreenState

    data class Ready(val renderer: GraphiteRenderer) : RendererScreenState

    data class Failed(val error: Throwable) : RendererScreenState
}
