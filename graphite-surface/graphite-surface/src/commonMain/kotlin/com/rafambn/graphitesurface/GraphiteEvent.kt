package com.rafambn.graphitesurface

/** Rare asynchronous diagnostic emitted by a runtime. */
public sealed interface GraphiteEvent {
    public data class PresentationAttachRejected(public val reason: String) : GraphiteEvent
    public data class RecordingFailed(public val recorderIndex: Int, public val error: Throwable) : GraphiteEvent
    public data class ArchiveFailure(public val error: Throwable) : GraphiteEvent
    public data class FatalFailure(public val failure: GraphiteFailure) : GraphiteEvent
}
