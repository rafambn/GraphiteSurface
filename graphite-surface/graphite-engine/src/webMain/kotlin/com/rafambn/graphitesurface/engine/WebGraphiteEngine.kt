@file:OptIn(
    kotlin.js.ExperimentalWasmJsInterop::class,
    org.jetbrains.skiko.ExperimentalSkikoApi::class,
    org.jetbrains.skiko.InternalSkikoApi::class,
)

package com.rafambn.graphitesurface.engine

import kotlinx.browser.window
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.Surface
import org.jetbrains.skia.gpu.graphite.BackendTexture
import org.jetbrains.skia.gpu.graphite.GraphiteContext
import org.jetbrains.skia.gpu.graphite.Recorder
import org.jetbrains.skia.gpu.graphite.wrapBackendTexture
import org.jetbrains.skia.impl.use
import org.jetbrains.skiko.wasm.addWebGPUTexture
import org.jetbrains.skiko.wasm.awaitSkiko
import org.jetbrains.skiko.wasm.setWebGPUDevice
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.JsAny
import kotlin.math.roundToInt

/** Owns the browser WebGPU swapchain and a Skia Graphite Dawn context. */
public class WebGraphiteEngine(
    private val canvas: HTMLCanvasElement,
    private val continuously: Boolean,
    private val onSurfaceCreated: () -> Unit,
    private val onSurfaceChanged: (width: Int, height: Int) -> Unit,
    private val onDrawFrame: (WebGraphiteDrawContext) -> Unit,
) {
    private var graphiteContext: GraphiteContext? = null
    private var recorder: Recorder? = null
    private var webGpuContext: JsAny? = null
    private var retainedSurface: Surface? = null
    private var retainedBackendTexture: BackendTexture? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var frameScheduled = false
    private var pendingRender = true
    private var started = false
    private var ready = false
    private var disposed = false

    public fun start() {
        if (started || disposed) return
        started = true

        requestWebGpuDevice().then(
            onFulfilled = { device ->
                if (device == null) {
                    fail("WebGPU adapter/device creation returned null")
                } else {
                    awaitSkiko.then(
                        onFulfilled = {
                            initialize(device)
                            null
                        },
                        onRejected = { error ->
                            fail("Skiko runtime initialization failed: $error")
                            null
                        },
                    )
                }
                null
            },
            onRejected = { error ->
                fail("WebGPU initialization failed: $error")
                null
            },
        )
    }

    public fun resizeToDisplaySize() {
        resizeToDisplaySize(scheduleFrame = true)
    }

    private fun resizeToDisplaySize(scheduleFrame: Boolean) {
        if (disposed) return

        val bounds = canvas.getBoundingClientRect()
        val scale = window.devicePixelRatio
        val width = (bounds.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bounds.height * scale).roundToInt().coerceAtLeast(1)
        if (canvas.width == width && canvas.height == height) return

        canvas.width = width
        canvas.height = height
        pendingRender = true
        if (scheduleFrame) scheduleFrame()
    }

    public fun requestRender() {
        if (disposed) return
        pendingRender = true
        scheduleFrame()
    }

    public fun dispose() {
        if (disposed) return
        disposed = true
        frameScheduled = false
        retainedSurface?.close()
        retainedSurface = null
        retainedBackendTexture?.close()
        retainedBackendTexture = null
        recorder?.close()
        recorder = null
        graphiteContext?.close()
        graphiteContext = null
        webGpuContext = null
        ready = false
    }

    private fun initialize(device: JsAny) {
        if (disposed) return
        try {
            setWebGPUDevice(device)
            webGpuContext = configureWebGpuCanvas(canvas, device)
                ?: error("The canvas does not expose a WebGPU context")
            graphiteContext = GraphiteContext.makeDawn()
            recorder = graphiteContext!!.makeRecorder()
            ready = true
            onSurfaceCreated()
            requestRender()
        } catch (error: Throwable) {
            fail("Skia Graphite Dawn initialization failed: ${error.message ?: error}")
        }
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

        // Compose may call the HtmlElementView update before its wrapper has
        // been positioned. Re-measure on the first frame so the swapchain
        // texture matches the final DOM bounds.
        resizeToDisplaySize(scheduleFrame = false)
        pendingRender = false
        val width = canvas.width
        val height = canvas.height
        if (width <= 0 || height <= 0) {
            scheduleFrame()
            return
        }

        if (width != lastWidth || height != lastHeight) {
            lastWidth = width
            lastHeight = height
            onSurfaceChanged(width, height)
        }

        val context = graphiteContext ?: return
        val recorder = recorder ?: return
        val webGpuContext = webGpuContext ?: return

        var surface: Surface? = null
        var backendTexture: BackendTexture? = null
        try {
            val texture = currentWebGpuTexture(webGpuContext)
            val textureHandle = addWebGPUTexture(texture)
            backendTexture = BackendTexture.makeDawn(textureHandle)
            surface = Surface.wrapBackendTexture(
                recorder = recorder,
                backendTexture = backendTexture,
                colorSpace = ColorSpace.sRGB,
            )
            if (surface == null) {
                backendTexture.close()
                backendTexture = null
                scheduleFrame()
                return
            }

            onDrawFrame(WebGraphiteDrawContext(surface.canvas))
            recorder.snap().use { recording ->
                context.insertRecording(recording)
                context.submit(syncCpu = false)
            }

            val previousSurface = retainedSurface
            val previousBackendTexture = retainedBackendTexture
            retainedSurface = surface
            retainedBackendTexture = backendTexture
            surface = null
            backendTexture = null
            previousSurface?.close()
            previousBackendTexture?.close()
        } catch (error: Throwable) {
            surface?.close()
            backendTexture?.close()
            fail("Skia Graphite Dawn frame failed: ${error.message ?: error}")
        }

        if (continuously) scheduleFrame()
    }

    private fun fail(message: String) {
        if (disposed) return
        ready = false
        reportWebGpuError(message)
    }
}
