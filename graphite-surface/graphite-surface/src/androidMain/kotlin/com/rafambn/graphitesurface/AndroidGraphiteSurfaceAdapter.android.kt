@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.rafambn.graphitesurface

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.rafambn.graphitesurface.engine.AndroidGraphiteNative
import java.util.concurrent.atomic.AtomicBoolean

@Composable
@ExperimentalGraphiteSurfaceApi
public actual fun GraphiteSurface(
    renderer: GraphiteRenderer,
    modifier: Modifier,
    renderMode: GraphiteRenderMode,
    state: GraphiteSurfaceState,
) {
    val adapter = remember(renderer, renderMode) {
        AndroidGraphiteSurfaceAdapter(renderer, renderMode)
    }

    DisposableEffect(state, adapter, renderMode) {
        val requestFrameHandler = { adapter.requestRender() }
        if (renderMode == GraphiteRenderMode.OnDemand) {
            state.setRequestFrameHandler(requestFrameHandler)
        }
        onDispose {
            state.clearRequestFrameHandler(requestFrameHandler)
        }
    }

    AndroidView(
        factory = { context -> adapter.createView(context) },
        modifier = modifier,
        onRelease = { adapter.dispose() },
    )
}

private class AndroidGraphiteSurfaceAdapter(
    private val renderer: GraphiteRenderer,
    private val renderMode: GraphiteRenderMode,
) {
    private var view: GraphiteSurfaceView? = null

    fun createView(context: Context): GraphiteSurfaceView {
        return view ?: GraphiteSurfaceView(context, renderer, renderMode).also { view = it }
    }

    fun requestRender() {
        view?.requestRender()
    }

    fun dispose() {
        view?.dispose()
        view = null
    }
}

private class GraphiteSurfaceView(
    context: Context,
    private val renderer: GraphiteRenderer,
    private val renderMode: GraphiteRenderMode,
) : SurfaceView(context), SurfaceHolder.Callback, Choreographer.FrameCallback {
    private val renderThread = HandlerThread(
        "GraphiteSurface",
        Process.THREAD_PRIORITY_DISPLAY,
    ).also { it.start() }
    private val renderHandler = Handler(renderThread.looper)
    private val disposeRequested = AtomicBoolean(false)

    private var choreographer: Choreographer? = null
    private var engineHandle = 0L

    private var frameScheduled = false
    private var pendingRender = true
    private var surfaceReady = false
    private var rendererCreated = false
    private var disposed = false
    private var lastSize = GraphiteSize.Zero

    init {
        holder.addCallback(this)
        setBackgroundColor(Color.TRANSPARENT)
        renderHandler.post {
            choreographer = Choreographer.getInstance()
            engineHandle = AndroidGraphiteNative.create(false)
            if (engineHandle == 0L) {
                disposed = true
                val error = IllegalStateException("The Android Graphite engine could not be created")
                Log.e(TAG, error.message, error)
                renderer.onSurfaceError(error)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val surface = holder.surface
        val surfaceWidth = width
        val surfaceHeight = height
        renderHandler.post {
            if (disposed || engineHandle == 0L) return@post
            surfaceReady = AndroidGraphiteNative.setSurface(
                engineHandle,
                surface,
                surfaceWidth,
                surfaceHeight,
            )
            if (!surfaceReady) {
                val error = IllegalStateException("The Android Graphite engine could not bind the Surface")
                Log.e(TAG, error.message, error)
                renderer.onSurfaceError(error)
                return@post
            }
            if (!rendererCreated) {
                rendererCreated = true
                renderer.onSurfaceCreated()
            }
            updateSize(surfaceWidth, surfaceHeight)
            scheduleFrame()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val surface = holder.surface
        renderHandler.post {
            if (disposed || engineHandle == 0L) return@post
            surfaceReady = AndroidGraphiteNative.setSurface(
                engineHandle,
                surface,
                width,
                height,
            )
            if (!surfaceReady) {
                val error = IllegalStateException("The Android Graphite engine could not resize the Surface")
                Log.e(TAG, error.message, error)
                renderer.onSurfaceError(error)
                return@post
            }
            updateSize(width, height)
            scheduleFrame()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderHandler.post {
            surfaceReady = false
            frameScheduled = false
            choreographer?.removeFrameCallback(this)
            if (!disposed && engineHandle != 0L) {
                AndroidGraphiteNative.setSurface(engineHandle, null, 0, 0)
            }
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameScheduled = false
        if (disposed || !surfaceReady) return

        if (renderMode == GraphiteRenderMode.OnDemand && !pendingRender) return
        AndroidGraphiteNative.setFrameTimeNanos(engineHandle, frameTimeNanos)
        if (AndroidGraphiteNative.beginFrame(engineHandle)) {
            pendingRender = false
            try {
                renderer.onDrawFrame(AndroidGraphiteDrawContext(engineHandle))
            } finally {
                if (!AndroidGraphiteNative.endFrame(engineHandle)) {
                    val error = IllegalStateException("The Android Graphite frame could not be presented")
                    Log.e(TAG, error.message, error)
                    renderer.onSurfaceError(error)
                }
            }
        }

        if (renderMode == GraphiteRenderMode.Continuous || pendingRender) {
            scheduleFrame()
        }
    }

    fun requestRender() {
        if (disposeRequested.get()) return
        renderHandler.post {
            if (disposed) return@post
            pendingRender = true
            scheduleFrame()
        }
    }

    fun dispose() {
        if (!disposeRequested.compareAndSet(false, true)) return
        holder.removeCallback(this)
        renderHandler.post {
            disposed = true
            frameScheduled = false
            choreographer?.removeFrameCallback(this)
            if (engineHandle != 0L) {
                AndroidGraphiteNative.setSurface(engineHandle, null, 0, 0)
                AndroidGraphiteNative.dispose(engineHandle)
                engineHandle = 0L
            }
            renderThread.quitSafely()
        }
    }

    private fun scheduleFrame() {
        if (disposed || !surfaceReady || frameScheduled) return
        if (renderMode == GraphiteRenderMode.OnDemand && !pendingRender) return
        val frameClock = choreographer ?: return
        frameScheduled = true
        frameClock.postFrameCallback(this)
    }

    private fun updateSize(width: Int, height: Int) {
        val size = GraphiteSize(width, height)
        if (size != lastSize) {
            lastSize = size
            renderer.onSurfaceChanged(size)
        }
    }

    private companion object {
        const val TAG = "GraphiteSurface"
    }
}

private class AndroidGraphiteDrawContext(
    private val engineHandle: Long,
) : GraphiteDrawContext {
    override fun clear(color: Long) {
        AndroidGraphiteNative.clear(engineHandle, color.toInt())
    }

    override fun save() {
        AndroidGraphiteNative.save(engineHandle)
    }

    override fun restore() {
        AndroidGraphiteNative.restore(engineHandle)
    }

    override fun translate(x: Float, y: Float) {
        AndroidGraphiteNative.translate(engineHandle, x, y)
    }

    override fun rotate(degrees: Float) {
        AndroidGraphiteNative.rotate(engineHandle, degrees)
    }

    override fun concat(transform: GraphiteTransform) {
        AndroidGraphiteNative.concat(
            engineHandle,
            FloatArray(16) { index -> transform[index / 4, index % 4] },
        )
    }

    override fun clipRect(rect: GraphiteRect, antiAlias: Boolean) {
        AndroidGraphiteNative.clipRect(
            engineHandle, rect.left, rect.top, rect.right, rect.bottom, antiAlias,
        )
    }

    override fun beginPath() {
        AndroidGraphiteNative.beginPath(engineHandle)
    }

    override fun moveTo(x: Float, y: Float) {
        AndroidGraphiteNative.moveTo(engineHandle, x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        AndroidGraphiteNative.lineTo(engineHandle, x, y)
    }

    override fun closePath() {
        AndroidGraphiteNative.closePath(engineHandle)
    }

    override fun drawPath(color: Long, antiAlias: Boolean) {
        AndroidGraphiteNative.drawPath(engineHandle, color.toInt(), antiAlias)
    }

    override fun drawPath(path: GraphitePath, paint: GraphitePaint) {
        AndroidGraphiteNative.drawImmutablePath(
            engineHandle,
            path.verbs,
            path.points,
            paint.color.toArgbLong().toInt(),
            paint.style == GraphitePaint.Style.Stroke,
            paint.strokeWidth,
            paint.antiAlias,
        )
    }

    override fun drawRect(rect: GraphiteRect, paint: GraphitePaint) {
        AndroidGraphiteNative.drawRect(
            engineHandle, rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong().toInt(), paint.style == GraphitePaint.Style.Stroke,
            paint.strokeWidth, paint.antiAlias,
        )
    }

    override fun drawRoundRect(
        rect: GraphiteRect,
        radiusX: Float,
        radiusY: Float,
        paint: GraphitePaint,
    ) {
        AndroidGraphiteNative.drawRoundRect(
            engineHandle, rect.left, rect.top, rect.right, rect.bottom, radiusX, radiusY,
            paint.color.toArgbLong().toInt(), paint.style == GraphitePaint.Style.Stroke,
            paint.strokeWidth, paint.antiAlias,
        )
    }

    override fun drawOval(rect: GraphiteRect, paint: GraphitePaint) {
        AndroidGraphiteNative.drawOval(
            engineHandle, rect.left, rect.top, rect.right, rect.bottom,
            paint.color.toArgbLong().toInt(), paint.style == GraphitePaint.Style.Stroke,
            paint.strokeWidth, paint.antiAlias,
        )
    }

    override fun drawCircle(center: GraphitePoint, radius: Float, paint: GraphitePaint) {
        AndroidGraphiteNative.drawCircle(
            engineHandle, center.x, center.y, radius,
            paint.color.toArgbLong().toInt(), paint.style == GraphitePaint.Style.Stroke,
            paint.strokeWidth, paint.antiAlias,
        )
    }

    override fun drawLine(start: GraphitePoint, end: GraphitePoint, paint: GraphitePaint) {
        AndroidGraphiteNative.drawLine(
            engineHandle, start.x, start.y, end.x, end.y,
            paint.color.toArgbLong().toInt(), paint.strokeWidth, paint.antiAlias,
        )
    }
}
