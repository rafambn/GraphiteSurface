package com.rafambn.graphitesurface

internal class GraphiteFrameSnapshot(
    internal val presentationGeneration: Long,
    internal val clearColor: GraphiteColor,
    private val content: GraphiteRetainedReference<GraphiteFrameContent>,
) : AutoCloseable {
    internal val insertions: List<GraphiteFrameInsertion> get() = content.value.insertions

    override fun close() {
        content.close()
    }
}
