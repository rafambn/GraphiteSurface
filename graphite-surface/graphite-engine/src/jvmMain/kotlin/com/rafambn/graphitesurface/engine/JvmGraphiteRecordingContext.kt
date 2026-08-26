@file:OptIn(org.jetbrains.skiko.ExperimentalSkikoApi::class)

package com.rafambn.graphitesurface.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.jetbrains.skia.IPoint
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.gpu.graphite.BackendTexture
import org.jetbrains.skia.gpu.graphite.GraphiteContext
import org.jetbrains.skia.gpu.graphite.InsertRecordingInfo
import org.jetbrains.skia.gpu.graphite.TextureInfo

/** Shared JVM Graphite context boundary used to create worker-owned recorders. */
class JvmGraphiteRecordingContext internal constructor(
    private val native: GraphiteContext,
) : AutoCloseable {
    private val textureInfo = AtomicReference<TextureInfo?>(null)
    private val targetReady = CountDownLatch(1)

    fun makeRecorder(): JvmGraphiteRecorder = JvmGraphiteRecorder(native.makeRecorder(), this)

    internal fun installTarget(backendTexture: BackendTexture) {
        if (textureInfo.compareAndSet(null, backendTexture.textureInfo)) {
            targetReady.countDown()
        }
    }

    internal fun awaitTextureInfo(): TextureInfo {
        targetReady.await()
        return checkNotNull(textureInfo.get())
    }

    internal fun insert(
        recording: JvmGraphiteRecording,
        surface: Surface,
        translationX: Int,
        translationY: Int,
        clipLeft: Int,
        clipTop: Int,
        clipRight: Int,
        clipBottom: Int,
        hasClip: Boolean,
    ) {
        native.insertRecording(
            InsertRecordingInfo(
                recording = recording.native,
                targetSurface = surface,
                targetTranslation = IPoint(translationX, translationY),
                targetClip = if (hasClip) {
                    IRect.makeLTRB(clipLeft, clipTop, clipRight, clipBottom)
                } else {
                    null
                },
            ),
        )
    }

    override fun close() {
        textureInfo.getAndSet(null)?.close()
    }
}
