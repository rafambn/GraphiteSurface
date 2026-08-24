package com.rafambn.graphitesurface

internal class GraphiteFrameContent(
    internal val insertions: List<GraphiteFrameInsertion>,
) : AutoCloseable {
    override fun close() {
        insertions.forEach(GraphiteFrameInsertion::close)
    }
}
