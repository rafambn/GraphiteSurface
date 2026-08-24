package com.rafambn.graphitesurface.sample

import com.rafambn.graphitesurface.GraphiteRuntime

internal sealed interface GraphiteSampleUiState {
    data object Initializing : GraphiteSampleUiState

    data class Ready(val runtime: GraphiteRuntime) : GraphiteSampleUiState

    data class Failed(val error: Throwable) : GraphiteSampleUiState
}
