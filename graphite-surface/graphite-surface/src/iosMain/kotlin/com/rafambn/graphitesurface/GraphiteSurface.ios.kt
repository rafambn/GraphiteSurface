@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    com.rafambn.graphitesurface.ExperimentalGraphiteSurfaceApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.UIKitView
import com.rafambn.graphitesurface.engine.gsBeginPath
import com.rafambn.graphitesurface.engine.gsClear
import com.rafambn.graphitesurface.engine.gsClosePath
import com.rafambn.graphitesurface.engine.gsCreateView
import com.rafambn.graphitesurface.engine.gsDisposeView
import com.rafambn.graphitesurface.engine.gsDrawPath
import com.rafambn.graphitesurface.engine.gsLineTo
import com.rafambn.graphitesurface.engine.gsMoveTo
import com.rafambn.graphitesurface.engine.gsRequestRender
import com.rafambn.graphitesurface.engine.gsRestore
import com.rafambn.graphitesurface.engine.gsRotate
import com.rafambn.graphitesurface.engine.gsSave
import com.rafambn.graphitesurface.engine.gsStartRendering
import com.rafambn.graphitesurface.engine.gsStopRendering
import com.rafambn.graphitesurface.engine.gsTranslate
import kotlinx.cinterop.useContents
import platform.UIKit.UIView
import kotlin.math.roundToInt

@Composable
@ExperimentalGraphiteSurfaceApi
public actual fun GraphiteSurface(
    renderer: GraphiteRenderer,
    modifier: Modifier,
    renderMode: GraphiteRenderMode,
    controller: GraphiteSurfaceController?,
) {
    val adapter = remember(renderer, renderMode) { GraphiteSurfaceAdapter(renderer, renderMode) }

    DisposableEffect(controller, adapter) {
        controller?.setRequestRenderHandler { adapter.requestRender() }
        onDispose {
            controller?.setRequestRenderHandler(null)
        }
    }

    UIKitView(
        factory = { adapter.view },
        modifier = modifier,
        onRelease = { adapter.dispose() },
    )
}

private class GraphiteSurfaceAdapter(
    private val renderer: GraphiteRenderer,
    private val renderMode: GraphiteRenderMode,
) {
    private var engineView: UIView? = null
    private var surfaceCreated = false
    private var lastSize = IntSize.Zero

    val view: UIView
        get() {
            val existing = engineView
            if (existing != null) return existing
            return gsCreateView(renderMode.ordinal).also { created ->
                engineView = created
                gsStartRendering(created) {
                    onFrame(created)
                }
            }
        }

    fun requestRender() {
        engineView?.let(::gsRequestRender)
    }

    fun dispose() {
        engineView?.let(::gsStopRendering)
        engineView?.let(::gsDisposeView)
        engineView = null
    }

    internal fun onFrame(view: UIView) {
        val scale = view.contentScaleFactor
        val size = view.bounds.useContents {
            IntSize(
                (size.width * scale).roundToInt(),
                (size.height * scale).roundToInt(),
            )
        }
        if (!surfaceCreated) {
            surfaceCreated = true
            renderer.onSurfaceCreated()
        }
        if (lastSize != size) {
            lastSize = size
            renderer.onSurfaceChanged(size)
        }
        renderer.onDrawFrame(GSGraphiteDrawContext(view))
    }
}

private class GSGraphiteDrawContext(
    private val view: UIView,
) : GraphiteDrawContext {
    override fun clear(color: Long) {
        gsClear(view, color.toUInt())
    }

    override fun save() {
        gsSave(view)
    }

    override fun restore() {
        gsRestore(view)
    }

    override fun translate(x: Float, y: Float) {
        gsTranslate(view, x, y)
    }

    override fun rotate(degrees: Float) {
        gsRotate(view, degrees)
    }

    override fun beginPath() {
        gsBeginPath(view)
    }

    override fun moveTo(x: Float, y: Float) {
        gsMoveTo(view, x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        gsLineTo(view, x, y)
    }

    override fun closePath() {
        gsClosePath(view)
    }

    override fun drawPath(color: Long, antiAlias: Boolean) {
        gsDrawPath(view, color.toUInt(), if (antiAlias) 1 else 0)
    }
}
