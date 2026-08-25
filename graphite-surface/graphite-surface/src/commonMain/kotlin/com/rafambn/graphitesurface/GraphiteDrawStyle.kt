package com.rafambn.graphitesurface

/** Fill or stroke applied by a geometry command. */
sealed interface GraphiteDrawStyle {
    /** Fills the geometry interior. */
    data object Fill : GraphiteDrawStyle

    /** Strokes the geometry outline with [width]. */
    data class Stroke(val width: Float = 1f) : GraphiteDrawStyle {
        init {
            require(width.isFinite() && width >= 0f) {
                "stroke width must be finite and non-negative"
            }
        }
    }
}
