package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntSize

/** Internal synchronous bridge consumed by platform presentation hosts. */
internal interface GraphitePresentationRenderer {
    fun onSurfaceCreated()

    fun onSurfaceChanged(size: IntSize)

    fun hasPendingFrame(): Boolean

    fun onDrawFrame(context: GraphiteDrawContext)

    fun onSurfaceError(error: Throwable) = Unit
}
