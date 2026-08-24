package com.rafambn.graphitesurface

/** Internal synchronous bridge consumed by platform presentation hosts. */
internal interface GraphitePresentationRenderer {
    fun onSurfaceCreated()

    fun onSurfaceChanged(size: GraphiteSize)

    fun hasPendingFrame(): Boolean

    fun onDrawFrame(context: GraphiteDrawContext)

    fun onSurfaceError(error: Throwable) = Unit
}
