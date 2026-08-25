package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect

internal class GraphiteFrameInsertion(
    internal val program: GraphiteCommandProgram,
    internal val translation: IntOffset,
    internal val clip: IntRect?,
)
