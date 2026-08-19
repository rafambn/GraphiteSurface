package com.rafambn.graphitesurface

/** Requests frames from a [GraphiteSurface] outside the Compose call site. */
public class GraphiteSurfaceController {
    private var requestRenderHandler: (() -> Unit)? = null

    internal fun setRequestRenderHandler(handler: (() -> Unit)?) {
        requestRenderHandler = handler
    }

    /** Requests one frame when the surface uses [GraphiteRenderMode.WhenDirty]. */
    public fun requestRender() {
        requestRenderHandler?.invoke()
    }
}
