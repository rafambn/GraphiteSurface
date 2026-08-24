@file:OptIn(org.jetbrains.skiko.ExperimentalSkikoApi::class)

package com.rafambn.graphitesurface.engine

import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Matrix44
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
    private val renderer: JvmGraphiteRenderer,
) : AutoCloseable {
    private val backend: Backend = when {
        isMacOs -> MetalBackend(renderer)
        isLinux -> VulkanBackend(renderer)
        else -> error("JVM Graphite requires macOS/Metal or Linux/Vulkan support")
    }
    private val renderExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GraphiteRender").apply { isDaemon = true }
    }
    private val renderRequested = AtomicBoolean(false)
    private val renderScheduled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val componentListener = object : ComponentAdapter() {
        override fun componentShown(event: ComponentEvent) {
            render()
        }

        override fun componentResized(event: ComponentEvent) {
            render()
        }
    }

    init {
        backend.component.addComponentListener(componentListener)
    }

    /** AWT component that owns the native GPU presentation surface. */
    public val component: Component
        get() = backend.component

    /** Records and presents one Graphite frame when the AWT component has a size. */
    public fun render() {
        if (closed.get() || failed.get()) return
        renderRequested.set(true)
        scheduleRender()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        backend.component.removeComponentListener(componentListener)
        renderRequested.set(false)
        renderExecutor.execute {
            try {
                backend.close()
            } finally {
                renderExecutor.shutdown()
            }
        }
    }

    private fun scheduleRender() {
        if (closed.get() || !renderScheduled.compareAndSet(false, true)) return
        renderExecutor.execute {
            try {
                renderRequested.set(false)
                if (!closed.get() && !failed.get()) {
                    try {
                        backend.render()
                    } catch (error: Throwable) {
                        if (failed.compareAndSet(false, true)) renderer.onSurfaceError(error)
                    }
                }
            } finally {
                renderScheduled.set(false)
                if (renderRequested.get() && !closed.get() && !failed.get()) scheduleRender()
            }
        }
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

        override fun concat(columnMajor: FloatArray) {
            require(columnMajor.size == 16)
            canvas.concat(
                Matrix44(
                    columnMajor[0], columnMajor[4], columnMajor[8], columnMajor[12],
                    columnMajor[1], columnMajor[5], columnMajor[9], columnMajor[13],
                    columnMajor[2], columnMajor[6], columnMajor[10], columnMajor[14],
                    columnMajor[3], columnMajor[7], columnMajor[11], columnMajor[15],
                ),
            )
        }

        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, antiAlias: Boolean) {
            canvas.clipRect(left, top, right, bottom, antiAlias)
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

        override fun drawPath(
            verbs: ByteArray,
            points: FloatArray,
            color: Long,
            stroke: Boolean,
            strokeWidth: Float,
            antiAlias: Boolean,
        ) {
            val builder = PathBuilder()
            var pointIndex = 0
            verbs.forEach { verb ->
                when (verb.toInt()) {
                    1 -> builder.moveTo(points[pointIndex++], points[pointIndex++])
                    2 -> builder.lineTo(points[pointIndex++], points[pointIndex++])
                    3 -> builder.closePath()
                    else -> error("Unknown Graphite path verb: $verb")
                }
            }
            builder.detach().use { path ->
                makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
                    canvas.drawPath(path, paint)
                }
            }
        }

        override fun drawRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            color: Long,
            stroke: Boolean,
            strokeWidth: Float,
            antiAlias: Boolean,
        ) {
            makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
            canvas.drawRect(left, top, right, bottom, paint)
            }
        }

        override fun drawRoundRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            radiusX: Float,
            radiusY: Float,
            color: Long,
            stroke: Boolean,
            strokeWidth: Float,
            antiAlias: Boolean,
        ) {
            makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
                canvas.drawRRect(
                    left,
                    top,
                    right,
                    bottom,
                    floatArrayOf(radiusX, radiusY, radiusX, radiusY, radiusX, radiusY, radiusX, radiusY),
                    paint,
                )
            }
        }

        override fun drawOval(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            color: Long,
            stroke: Boolean,
            strokeWidth: Float,
            antiAlias: Boolean,
        ) {
            makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
                canvas.drawOval(left, top, right, bottom, paint)
            }
        }

        override fun drawCircle(
            x: Float,
            y: Float,
            radius: Float,
            color: Long,
            stroke: Boolean,
            strokeWidth: Float,
            antiAlias: Boolean,
        ) {
            makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
                canvas.drawCircle(x, y, radius, paint)
            }
        }

        override fun drawLine(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            color: Long,
            strokeWidth: Float,
            antiAlias: Boolean,
        ) {
            makePaint(color, true, strokeWidth, antiAlias).use { paint ->
                canvas.drawLine(x0, y0, x1, y1, paint)
            }
        }

        private fun makePaint(
            color: Long,
            stroke: Boolean,
            strokeWidth: Float,
            antiAlias: Boolean,
        ): Paint = Paint().apply {
            this.color = color.toInt()
            mode = if (stroke) PaintMode.STROKE else PaintMode.FILL
            this.strokeWidth = strokeWidth
            isAntiAlias = antiAlias
        }
    }
}
