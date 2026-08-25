package com.rafambn.graphitesurface

/** Rare asynchronous diagnostic emitted by a runtime. */
sealed interface GraphiteEvent {
    data class PresentationAttachRejected(val reason: String) : GraphiteEvent
    data class RecordingFailed(val recorderIndex: Int, val error: Throwable) : GraphiteEvent
    data class ArchiveFailure(val error: Throwable) : GraphiteEvent
    data class FatalFailure(val failure: GraphiteFailure) : GraphiteEvent
}
