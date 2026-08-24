package com.rafambn.graphitesurface

internal class GraphiteFrameSnapshot(
    internal val presentationGeneration: Long,
    internal val clearColor: GraphiteColor,
    internal val insertions: List<GraphiteFrameInsertion>,
)
