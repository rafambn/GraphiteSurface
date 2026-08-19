@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.rafambn.graphitesurface

import com.rafambn.graphitesurface.engine.GraphiteEngineGraphiteEngineView_iosKt
import kotlinx.cinterop.useContents
import platform.UIKit.UIView
import kotlin.math.roundToInt

internal class GraphiteSurfaceAdapter(
    private val renderer: GraphiteRenderer,
    private val renderMode: GraphiteRenderMode,
) {
    private var engineView: UIView? = null
    private var surfaceCreated = false
    private var lastSize = GraphiteSize.Zero

    val view: UIView
        get() {
            val existing = engineView
            if (existing != null) return existing
            val created = GraphiteEngineGraphiteEngineView_iosKt.gsCreateViewRenderMode(renderMode.ordinal)
                ?: error("Graphite engine returned no native view")
            return created.also {
                engineView = created
                GraphiteEngineGraphiteEngineView_iosKt.gsStartRenderingView(created) {
                    onFrame(created)
                }
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
        val scale = view.contentScaleFactor
        val size = view.bounds.useContents {
            GraphiteSize(
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
