package com.rafambn.graphitesurface

/** Controls how often the native surface asks the renderer for a frame. */
public enum class GraphiteRenderMode {
    /** Draws once per display frame. */
    Continuous,

    /** Draws only after [GraphiteSurfaceState.requestFrame] or a surface change. */
    OnDemand,
}
