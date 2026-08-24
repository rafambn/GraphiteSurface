package com.rafambn.graphitesurface

/** Builder for an ordered frame made from completed recordings. */
public class GraphiteFrameBuilder internal constructor(private val runtimeToken: Any) : AutoCloseable {
    private val insertions: MutableList<GraphiteFrameInsertion> = mutableListOf()
    private var built: Boolean = false

    public fun insert(
        recording: GraphiteRecording,
        translation: GraphiteIntOffset = GraphiteIntOffset.Zero,
        clip: GraphiteIntRect? = null,
    ) {
        if (recording.runtimeToken !== runtimeToken) {
            throw GraphitePresentationException("recording belongs to a different runtime")
        }
        check(!built) { "frame builder has already finished" }
        val retained = recording.retainContent()
        try {
            insertions += GraphiteFrameInsertion(
                recording = retained,
                translation = translation,
                clip = clip,
            )
        } catch (error: Throwable) {
            retained.close()
            throw error
        }
    }

    internal fun build(): List<GraphiteFrameInsertion> {
        check(!built) { "frame builder has already finished" }
        built = true
        return insertions.toList()
    }

    override fun close() {
        if (built) return
        built = true
        insertions.forEach(GraphiteFrameInsertion::close)
    }
}
