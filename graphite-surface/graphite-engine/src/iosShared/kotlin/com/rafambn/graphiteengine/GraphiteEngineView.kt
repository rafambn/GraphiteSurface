@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
    org.jetbrains.skiko.ExperimentalSkikoApi::class,
)

package com.rafambn.graphiteengine

import kotlinx.cinterop.CValue
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.pin
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Color
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
import platform.Foundation.NSRunLoop
import platform.Foundation.NSCoder
import platform.Foundation.NSSelectorFromString
import platform.Metal.MTLCommandBufferProtocol
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.Metal.MTLDeviceProtocol
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.QuartzCore.CADisplayLink
import platform.QuartzCore.CAMetalLayer
import platform.UIKit.UIView
import platform.UIKit.UIViewMeta
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_t
import platform.darwin.dispatch_semaphore_wait
import kotlin.math.min
import kotlin.time.TimeSource

@kotlinx.cinterop.ExportObjCClass
public class GraphiteEngineView : UIView {
    public companion object : UIViewMeta() {
        override fun layerClass() = CAMetalLayer
    }

    private val metalLayer: CAMetalLayer
        get() = layer as CAMetalLayer

    private val device: MTLDeviceProtocol =
        MTLCreateSystemDefaultDevice()
            ?: error("Metal is not supported on this device")

    private val queue = device.newCommandQueue()
        ?: error("Could not create a Metal command queue")

    private val context = run {
        println("GraphiteEngine: creating Metal context")
        GraphiteContext.makeMetal(device.objcPtr(), queue.objcPtr()).also {
            println("GraphiteEngine: Metal context created")
        }
    }
    private val recorder = context.makeRecorder().also {
        println("GraphiteEngine: recorder created")
    }
    private val startTime = TimeSource.Monotonic.markNow()
    private val paint = Paint().apply {
        color = Color.RED
        isAntiAlias = true
    }
    private val inflightSemaphore = dispatch_semaphore_create(metalLayer.maximumDrawableCount.toLong())
    private var displayLink: CADisplayLink? = null
    private var disposed = false
    private var frameCount = 0L
    private var lastReport = 0L
    private var submitTotalNanos = 0L

    public constructor(frame: CValue<CGRect> = CGRectMake(0.0, 0.0, 0.0, 0.0)) : super(frame) {
        println("GraphiteEngine: maximumDrawableCount=${metalLayer.maximumDrawableCount}")
        configureMetalLayer()
        startDisplayLink()
    }

    @Suppress("UNUSED")
    @kotlinx.cinterop.ObjCObjectBase.OverrideInit
    public constructor(coder: NSCoder) : super(coder) {
        error("init(coder:) is not supported")
    }

    public fun dispose() {
        if (disposed) return
        disposed = true
        displayLink?.invalidate()
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
        frameCount++
        if (frameCount % 2L != 0L) return

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
            val elapsedSeconds = startTime.elapsedNow().inWholeMilliseconds / 1_000.0
            it.canvas.clear(Color.WHITE)
            drawTriangle(it, width, height, elapsedSeconds)

            recorder.snap().use { recording ->
                context.insertRecording(recording)
                val submitStart = TimeSource.Monotonic.markNow()
                context.submit(syncCpu = true)
                submitTotalNanos += submitStart.elapsedNow().inWholeNanoseconds
            }

            if (frameCount == 120L) {
                dumpTextureStats(drawable.texture, width, height)
            }
        }

        frameCount++
        if (frameCount % 60 == 0L) {
            val elapsed = startTime.elapsedNow().inWholeMilliseconds
            val avgSubmitMs = submitTotalNanos / frameCount / 1_000_000.0
            val nowNanos = TimeSource.Monotonic.markNow().elapsedNow().inWholeNanoseconds
            println("GraphiteEngine: frames=$frameCount fps=${frameCount * 1_000.0 / elapsed} avgSubmitMs=$avgSubmitMs lastReportDeltaNs=${nowNanos - lastReport}")
            lastReport = nowNanos
        }

        val commandBuffer = queue.commandBuffer()!!
        commandBuffer.label = "GraphiteEnginePresent"
        commandBuffer.presentDrawable(drawable)
        commandBuffer.addCompletedHandler {
            dispatch_semaphore_signal(inflightSemaphore)
        }
        commandBuffer.commit()
    }


    private fun dumpTextureStats(texture: objcnames.protocols.MTLTextureProtocol, width: Int, height: Int) {
        try {
            val byteLength = (width * height * 4).toULong()
            val buffer = queue.device.newBufferWithLength(byteLength, platform.Metal.MTLResourceStorageModeShared)
            if (buffer == null) {
                println("GraphiteEngine: buffer alloc failed")
                return
            }
            val commandBuffer = queue.commandBuffer()!!
            val blitEncoder = commandBuffer.blitCommandEncoder()!!
            blitEncoder.copyFromTexture(
                texture as platform.Metal.MTLTextureProtocol,
                sourceSlice = 0u,
                sourceLevel = 0u,
                sourceOrigin = platform.Metal.MTLOriginMake(0u, 0u, 0u),
                sourceSize = platform.Metal.MTLSizeMake(width.toULong(), height.toULong(), 1u),
                toBuffer = buffer,
                destinationOffset = 0u,
                destinationBytesPerRow = (width * 4).toULong(),
                destinationBytesPerImage = byteLength,
            )
            blitEncoder.endEncoding()
            commandBuffer.commit()
            commandBuffer.waitUntilCompleted()
            val contents = buffer.contents()
            val pixels = ByteArray(width * height * 4)
            val pinned = pixels.pin()
            try {
                platform.posix.memcpy(pinned.addressOf(0), contents, byteLength)
            } finally {
                pinned.unpin()
            }
            val band = 128
            val sb = StringBuilder("GraphiteEngine: texture readback $width x $height\n")
            for (y in 0 until height step band) {
                var white = 0
                var red = 0
                var other = 0
                for (yy in y until min(y + band, height)) {
                    for (x in 0 until width step 16) {
                        val i = (yy * width + x) * 4
                        val b = pixels[i].toInt() and 0xFF
                        val g = pixels[i + 1].toInt() and 0xFF
                        val r = pixels[i + 2].toInt() and 0xFF
                        when {
                            r > 240 && g > 240 && b > 240 -> white++
                            r > 180 && g < 90 && b < 90 -> red++
                            else -> other++
                        }
                    }
                }
                sb.append("  y=$y: w=$white r=$red o=$other\n")
            }
            println(sb.toString())
        } catch (t: Throwable) {
            println("GraphiteEngine: readback failed: $t")
        }
    }

    private fun drawTriangle(surface: Surface, width: Int, height: Int, elapsedSeconds: Double) {
        val size = min(width, height) * 0.35f
        val canvas = surface.canvas
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.rotate((elapsedSeconds * 90.0).toFloat())
        val path = PathBuilder().apply {
            moveTo(0f, -size)
            lineTo(size, size)
            lineTo(-size, size)
            closePath()
        }.detach()
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}

private class DisplayLinkProxy(
    private val callback: () -> Unit,
) : NSObject() {
    @kotlinx.cinterop.ObjCAction
    fun handleDisplayLinkTick() {
        callback()
    }
}

@kotlin.native.CName("GraphiteEngineCreateView")
public fun graphiteEngineCreateView(): UIView = GraphiteEngineView()

@kotlin.native.CName("GraphiteEngineDisposeView")
public fun graphiteEngineDisposeView(view: UIView) {
    (view as? GraphiteEngineView)?.dispose()
}