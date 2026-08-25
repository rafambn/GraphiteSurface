@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntSize
import com.rafambn.graphitesurface.engine.GraphiteEngineGraphiteEngineView_iosKt
import platform.UIKit.UIView

internal class GraphiteSurfaceAdapter(
    private val renderer: GraphitePresentationRenderer,
    private val renderMode: GraphiteRenderMode,
) {
    private var engineView: UIView? = null
    private var surfaceCreated = false
    private var lastSize = IntSize.Zero

    val view: UIView
        get() {
            val existing = engineView
            if (existing != null) return existing
            val created = GraphiteEngineGraphiteEngineView_iosKt.gsCreateViewRenderMode(renderMode.ordinal)
                ?: error("Graphite engine returned no native view")
            return created.also {
                engineView = created
                GraphiteEngineGraphiteEngineView_iosKt.gsStartRenderingView(
                    created,
                    callback = { onFrame(created) },
                    failureCallback = { message ->
                        renderer.onSurfaceError(
                            IllegalStateException(message ?: "The iOS Graphite render worker failed"),
                        )
                    },
                )
            }
        }

    fun requestRender() {
        engineView?.let { GraphiteEngineGraphiteEngineView_iosKt.gsRequestRenderView(it) }
    }

    fun dispose() {
        engineView?.let { GraphiteEngineGraphiteEngineView_iosKt.gsStopRenderingView(it) }
        engineView?.let { GraphiteEngineGraphiteEngineView_iosKt.gsDisposeViewView(it) }
        engineView = null
    }

    private fun onFrame(view: UIView) {
        val width = GraphiteEngineGraphiteEngineView_iosKt.gsDrawableWidthView(view)
        val height = GraphiteEngineGraphiteEngineView_iosKt.gsDrawableHeightView(view)
        val size = IntSize(width, height)
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
