@file:OptIn(org.jetbrains.skiko.ExperimentalSkikoApi::class)

package com.rafambn.graphitesurface.engine

import java.awt.Component
import kotlin.math.roundToInt
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Surface
import org.jetbrains.skia.gpu.graphite.BackendTexture
import org.jetbrains.skia.gpu.graphite.GraphiteContext
import org.jetbrains.skia.gpu.graphite.Recorder
import org.jetbrains.skia.gpu.graphite.wrapBackendTexture
import org.jetbrains.skia.impl.use
import org.jetbrains.skiko.graphite.GraphiteMetalHost
import org.jetbrains.skiko.graphite.GraphiteVulkanHost

/** A JVM Graphite surface backed by Metal on macOS or Vulkan on Linux. */
public class JvmGraphiteSurface(
    renderer: JvmGraphiteRenderer,
) : AutoCloseable {
    private val backend: Backend = when {
        isMacOs -> MetalBackend(renderer)
        isLinux -> VulkanBackend(renderer)
        else -> error("JVM Graphite requires macOS/Metal or Linux/Vulkan support")
    }

    /** AWT component that owns the native GPU presentation surface. */
    public val component: Component
        get() = backend.component

    /** Records and presents one Graphite frame when the AWT component has a size. */
    public fun render() {
        backend.render()
    }

    override fun close() {
        backend.close()
    }

    /** Whether this JVM implementation has a native Graphite presentation backend. */
    public companion object {
        public val isSupported: Boolean = isMacOs || isLinux

        private val isMacOs: Boolean
            get() = System.getProperty("os.name").equals("Mac OS X", ignoreCase = true)

        private val isLinux: Boolean
            get() = System.getProperty("os.name").equals("Linux", ignoreCase = true)
    }

    private interface Backend : AutoCloseable {
        val component: Component

        fun render()
    }

    private class MetalBackend(
        private val renderer: JvmGraphiteRenderer,
    ) : Backend {
        private val host = GraphiteMetalHost()
        private var graphiteContext: GraphiteContext? = null
        private var recorder: Recorder? = null
        private var lastWidth = 0
        private var lastHeight = 0
        private var closed = false

        override val component: Component
            get() = host

        override fun render() {
            check(!closed) { "JvmGraphiteSurface is closed" }
            if (!host.initialize()) return

            val logicalWidth = host.width
            val logicalHeight = host.height
            if (logicalWidth <= 0 || logicalHeight <= 0) return

            val scale = host.scale
            val width = (logicalWidth * scale).roundToInt().coerceAtLeast(1)
            val height = (logicalHeight * scale).roundToInt().coerceAtLeast(1)
            host.resize(logicalWidth, logicalHeight, scale)
            ensureContext()

            if (width != lastWidth || height != lastHeight) {
                lastWidth = width
                lastHeight = height
                renderer.onSurfaceChanged(width, height)
            }

            val texturePointer = host.nextDrawable()
            if (texturePointer == 0L) return

            var backendTexture: BackendTexture? = null
            var surface: Surface? = null
            var presented = false
            try {
                val currentRecorder = checkNotNull(recorder)
                val currentContext = checkNotNull(graphiteContext)
                backendTexture = BackendTexture.makeMetal(width, height, texturePointer)
                surface = Surface.wrapBackendTexture(
                    recorder = currentRecorder,
                    backendTexture = backendTexture,
                    colorSpace = ColorSpace.sRGB,
                )
                if (surface == null) return

                renderer.onDrawFrame(SkiaGraphiteDrawContext(surface.canvas))
                currentRecorder.snap().use { recording ->
                    currentContext.insertRecording(recording)
                    currentContext.submit(syncCpu = true)
                }
                host.present()
                presented = true
            } finally {
                surface?.close()
                backendTexture?.close()
                if (!presented) host.dropDrawable()
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            recorder?.close()
            recorder = null
            graphiteContext?.close()
            graphiteContext = null
            host.close()
        }

        private fun ensureContext() {
            if (graphiteContext != null) return
            val context = GraphiteContext.makeMetal(host.devicePointer, host.queuePointer)
            val newRecorder = context.makeRecorder()
            try {
                renderer.onSurfaceCreated()
            } catch (error: Throwable) {
                newRecorder.close()
                context.close()
                throw error
            }
            graphiteContext = context
            recorder = newRecorder
        }
    }

    private class VulkanBackend(
        private val renderer: JvmGraphiteRenderer,
    ) : Backend {
        private val host = GraphiteVulkanHost()
        private var graphiteContext: GraphiteContext? = null
        private var recorder: Recorder? = null
        private var lastWidth = 0
        private var lastHeight = 0
        private var closed = false

        override val component: Component
            get() = host

        override fun render() {
            check(!closed) { "JvmGraphiteSurface is closed" }
            if (!host.initialize()) return

            val logicalWidth = host.width
            val logicalHeight = host.height
            if (logicalWidth <= 0 || logicalHeight <= 0) return

            if (!host.resize(logicalWidth, logicalHeight, host.scale)) return
            val width = host.pixelWidth
            val height = host.pixelHeight
            if (width <= 0 || height <= 0) return
            ensureContext()

            if (width != lastWidth || height != lastHeight) {
                lastWidth = width
                lastHeight = height
                renderer.onSurfaceChanged(width, height)
            }

            val imagePointer = host.nextDrawable()
            if (imagePointer == 0L) return

            var backendTexture: BackendTexture? = null
            var surface: Surface? = null
            var presented = false
            try {
                val currentRecorder = checkNotNull(recorder)
                val currentContext = checkNotNull(graphiteContext)
                backendTexture = BackendTexture.makeVulkan(
                    width = width,
                    height = height,
                    format = host.imageFormat,
                    imageUsage = host.imageUsage,
                    imageLayout = host.imageLayout,
                    queueFamilyIndex = host.queueFamilyIndex,
                    imagePtr = imagePointer,
                )
                surface = Surface.wrapBackendTexture(
                    recorder = currentRecorder,
                    backendTexture = backendTexture,
                    colorSpace = ColorSpace.sRGB,
                )
                if (surface == null) return

                renderer.onDrawFrame(SkiaGraphiteDrawContext(surface.canvas))
                currentRecorder.snap().use { recording ->
                    currentContext.insertRecording(recording)
                    currentContext.submit(syncCpu = true)
                }
                host.present()
                presented = true
            } finally {
                surface?.close()
                backendTexture?.close()
                if (!presented) host.dropDrawable()
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            recorder?.close()
            recorder = null
            graphiteContext?.close()
            graphiteContext = null
            host.close()
        }

        private fun ensureContext() {
            if (graphiteContext != null) return
            val context = GraphiteContext.makeVulkan(
                instancePtr = host.instancePointer,
                physicalDevicePtr = host.physicalDevicePointer,
                devicePtr = host.devicePointer,
                queuePtr = host.queuePointer,
                queueFamilyIndex = host.queueFamilyIndex,
            )
            val newRecorder = context.makeRecorder()
            try {
                renderer.onSurfaceCreated()
            } catch (error: Throwable) {
                newRecorder.close()
                context.close()
                throw error
            }
            graphiteContext = context
            recorder = newRecorder
        }
    }

    private class SkiaGraphiteDrawContext(
        private val canvas: Canvas,
    ) : JvmGraphiteDrawContext {
        private var path = PathBuilder()

        override fun clear(color: Long) {
            canvas.clear(color.toInt())
        }

        override fun save() {
            canvas.save()
        }

        override fun restore() {
            canvas.restore()
        }

        override fun translate(x: Float, y: Float) {
            canvas.translate(x, y)
        }

        override fun rotate(degrees: Float) {
            canvas.rotate(degrees)
        }

        override fun beginPath() {
            path = PathBuilder()
        }

        override fun moveTo(x: Float, y: Float) {
            path.moveTo(x, y)
        }

        override fun lineTo(x: Float, y: Float) {
            path.lineTo(x, y)
        }

        override fun closePath() {
            path.closePath()
        }

        override fun drawPath(color: Long, antiAlias: Boolean) {
            val path = path.detach()
            val paint = Paint().apply {
                this.color = color.toInt()
                isAntiAlias = antiAlias
            }
            try {
                canvas.drawPath(path, paint)
            } finally {
                path.close()
                paint.close()
            }
        }
    }
}
