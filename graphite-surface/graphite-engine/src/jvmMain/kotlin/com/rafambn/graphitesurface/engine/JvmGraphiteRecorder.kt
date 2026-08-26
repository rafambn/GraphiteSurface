@file:OptIn(org.jetbrains.skiko.ExperimentalSkikoApi::class)

package com.rafambn.graphitesurface.engine

import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.gpu.graphite.Recorder

/** Thread-confined native Graphite recorder created from a presentation context. */
class JvmGraphiteRecorder internal constructor(
    private val native: Recorder,
    private val context: JvmGraphiteRecordingContext,
) : AutoCloseable {
    fun record(
        width: Int,
        height: Int,
        block: JvmGraphiteDrawContext.() -> Unit,
    ): JvmGraphiteRecording {
        val canvas = native.makeDeferredCanvas(
            ImageInfo.makeN32Premul(width, height, ColorSpace.sRGB),
            context.awaitTextureInfo(),
        )
        JvmGraphiteSurface.SkiaGraphiteDrawContext(canvas).block()
        return JvmGraphiteRecording(native.snap())
    }

    override fun close() {
        native.close()
    }
}
