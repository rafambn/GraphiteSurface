package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect

/** Builder for an ordered frame made from completed recordings. */
class GraphiteFrameBuilder internal constructor(private val runtimeToken: Any) {
    private val insertions = mutableListOf<GraphiteFrameInsertion>()
    private var built = false

    fun insert(
        recording: GraphiteRecording,
        translation: IntOffset = IntOffset.Zero,
        clip: IntRect? = null,
    ) {
        if (recording.runtimeToken !== runtimeToken) {
            throw GraphitePresentationException("recording belongs to a different runtime")
        }
        check(!built) { "frame builder has already finished" }
        insertions += GraphiteFrameInsertion(
            program = recording.program,
            platformRecording = recording.platformRecording,
            translation = translation,
            clip = clip,
        )
    }

    internal fun build(): List<GraphiteFrameInsertion> {
        check(!built) { "frame builder has already finished" }
        built = true
        return insertions.toList()
    }
}
