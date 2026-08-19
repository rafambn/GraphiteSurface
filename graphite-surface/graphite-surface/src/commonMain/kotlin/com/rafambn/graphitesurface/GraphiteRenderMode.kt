package com.rafambn.graphitesurface

/** Controls how often the native surface asks the renderer for a frame. */
public enum class GraphiteRenderMode {
    /** Draw continuously, like GLSurfaceView.RENDERMODE_CONTINUOUSLY. */
    Continuously,

    /** Draw only after [GraphiteSurfaceController.requestRender]. */
    WhenDirty,
}
