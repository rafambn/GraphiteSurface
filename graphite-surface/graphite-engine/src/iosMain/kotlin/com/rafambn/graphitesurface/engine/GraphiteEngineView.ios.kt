@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    org.jetbrains.skiko.ExperimentalSkikoApi::class,
)

package com.rafambn.graphitesurface.engine

import com.rafambn.graphitesurface.engine.api.GSFrameCallback
import kotlinx.cinterop.CValue
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.Paint
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

private const val GS_RENDER_MODE_CONTINUOUSLY = 0
private const val GS_RENDER_MODE_WHEN_DIRTY = 1

@Suppress("unused")
public fun gsCreateView(renderMode: Int): UIView = GraphiteEngineView(renderMode)

@Suppress("unused")
public fun gsDisposeView(view: UIView) {
    (view as? GraphiteEngineView)?.dispose()
}

@Suppress("unused")
public fun gsStartRendering(view: UIView, callback: GSFrameCallback) {
    (view as? GraphiteEngineView)?.startRendering(callback)
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
    val frame = frameContextOf(view)
    val path = frame.path.detach()
    val paint = Paint().apply {
        this.color = color.toInt()
        isAntiAlias = antiAlias != 0
    }
    frame.canvas.drawPath(path, paint)
    path.close()
    paint.close()
}

private class GraphiteEngineView : UIView {
    companion object : UIViewMeta() {
        override fun layerClass() = CAMetalLayer
    }

    private var renderMode: Int = GS_RENDER_MODE_CONTINUOUSLY
    private val metalLayer: CAMetalLayer
        get() = layer as CAMetalLayer

    private val device: MTLDeviceProtocol =
        MTLCreateSystemDefaultDevice()
            ?: error("Metal is not supported on this device")

    private val queue = device.newCommandQueue()
        ?: error("Could not create a Metal command queue")

    private val context = GraphiteContext.makeMetal(device.objcPtr(), queue.objcPtr())
    private val recorder = context.makeRecorder()
    private val inflightSemaphore =
        dispatch_semaphore_create(metalLayer.maximumDrawableCount.toLong())
    private var displayLink: CADisplayLink? = null
    private var disposed = false
    private var pendingRender = true
    private var frameCallback: GSFrameCallback = null
    internal var currentFrameContext: FrameContext? = null

    constructor(
        renderMode: Int,
        frame: CValue<CGRect> = CGRectMake(0.0, 0.0, 0.0, 0.0),
    ) : super(frame) {
        this.renderMode = renderMode
        configureMetalLayer()
        if (renderMode == GS_RENDER_MODE_CONTINUOUSLY) {
            startDisplayLink()
        }
    }

    @Suppress("UNUSED")
    @kotlinx.cinterop.ObjCObjectBase.OverrideInit
    constructor(coder: NSCoder) : super(coder) {
        error("init(coder:) is not supported")
    }

    fun startRendering(callback: GSFrameCallback) {
        frameCallback = callback
        if (callback != null && pendingRender) {
            draw()
        }
    }

    fun stopRendering() {
        frameCallback = null
    }

    fun requestRender() {
        if (disposed) return
        pendingRender = true
        draw()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        displayLink?.invalidate()
        displayLink = null
        stopRendering()
        recorder.close()
        context.close()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val scale = window?.screen?.scale ?: 1.0
        contentScaleFactor = scale
        metalLayer.drawableSize = bounds.useContents {
            CGSizeMake(size.width * scale, size.height * scale)
        }
        if (renderMode == GS_RENDER_MODE_WHEN_DIRTY) {
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
            target = DisplayLinkProxy { draw() },
            selector = NSSelectorFromString(DisplayLinkProxy::handleDisplayLinkTick.name),
        )
        link.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoop.mainRunLoop.currentMode)
        displayLink = link
    }

    private fun draw() {
        if (disposed) return
        if (renderMode == GS_RENDER_MODE_WHEN_DIRTY && !pendingRender) return
        pendingRender = false
        if (frameCallback == null) return

        val (width, height) = metalLayer.drawableSize.useContents {
            width.toInt() to height.toInt()
        }
        if (width <= 0 || height <= 0) return
        dispatch_semaphore_wait(inflightSemaphore, DISPATCH_TIME_FOREVER)

        val drawable = metalLayer.nextDrawable()
        if (drawable == null) {
            dispatch_semaphore_signal(inflightSemaphore)
            return
        }

        val backendTexture = BackendTexture.makeMetal(width, height, drawable.texture.objcPtr())
        val surface = Surface.wrapBackendTexture(
            recorder = recorder,
            backendTexture = backendTexture,
            colorSpace = ColorSpace.sRGB,
        )
        if (surface == null) {
            dispatch_semaphore_signal(inflightSemaphore)
            return
        }

        surface.use {
            val callback = frameCallback
            if (callback != null) {
                currentFrameContext = FrameContext(it.canvas)
                try {
                    callback()
                } finally {
                    currentFrameContext = null
                }
                recorder.snap().use { recording ->
                    context.insertRecording(recording)
                    context.submit(syncCpu = true)
                }
            }
        }

        val commandBuffer = queue.commandBuffer()!!
        commandBuffer.presentDrawable(drawable)
        commandBuffer.addCompletedHandler {
            dispatch_semaphore_signal(inflightSemaphore)
        }
        commandBuffer.commit()
    }
}

private fun frameContextOf(view: UIView): FrameContext =
    (view as? GraphiteEngineView)?.currentFrameContext
        ?: error("Graphite draw calls are only valid during onDrawFrame")
