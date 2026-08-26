@file:OptIn(org.jetbrains.skiko.ExperimentalSkikoApi::class)

package com.rafambn.graphitesurface.engine

import org.jetbrains.skia.gpu.graphite.Recording

/** Native Graphite recording produced by one Android recorder worker. */
class AndroidGraphiteRecording internal constructor(
    internal val native: Recording,
)
