@file:OptIn(org.jetbrains.skiko.ExperimentalSkikoApi::class)

package com.rafambn.graphitesurface.engine

import android.view.Surface
import org.jetbrains.skia.gpu.graphite.Recording
import org.jetbrains.skia.gpu.graphite.Recorder
import org.jetbrains.skia.gpu.graphite.TextureInfo
import org.jetbrains.skia.gpu.graphite.AndroidGraphiteNative as SkikoAndroidGraphiteNative

/** Private engine boundary between GraphiteSurface and the Skiko Graphite Android host. */
object AndroidGraphiteNative {
    /** Creates an Android Graphite renderer and returns its native handle. */
    fun create(useHardwareBuffer: Boolean): Long =
        SkikoAndroidGraphiteNative.create(useHardwareBuffer)

    /** Attaches or detaches the Android presentation surface. */
    fun setSurface(handle: Long, surface: Surface?, width: Int, height: Int): Boolean =
        SkikoAndroidGraphiteNative.setSurface(handle, surface, width, height)

    /** Acquires the next frame for drawing. */
    fun beginFrame(handle: Long): Boolean = SkikoAndroidGraphiteNative.beginFrame(handle)

    /** Updates the animation time used by the current frame. */
    fun setFrameTimeNanos(handle: Long, frameTimeNanos: Long) {
        SkikoAndroidGraphiteNative.setFrameTimeNanos(handle, frameTimeNanos)
    }

    /** Submits and presents the current frame. */
    fun endFrame(handle: Long): Boolean = SkikoAndroidGraphiteNative.endFrame(handle)

    /** Releases the native renderer. */
    fun dispose(handle: Long) {
        SkikoAndroidGraphiteNative.dispose(handle)
    }

    fun recordingContext(handle: Long): AndroidGraphiteRecordingContext =
        AndroidGraphiteRecordingContext(handle)

    fun makeRecorder(handle: Long): Recorder =
        SkikoAndroidGraphiteNative.makeRecorder(handle)

    fun targetTextureInfo(handle: Long): TextureInfo =
        SkikoAndroidGraphiteNative.targetTextureInfo(handle)

    fun insertRecording(
        handle: Long,
        recording: Recording,
        translationX: Int,
        translationY: Int,
        clipLeft: Int,
        clipTop: Int,
        clipRight: Int,
        clipBottom: Int,
        hasClip: Boolean,
    ): Boolean = SkikoAndroidGraphiteNative.insertRecording(
        handle,
        recording,
        translationX,
        translationY,
        clipLeft,
        clipTop,
        clipRight,
        clipBottom,
        hasClip,
    )

    /** Clears the current canvas. */
    fun clear(handle: Long, color: Int) {
        SkikoAndroidGraphiteNative.clear(handle, color)
    }

    /** Saves the current canvas state. */
    fun save(handle: Long) {
        SkikoAndroidGraphiteNative.save(handle)
    }

    /** Restores the previous canvas state. */
    fun restore(handle: Long) {
        SkikoAndroidGraphiteNative.restore(handle)
    }

    /** Translates the current canvas transform. */
    fun translate(handle: Long, x: Float, y: Float) {
        SkikoAndroidGraphiteNative.translate(handle, x, y)
    }

    /** Rotates the current canvas transform. */
    fun rotate(handle: Long, degrees: Float) {
        SkikoAndroidGraphiteNative.rotate(handle, degrees)
    }

    /** Concatenates a column-major transform matrix. */
    fun concat(handle: Long, columnMajor: FloatArray) {
        SkikoAndroidGraphiteNative.concat(handle, columnMajor)
    }

    /** Clips the current canvas to a rectangle. */
    fun clipRect(
        handle: Long,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        antiAlias: Boolean,
    ) {
        SkikoAndroidGraphiteNative.clipRect(handle, left, top, right, bottom, antiAlias)
    }

    /** Starts a mutable path. */
    fun beginPath(handle: Long) {
        SkikoAndroidGraphiteNative.beginPath(handle)
    }

    /** Moves the mutable path cursor. */
    fun moveTo(handle: Long, x: Float, y: Float) {
        SkikoAndroidGraphiteNative.moveTo(handle, x, y)
    }

    /** Adds a line to the mutable path. */
    fun lineTo(handle: Long, x: Float, y: Float) {
        SkikoAndroidGraphiteNative.lineTo(handle, x, y)
    }

    /** Closes the mutable path. */
    fun closePath(handle: Long) {
        SkikoAndroidGraphiteNative.closePath(handle)
    }

    /** Draws the current mutable path. */
    fun drawPath(handle: Long, color: Int, antiAlias: Boolean) {
        SkikoAndroidGraphiteNative.drawPath(handle, color, antiAlias)
    }

    /** Draws an immutable encoded path. */
    fun drawImmutablePath(
        handle: Long,
        verbs: ByteArray,
        points: FloatArray,
        weights: FloatArray,
        fillType: Int,
        color: Int,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        SkikoAndroidGraphiteNative.drawImmutablePath(
            handle,
            verbs,
            points,
            weights,
            fillType,
            color,
            stroke,
            strokeWidth,
            antiAlias,
        )
    }

    /** Draws a rectangle. */
    fun drawRect(
        handle: Long,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Int,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        SkikoAndroidGraphiteNative.drawRect(
            handle,
            left,
            top,
            right,
            bottom,
            color,
            stroke,
            strokeWidth,
            antiAlias,
        )
    }

    /** Draws a rounded rectangle. */
    fun drawRoundRect(
        handle: Long,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radiusX: Float,
        radiusY: Float,
        color: Int,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        SkikoAndroidGraphiteNative.drawRoundRect(
            handle,
            left,
            top,
            right,
            bottom,
            radiusX,
            radiusY,
            color,
            stroke,
            strokeWidth,
            antiAlias,
        )
    }

    /** Draws an oval. */
    fun drawOval(
        handle: Long,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Int,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        SkikoAndroidGraphiteNative.drawOval(
            handle,
            left,
            top,
            right,
            bottom,
            color,
            stroke,
            strokeWidth,
            antiAlias,
        )
    }

    /** Draws a circle. */
    fun drawCircle(
        handle: Long,
        x: Float,
        y: Float,
        radius: Float,
        color: Int,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        SkikoAndroidGraphiteNative.drawCircle(
            handle,
            x,
            y,
            radius,
            color,
            stroke,
            strokeWidth,
            antiAlias,
        )
    }

    /** Draws a line segment. */
    fun drawLine(
        handle: Long,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: Int,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) {
        SkikoAndroidGraphiteNative.drawLine(
            handle,
            x0,
            y0,
            x1,
            y1,
            color,
            strokeWidth,
            antiAlias,
        )
    }
}
