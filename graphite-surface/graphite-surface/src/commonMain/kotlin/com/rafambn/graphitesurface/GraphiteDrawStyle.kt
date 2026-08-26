package com.rafambn.graphitesurface

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

/** Fill or stroke applied by a geometry command. */
sealed interface GraphiteDrawStyle {
    /** Fills the geometry interior. */
    data object Fill : GraphiteDrawStyle

    /** Strokes the geometry outline with the supplied Skia stroke settings. */
    data class Stroke(
        val width: Float = 1f,
        val cap: StrokeCap = StrokeCap.Butt,
        val join: StrokeJoin = StrokeJoin.Miter,
        val miter: Float = 4f,
    ) : GraphiteDrawStyle {
        init {
            require(width.isFinite() && width >= 0f) {
                "stroke width must be finite and non-negative"
            }
            require(miter.isFinite() && miter >= 0f) {
                "stroke miter must be finite and non-negative"
            }
        }
    }
}
