package com.rafambn.graphitesurface

import androidx.compose.ui.graphics.Color

/** Immutable ordered content for one presentation generation. */
internal class GraphiteFrame(
    val presentation: GraphitePresentationInfo,
    val clearColor: Color,
    internal val insertions: List<GraphiteFrameInsertion>,
)
