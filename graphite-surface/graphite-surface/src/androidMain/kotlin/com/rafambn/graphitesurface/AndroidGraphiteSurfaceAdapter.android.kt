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
    controller: GraphiteSurfaceController?,
    outputMode: GraphiteOutputMode,
) {
    val adapter = remember(renderer, renderMode, outputMode) {
        AndroidGraphiteSurfaceAdapter(renderer, renderMode, outputMode)
    }

    DisposableEffect(controller, adapter) {
        controller?.setRequestRenderHandler { adapter.requestRender() }
        onDispose {
            controller?.setRequestRenderHandler(null)
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
    private val outputMode: GraphiteOutputMode,
) {
    private var view: GraphiteSurfaceView? = null

    fun createView(context: Context): GraphiteSurfaceView {
        return view ?: GraphiteSurfaceView(context, renderer, renderMode, outputMode).also { view = it }
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
    private val outputMode: GraphiteOutputMode,
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
            engineHandle = AndroidGraphiteNative.create(outputMode.ordinal)
            if (engineHandle == 0L) {
                disposed = true
                Log.e(TAG, "The Android Graphite engine could not be created")
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
                Log.e(TAG, "The Android Graphite engine could not bind the Surface")
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
                Log.e(TAG, "The Android Graphite engine could not resize the Surface")
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

        if (renderMode == GraphiteRenderMode.WhenDirty && !pendingRender) return
        if (AndroidGraphiteNative.beginFrame(engineHandle)) {
            pendingRender = false
            try {
                renderer.onDrawFrame(AndroidGraphiteDrawContext(engineHandle))
            } finally {
                if (!AndroidGraphiteNative.endFrame(engineHandle)) {
                    Log.e(TAG, "The Android Graphite frame could not be presented")
                }
            }
        }

        if (renderMode == GraphiteRenderMode.Continuously || pendingRender) {
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
        if (renderMode == GraphiteRenderMode.WhenDirty && !pendingRender) return
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
}
