package com.rafambn.graphitesurface

internal class GraphiteFrameInsertion(
    internal val recording: GraphiteRetainedReference<GraphiteRecordingContent>,
    internal val targetSize: GraphiteSize,
    internal val translation: GraphiteIntOffset,
    internal val clip: GraphiteIntRect?,
) : AutoCloseable {
    override fun close() {
        recording.close()
    }
}
