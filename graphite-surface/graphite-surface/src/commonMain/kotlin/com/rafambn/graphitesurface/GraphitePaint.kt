package com.rafambn.graphitesurface

/** Immutable paint used by geometry commands. */
class GraphitePaint(
    val color: GraphiteColor,
    val style: Style = Style.Fill,
    val strokeWidth: Float = 1f,
    val antiAlias: Boolean = true,
) {
    init {
        require(strokeWidth.isFinite() && strokeWidth >= 0f) {
            "strokeWidth must be finite and non-negative"
        }
    }

    enum class Style {
        Fill,
        Stroke,
    }
}
