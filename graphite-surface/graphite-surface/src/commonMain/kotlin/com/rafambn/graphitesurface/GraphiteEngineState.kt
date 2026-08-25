package com.rafambn.graphitesurface

/** Observable terminal and non-terminal runtime states. */
sealed interface GraphiteEngineState {
    data object Ready : GraphiteEngineState
    data class Failed(val error: Throwable) : GraphiteEngineState
    data object Closing : GraphiteEngineState
    data object Closed : GraphiteEngineState
}
