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
import com.rafambn.graphitesurface.engine.GraphiteEngineGraphiteEngineViewKt
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
            return GraphiteEngineGraphiteEngineViewKt.gsCreateViewRenderMode(renderMode.ordinal)!!.also { created ->
                engineView = created
                GraphiteEngineGraphiteEngineViewKt.gsStartRenderingView(created) {
                    onFrame(created)
                }
            }
        }

    fun requestRender() {
        engineView?.let { GraphiteEngineGraphiteEngineViewKt.gsRequestRenderView(it) }
    }

    fun dispose() {
        engineView?.let { GraphiteEngineGraphiteEngineViewKt.gsStopRenderingView(it) }
        engineView?.let { GraphiteEngineGraphiteEngineViewKt.gsDisposeViewView(it) }
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
        GraphiteEngineGraphiteEngineViewKt.gsClearView(view, color.toUInt())
    }

    override fun save() {
        GraphiteEngineGraphiteEngineViewKt.gsSaveView(view)
    }

    override fun restore() {
        GraphiteEngineGraphiteEngineViewKt.gsRestoreView(view)
    }

    override fun translate(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineViewKt.gsTranslateView(view, x, y)
    }

    override fun rotate(degrees: Float) {
        GraphiteEngineGraphiteEngineViewKt.gsRotateView(view, degrees)
    }

    override fun beginPath() {
        GraphiteEngineGraphiteEngineViewKt.gsBeginPathView(view)
    }

    override fun moveTo(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineViewKt.gsMoveToView(view, x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineViewKt.gsLineToView(view, x, y)
    }

    override fun closePath() {
        GraphiteEngineGraphiteEngineViewKt.gsClosePathView(view)
    }

    override fun drawPath(color: Long, antiAlias: Boolean) {
        GraphiteEngineGraphiteEngineViewKt.gsDrawPathView(view, color.toUInt(), if (antiAlias) 1 else 0)
    }
}
