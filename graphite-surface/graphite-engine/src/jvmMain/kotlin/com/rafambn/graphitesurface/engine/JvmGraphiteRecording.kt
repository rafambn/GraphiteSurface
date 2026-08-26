package com.rafambn.graphitesurface.engine

import org.jetbrains.skia.gpu.graphite.Recording

/** Native Graphite recording produced by one JVM recorder worker. */
class JvmGraphiteRecording internal constructor(
    internal val native: Recording,
)
