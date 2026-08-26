package com.rafambn.graphitesurface.engine

/** JVM callback contract used by the Compose adapter. */
interface JvmGraphiteRenderer {
    fun onSurfaceCreated(recordingContext: JvmGraphiteRecordingContext)

    fun onSurfaceDestroyed(recordingContext: JvmGraphiteRecordingContext)

    fun onSurfaceChanged(width: Int, height: Int)

    fun onDrawFrame(context: JvmGraphiteDrawContext)

    fun onSurfaceError(error: Throwable) = Unit
}
