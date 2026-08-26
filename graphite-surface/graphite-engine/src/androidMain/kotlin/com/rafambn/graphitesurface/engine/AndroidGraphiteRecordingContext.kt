@file:OptIn(org.jetbrains.skiko.ExperimentalSkikoApi::class)

package com.rafambn.graphitesurface.engine

import org.jetbrains.skia.gpu.graphite.TextureInfo

/** Android Graphite context boundary shared by the presentation and recorder workers. */
class AndroidGraphiteRecordingContext internal constructor(
    private val engineHandle: Long,
) : AutoCloseable {
    private val textureInfo: TextureInfo = AndroidGraphiteNative.targetTextureInfo(engineHandle)

    fun makeRecorder(): AndroidGraphiteRecorder = AndroidGraphiteRecorder(
        native = AndroidGraphiteNative.makeRecorder(engineHandle),
        textureInfo = textureInfo,
    )

    fun insert(
        recording: AndroidGraphiteRecording,
        translationX: Int,
        translationY: Int,
        clipLeft: Int,
        clipTop: Int,
        clipRight: Int,
        clipBottom: Int,
        hasClip: Boolean,
    ) {
        check(
            AndroidGraphiteNative.insertRecording(
                engineHandle,
                recording.native,
                translationX,
                translationY,
                clipLeft,
                clipTop,
                clipRight,
                clipBottom,
                hasClip,
            ),
        ) { "Could not insert the Android Graphite recording" }
    }

    override fun close() {
        textureInfo.close()
    }
}
