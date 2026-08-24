package com.rafambn.graphitesurface

/** Observable terminal and non-terminal runtime states. */
public sealed interface GraphiteEngineState {
    public data object Ready : GraphiteEngineState
    public data class DeviceLost(public val error: Throwable) : GraphiteEngineState
    public data class Failed(public val failure: GraphiteFailure) : GraphiteEngineState
    public data object Closing : GraphiteEngineState
    public data object Closed : GraphiteEngineState
}
