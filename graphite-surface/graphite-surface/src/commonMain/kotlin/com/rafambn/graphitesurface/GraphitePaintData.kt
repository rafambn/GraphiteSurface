package com.rafambn.graphitesurface

import androidx.compose.ui.graphics.Color

internal data class GraphitePaintData(
    internal val color: Color,
    internal val strokeWidth: Float?,
    internal val antiAlias: Boolean,
)
