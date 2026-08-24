package com.rafambn.graphitesurface

/** Controls when a [GraphiteRenderer] produces frames. */
public enum class GraphiteRenderMode {
    /** Produces one frame for every available display frame. */
    Continuous,

    /** Produces a frame after [GraphiteRenderer.requestRender] requests one. */
    OnDemand,

    /** Produces frames only through [GraphiteRenderer.render]. */
    Manual,
}
