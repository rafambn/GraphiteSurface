package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntRect

internal class GraphiteFrameInsertion(
    internal val program: GraphiteCommandProgram,
    internal val platformRecording: PlatformRecording,
    internal val transform: GraphiteTransform,
    internal val clip: IntRect?,
)
