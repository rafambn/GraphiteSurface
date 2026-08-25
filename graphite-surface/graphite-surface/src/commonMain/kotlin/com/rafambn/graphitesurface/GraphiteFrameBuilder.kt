package com.rafambn.graphitesurface

/** Builder for an ordered frame made from completed recordings. */
class GraphiteFrameBuilder internal constructor(private val runtimeToken: Any) {
    private val insertions = mutableListOf<GraphiteFrameInsertion>()
    private var built = false

    fun insert(
        recording: GraphiteRecording,
        translation: GraphiteIntOffset = GraphiteIntOffset.Zero,
        clip: GraphiteIntRect? = null,
    ) {
        if (recording.runtimeToken !== runtimeToken) {
            throw GraphitePresentationException("recording belongs to a different runtime")
        }
        check(!built) { "frame builder has already finished" }
        insertions += GraphiteFrameInsertion(
            program = recording.program,
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
