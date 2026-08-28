package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect

/** Builder for an ordered frame made from completed recordings. */
class GraphiteFrameBuilder internal constructor(private val runtimeToken: Any) {
    private val insertions = mutableListOf<GraphiteFrameInsertion>()
    private var built = false

    /** Inserts [recording] with an integer translation for source compatibility. */
    fun insert(
        recording: GraphiteRecording,
        translation: IntOffset,
        clip: IntRect? = null,
    ) = insert(
        recording = recording,
        transform = GraphiteTransform.translation(
            translation.x.toFloat(),
            translation.y.toFloat(),
        ),
        clip = clip,
    )

    /** Inserts [recording] with an arbitrary transform. */
    fun insert(
        recording: GraphiteRecording,
        transform: GraphiteTransform = GraphiteTransform.Identity,
        clip: IntRect? = null,
    ) {
        if (recording.runtimeToken !== runtimeToken) {
            throw GraphitePresentationException("recording belongs to a different runtime")
        }
        check(!built) { "frame builder has already finished" }
        insertions += GraphiteFrameInsertion(
            program = recording.program,
            platformRecording = recording.platformRecording,
            transform = transform,
            clip = clip,
        )
    }

    internal fun build(): List<GraphiteFrameInsertion> {
        check(!built) { "frame builder has already finished" }
        built = true
        return insertions.toList()
    }
}
