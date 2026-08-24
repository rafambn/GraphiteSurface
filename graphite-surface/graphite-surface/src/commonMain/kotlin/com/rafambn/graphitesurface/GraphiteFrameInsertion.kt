package com.rafambn.graphitesurface

internal class GraphiteFrameInsertion(
    internal val commands: ByteArray,
    internal val targetSize: GraphiteSize,
    internal val translation: GraphiteIntOffset,
    internal val clip: GraphiteIntRect?,
)
