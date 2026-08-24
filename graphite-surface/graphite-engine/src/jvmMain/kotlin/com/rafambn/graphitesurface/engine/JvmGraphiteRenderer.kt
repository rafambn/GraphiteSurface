package com.rafambn.graphitesurface.engine

/** JVM callback contract used by the Compose adapter. */
public interface JvmGraphiteRenderer {
    public fun onSurfaceCreated()

    public fun onSurfaceChanged(width: Int, height: Int)

    public fun onDrawFrame(context: JvmGraphiteDrawContext)

    public fun onSurfaceError(error: Throwable) = Unit
}
