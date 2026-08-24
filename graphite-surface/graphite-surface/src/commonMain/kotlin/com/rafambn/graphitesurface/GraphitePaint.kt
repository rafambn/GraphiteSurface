package com.rafambn.graphitesurface

/** Immutable paint used by geometry commands. */
public class GraphitePaint(
    public val color: GraphiteColor,
    public val style: Style = Style.Fill,
    public val strokeWidth: Float = 1f,
    public val antiAlias: Boolean = true,
) {
    init {
        require(strokeWidth.isFinite() && strokeWidth >= 0f) {
            "strokeWidth must be finite and non-negative"
        }
    }

    public enum class Style {
        Fill,
        Stroke,
    }
}
