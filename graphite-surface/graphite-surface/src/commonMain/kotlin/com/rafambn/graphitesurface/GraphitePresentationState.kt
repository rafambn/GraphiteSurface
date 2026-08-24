package com.rafambn.graphitesurface

/** Observable state of the optional platform presentation target. */
public sealed interface GraphitePresentationState {
    public data object Detached : GraphitePresentationState
    public data object Attaching : GraphitePresentationState
    public data class Attached(public val info: GraphitePresentationInfo) : GraphitePresentationState
    public data class Failed(public val error: Throwable) : GraphitePresentationState
}
