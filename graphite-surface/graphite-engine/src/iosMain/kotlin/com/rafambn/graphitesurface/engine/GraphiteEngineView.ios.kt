@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    org.jetbrains.skiko.ExperimentalSkikoApi::class,
)

package com.rafambn.graphitesurface.engine

import com.rafambn.graphitesurface.engine.api.GSFrameCallback
import com.rafambn.graphitesurface.engine.api.GSFailureCallback
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.Matrix44
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Surface
import org.jetbrains.skia.gpu.graphite.BackendTexture
import org.jetbrains.skia.gpu.graphite.GraphiteContext
import org.jetbrains.skia.gpu.graphite.wrapBackendTexture
import org.jetbrains.skia.impl.use
import platform.CoreGraphics.CGColorCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSCoder
import platform.Foundation.NSRunLoop
import platform.Foundation.NSSelectorFromString
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.Metal.MTLDeviceProtocol
import platform.QuartzCore.CADisplayLink
import platform.QuartzCore.CAMetalLayer
import platform.UIKit.UIView
import platform.UIKit.UIViewMeta
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_t
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create

private const val GS_RENDER_MODE_CONTINUOUS = 0
private const val GS_RENDER_MODE_ON_DEMAND = 1

@Suppress("unused")
public fun gsCreateView(renderMode: Int): UIView = GraphiteEngineView(renderMode)

@Suppress("unused")
public fun gsDisposeView(view: UIView) {
    (view as? GraphiteEngineView)?.dispose()
}

@Suppress("unused")
public fun gsStartRendering(
    view: UIView,
    callback: GSFrameCallback,
    failureCallback: GSFailureCallback,
) {
    (view as? GraphiteEngineView)?.startRendering(callback, failureCallback)
}

@Suppress("unused")
public fun gsStopRendering(view: UIView) {
    (view as? GraphiteEngineView)?.stopRendering()
}

@Suppress("unused")
public fun gsRequestRender(view: UIView) {
    (view as? GraphiteEngineView)?.requestRender()
}

@Suppress("unused")
public fun gsDrawableWidth(view: UIView): Int =
    frameContextOf(view).width

@Suppress("unused")
public fun gsDrawableHeight(view: UIView): Int =
    frameContextOf(view).height

@Suppress("unused")
public fun gsClear(view: UIView, color: UInt) {
    frameContextOf(view).canvas.clear(color.toInt())
}

@Suppress("unused")
public fun gsSave(view: UIView) {
    frameContextOf(view).canvas.save()
}

@Suppress("unused")
public fun gsRestore(view: UIView) {
    frameContextOf(view).canvas.restore()
}

@Suppress("unused")
public fun gsTranslate(view: UIView, x: Float, y: Float) {
    frameContextOf(view).canvas.translate(x, y)
}

@Suppress("unused")
public fun gsRotate(view: UIView, degrees: Float) {
    frameContextOf(view).canvas.rotate(degrees)
}

@Suppress("unused", "LongParameterList")
public fun gsConcat(
    view: UIView,
    m0: Float, m1: Float, m2: Float, m3: Float,
    m4: Float, m5: Float, m6: Float, m7: Float,
    m8: Float, m9: Float, m10: Float, m11: Float,
    m12: Float, m13: Float, m14: Float, m15: Float,
) {
    frameContextOf(view).canvas.concat(
        Matrix44(
            m0, m4, m8, m12,
            m1, m5, m9, m13,
            m2, m6, m10, m14,
            m3, m7, m11, m15,
        ),
    )
}

@Suppress("unused")
public fun gsClipRect(
    view: UIView,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    antiAlias: Int,
) {
    frameContextOf(view).canvas.clipRect(left, top, right, bottom, antiAlias != 0)
}

@Suppress("unused")
public fun gsBeginPath(view: UIView) {
    frameContextOf(view).path = PathBuilder()
}

@Suppress("unused")
public fun gsMoveTo(view: UIView, x: Float, y: Float) {
    frameContextOf(view).path.moveTo(x, y)
}

@Suppress("unused")
public fun gsLineTo(view: UIView, x: Float, y: Float) {
    frameContextOf(view).path.lineTo(x, y)
}

@Suppress("unused")
public fun gsClosePath(view: UIView) {
    frameContextOf(view).path.closePath()
}

@Suppress("unused")
public fun gsDrawPath(view: UIView, color: UInt, antiAlias: Int) {
    gsDrawStyledPath(view, color, 0, 1f, antiAlias)
}

@Suppress("unused")
public fun gsDrawStyledPath(
    view: UIView,
    color: UInt,
    stroke: Int,
    strokeWidth: Float,
    antiAlias: Int,
) {
    val frame = frameContextOf(view)
    val path = frame.path.detach()
    val paint = makePaint(color, stroke, strokeWidth, antiAlias)
    frame.canvas.drawPath(path, paint)
    path.close()
    paint.close()
}

@Suppress("unused", "LongParameterList")
public fun gsDrawRect(
    view: UIView,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    color: UInt,
    stroke: Int,
    strokeWidth: Float,
    antiAlias: Int,
) {
    makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
        frameContextOf(view).canvas.drawRect(left, top, right, bottom, paint)
    }
}

@Suppress("unused", "LongParameterList")
public fun gsDrawRoundRect(
    view: UIView,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radiusX: Float,
    radiusY: Float,
    color: UInt,
    stroke: Int,
    strokeWidth: Float,
    antiAlias: Int,
) {
    makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
        frameContextOf(view).canvas.drawRRect(
            left,
            top,
            right,
            bottom,
            floatArrayOf(radiusX, radiusY, radiusX, radiusY, radiusX, radiusY, radiusX, radiusY),
            paint,
        )
    }
}

@Suppress("unused", "LongParameterList")
public fun gsDrawOval(
    view: UIView,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    color: UInt,
    stroke: Int,
    strokeWidth: Float,
    antiAlias: Int,
) {
    makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
        frameContextOf(view).canvas.drawOval(left, top, right, bottom, paint)
    }
}

@Suppress("unused", "LongParameterList")
public fun gsDrawCircle(
    view: UIView,
    x: Float,
    y: Float,
    radius: Float,
    color: UInt,
    stroke: Int,
    strokeWidth: Float,
    antiAlias: Int,
) {
    makePaint(color, stroke, strokeWidth, antiAlias).use { paint ->
        frameContextOf(view).canvas.drawCircle(x, y, radius, paint)
    }
}

@Suppress("unused", "LongParameterList")
public fun gsDrawLine(
    view: UIView,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    color: UInt,
    strokeWidth: Float,
    antiAlias: Int,
) {
    makePaint(color, 1, strokeWidth, antiAlias).use { paint ->
        frameContextOf(view).canvas.drawLine(x0, y0, x1, y1, paint)
    }
}

private fun makePaint(color: UInt, stroke: Int, strokeWidth: Float, antiAlias: Int): Paint =
    Paint().apply {
        this.color = color.toInt()
        mode = if (stroke != 0) PaintMode.STROKE else PaintMode.FILL
        this.strokeWidth = strokeWidth
        isAntiAlias = antiAlias != 0
    }

@OptIn(ExperimentalAtomicApi::class)
private class GraphiteEngineView : UIView {
    companion object : UIViewMeta() {
        override fun layerClass() = CAMetalLayer
    }

    private var renderMode: Int = GS_RENDER_MODE_CONTINUOUS
    private val metalLayer: CAMetalLayer
        get() = layer as CAMetalLayer

    private val device: MTLDeviceProtocol =
        MTLCreateSystemDefaultDevice()
            ?: error("Metal is not supported on this device")

    private val queue = device.newCommandQueue()
        ?: error("Could not create a Metal command queue")

    private val renderQueue = dispatch_queue_create("com.rafambn.graphitesurface.render", null)
    private var context: GraphiteContext? = null
    private var recorder: org.jetbrains.skia.gpu.graphite.Recorder? = null
    private val inflightSemaphore =
        dispatch_semaphore_create(metalLayer.maximumDrawableCount.toLong())
    private var displayLink: CADisplayLink? = null
    private val disposed = AtomicBoolean(false)
    private val pendingRender = AtomicBoolean(true)
    private val frameScheduled = AtomicBoolean(false)
    private var frameCallback: GSFrameCallback = null
    private var failureCallback: GSFailureCallback = null
    internal var currentFrameContext: FrameContext? = null

    constructor(
        renderMode: Int,
        frame: CValue<CGRect> = CGRectMake(0.0, 0.0, 0.0, 0.0),
    ) : super(frame) {
        this.renderMode = renderMode
        configureMetalLayer()
        if (renderMode == GS_RENDER_MODE_CONTINUOUS) {
            startDisplayLink()
        }
    }

    @Suppress("UNUSED")
    @kotlinx.cinterop.ObjCObjectBase.OverrideInit
    constructor(coder: NSCoder) : super(coder) {
        error("init(coder:) is not supported")
    }

    fun startRendering(callback: GSFrameCallback, onFailure: GSFailureCallback) {
        dispatch_async(renderQueue) {
            if (disposed.load()) return@dispatch_async
            frameCallback = callback
            failureCallback = onFailure
            if (callback != null && pendingRender.load()) scheduleDraw()
        }
    }

    fun stopRendering() {
        dispatch_async(renderQueue) {
            frameCallback = null
            failureCallback = null
        }
    }

    fun requestRender() {
        if (disposed.load()) return
        pendingRender.store(true)
        scheduleDraw()
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        displayLink?.invalidate()
        displayLink = null
        stopRendering()
        dispatch_async(renderQueue) {
            recorder?.close()
            recorder = null
            context?.close()
            context = null
        }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val scale = window?.screen?.scale ?: 1.0
        contentScaleFactor = scale
        metalLayer.drawableSize = bounds.useContents {
            CGSizeMake(size.width * scale, size.height * scale)
        }
        if (renderMode == GS_RENDER_MODE_ON_DEMAND) {
            requestRender()
        }
    }

    private fun configureMetalLayer() {
        @Suppress("USELESS_CAST")
        metalLayer.device = device as objcnames.protocols.MTLDeviceProtocol?
        metalLayer.pixelFormat = MTLPixelFormatBGRA8Unorm
        metalLayer.framebufferOnly = false
        metalLayer.allowsNextDrawableTimeout = false
        opaque = false
        metalLayer.backgroundColor = CGColorCreate(
            CGColorSpaceCreateDeviceRGB(),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0).usePinned { it.addressOf(0) },
        )
    }

    private fun startDisplayLink() {
        val link = CADisplayLink.displayLinkWithTarget(
            target = DisplayLinkProxy { requestRender() },
            selector = NSSelectorFromString(DisplayLinkProxy::handleDisplayLinkTick.name),
        )
        link.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoop.mainRunLoop.currentMode)
        displayLink = link
    }

    private fun scheduleDraw() {
        if (disposed.load() || !frameScheduled.compareAndSet(false, true)) return
        dispatch_async(renderQueue) {
            try {
                drawOnRenderQueue()
            } catch (error: Throwable) {
                pendingRender.store(false)
                failureCallback?.invoke(error.message ?: error.toString())
            } finally {
                frameScheduled.store(false)
                if (pendingRender.load() && !disposed.load()) scheduleDraw()
            }
        }
    }

    private fun drawOnRenderQueue() {
        if (disposed.load()) return
        if (renderMode == GS_RENDER_MODE_ON_DEMAND && !pendingRender.load()) return
        pendingRender.store(false)
        if (frameCallback == null) return

        val (width, height) = metalLayer.drawableSize.useContents {
            width.toInt() to height.toInt()
        }
        if (width <= 0 || height <= 0) return
        dispatch_semaphore_wait(inflightSemaphore, DISPATCH_TIME_FOREVER)

        var completionOwnsSemaphore = false
        try {
            val drawable = metalLayer.nextDrawable() ?: return
            val currentContext = context ?: GraphiteContext
                .makeMetal(device.objcPtr(), queue.objcPtr())
                .also { context = it }
            val currentRecorder = recorder ?: currentContext.makeRecorder().also { recorder = it }
            BackendTexture.makeMetal(width, height, drawable.texture.objcPtr()).use { backendTexture ->
                val surface = Surface.wrapBackendTexture(
                    recorder = currentRecorder,
                    backendTexture = backendTexture,
                    colorSpace = ColorSpace.sRGB,
                ) ?: error("Could not wrap the current Metal drawable")

                surface.use {
                    val callback = frameCallback
                    if (callback != null) {
                        currentFrameContext = FrameContext(it.canvas, width, height)
                        try {
                            callback()
                        } finally {
                            currentFrameContext = null
                        }
                        currentRecorder.snap().use { recording ->
                            currentContext.insertRecording(recording)
                            currentContext.submit(syncCpu = true)
                        }
                    }
                }
            }

            val commandBuffer = queue.commandBuffer()
                ?: error("Could not create a Metal presentation command buffer")
            commandBuffer.presentDrawable(drawable)
            commandBuffer.addCompletedHandler {
                dispatch_semaphore_signal(inflightSemaphore)
            }
            commandBuffer.commit()
            completionOwnsSemaphore = true
        } finally {
            if (!completionOwnsSemaphore) dispatch_semaphore_signal(inflightSemaphore)
        }
    }
}

private fun frameContextOf(view: UIView): FrameContext =
    (view as? GraphiteEngineView)?.currentFrameContext
        ?: error("Graphite draw calls are only valid during onDrawFrame")
