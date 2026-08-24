package com.rafambn.graphitesurface

internal class GraphitePresentationAttachment(
    internal val id: Long,
    internal val requestFrame: () -> Unit,
    internal val info: GraphitePresentationInfo?,
)
