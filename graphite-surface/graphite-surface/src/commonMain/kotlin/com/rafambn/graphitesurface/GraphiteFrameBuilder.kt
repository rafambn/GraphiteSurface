package com.rafambn.graphitesurface

/** Builder for an ordered frame made from completed recordings. */
public class GraphiteFrameBuilder internal constructor(private val runtimeToken: Any) {
    private val insertions: MutableList<GraphiteFrameInsertion> = mutableListOf()

    public fun insert(
        recording: GraphiteRecording,
        translation: GraphiteIntOffset = GraphiteIntOffset.Zero,
        clip: GraphiteIntRect? = null,
    ) {
        if (recording.target.runtimeToken !== runtimeToken) {
            throw GraphitePresentationException("recording belongs to a different runtime")
        }
        insertions += GraphiteFrameInsertion(
            commands = recording.snapshotCommands(),
            targetSize = recording.target.pixelSize,
            translation = translation,
            clip = clip,
        )
    }

    internal fun build(): List<GraphiteFrameInsertion> = insertions.toList()
}
