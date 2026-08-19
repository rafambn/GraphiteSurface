package com.rafambn.graphitesurface

/**
 * Owns the scene drawn by a [GraphiteSurface]. Callbacks run on the native
 * rendering callback, not during Compose recomposition.
 */
public interface GraphiteRenderer {
    /** Creates resources that belong to this surface. */
    public fun onSurfaceCreated()

    /** Receives the pixel size before the next frame is drawn. */
    public fun onSurfaceChanged(size: GraphiteSize)

    /** Records one frame using the library-owned drawing context. */
    public fun onDrawFrame(context: GraphiteDrawContext)
}
