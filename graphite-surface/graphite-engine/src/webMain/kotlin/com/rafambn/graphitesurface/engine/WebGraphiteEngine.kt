@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.rafambn.graphitesurface.engine

import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.JsAny
import kotlin.math.roundToInt

/** Owns a browser render Worker whose Skia Graphite context owns an OffscreenCanvas. */
class WebGraphiteEngine(
    private val canvas: HTMLCanvasElement,
    private val continuously: Boolean,
    private val onSurfaceCreated: () -> Unit,
    private val onSurfaceChanged: (width: Int, height: Int) -> Unit,
    private val onDrawFrame: (WebGraphiteDrawContext) -> Unit,
    private val onSurfaceError: (Throwable) -> Unit,
) {
    private var worker: JsAny? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var frameScheduled = false
    private var pendingRender = true
    private var started = false
    private var ready = false
    private var disposed = false

    fun start() {
        if (started || disposed) return
        started = true
        if (!supportsGraphiteRenderWorker(canvas)) {
            fail("WebGPU, OffscreenCanvas, or module Web Workers are unavailable")
            return
        }
        worker = createGraphiteRenderWorker(
            canvas = canvas,
            onReady = {
                if (!disposed) {
                    ready = true
                    onSurfaceCreated()
                    requestRender()
                }
            },
            onFailure = ::fail,
            onDisposed = { worker = null },
        )
    }

    fun resizeToDisplaySize() {
        resizeToDisplaySize(scheduleFrame = true)
    }

    fun requestRender() {
        if (disposed) return
        pendingRender = true
        scheduleFrame()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        frameScheduled = false
        worker?.let(::disposeGraphiteRenderWorker)
        ready = false
    }

    private fun resizeToDisplaySize(scheduleFrame: Boolean) {
        if (disposed) return
        val bounds = canvas.getBoundingClientRect()
        val scale = window.devicePixelRatio
        val width = (bounds.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bounds.height * scale).roundToInt().coerceAtLeast(1)
        if (width == lastWidth && height == lastHeight) return
        lastWidth = width
        lastHeight = height
        pendingRender = true
        onSurfaceChanged(width, height)
        if (scheduleFrame) scheduleFrame()
    }

    private fun scheduleFrame() {
        if (disposed || !ready || frameScheduled) return
        if (!continuously && !pendingRender) return
        frameScheduled = true
        requestWebGpuAnimationFrame { drawFrame() }
    }

    private fun drawFrame() {
        frameScheduled = false
        if (disposed || !ready) return
        if (!continuously && !pendingRender) return
        resizeToDisplaySize(scheduleFrame = false)
        pendingRender = false
        if (lastWidth <= 0 || lastHeight <= 0) {
            scheduleFrame()
            return
        }
        val currentWorker = worker ?: return
        try {
            val context = WebGraphiteDrawContext()
            onDrawFrame(context)
            postGraphiteRenderFrame(currentWorker, lastWidth, lastHeight, context.finish())
        } catch (error: Throwable) {
            fail("Graphite frame encoding failed: ${error.message ?: error}")
            return
        }
        if (continuously) scheduleFrame()
    }

    private fun fail(message: String) {
        if (disposed) return
        ready = false
        reportWebGpuError(message)
        onSurfaceError(IllegalStateException(message))
    }
}
