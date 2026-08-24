package com.rafambn.graphitesurface

/** Observable terminal and non-terminal runtime states. */
public sealed interface GraphiteRuntimeState {
    public data object Ready : GraphiteRuntimeState
    public data class DeviceLost(public val error: Throwable) : GraphiteRuntimeState
    public data class Failed(public val failure: GraphiteFailure) : GraphiteRuntimeState
    public data object Closing : GraphiteRuntimeState
    public data object Closed : GraphiteRuntimeState
}
