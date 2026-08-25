package com.rafambn.graphitesurface

/** Observable state of the optional platform presentation target. */
sealed interface GraphitePresentationState {
    data object Detached : GraphitePresentationState
    data object Attaching : GraphitePresentationState
    data class Attached(val info: GraphitePresentationInfo) : GraphitePresentationState
    data class Failed(val error: Throwable) : GraphitePresentationState
}
