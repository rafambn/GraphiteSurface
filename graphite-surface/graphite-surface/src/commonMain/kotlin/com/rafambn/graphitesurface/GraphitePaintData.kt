package com.rafambn.graphitesurface

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

internal data class GraphitePaintData(
    internal val color: Color,
    internal val strokeWidth: Float?,
    internal val strokeCap: StrokeCap = StrokeCap.Butt,
    internal val strokeJoin: StrokeJoin = StrokeJoin.Miter,
    internal val strokeMiter: Float = 4f,
    internal val antiAlias: Boolean,
)

internal val GraphitePaintData.strokeCapCode: Int
    get() = when (strokeCap) {
        StrokeCap.Butt -> 0
        StrokeCap.Round -> 1
        StrokeCap.Square -> 2
        else -> error("unsupported stroke cap: $strokeCap")
    }

internal val GraphitePaintData.strokeJoinCode: Int
    get() = when (strokeJoin) {
        StrokeJoin.Miter -> 0
        StrokeJoin.Round -> 1
        StrokeJoin.Bevel -> 2
        else -> error("unsupported stroke join: $strokeJoin")
    }
