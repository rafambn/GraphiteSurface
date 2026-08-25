@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntSize
import com.rafambn.graphitesurface.engine.WebGraphiteEngine
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement

internal class WebGraphiteSurfaceAdapter(
    private val renderer: GraphitePresentationRenderer,
    renderMode: GraphiteRenderMode,
) {
    private val engineContinuously = renderMode == GraphiteRenderMode.Continuous
    private var host: HTMLDivElement? = null
    private var canvas: HTMLCanvasElement? = null
    private var engine: WebGraphiteEngine? = null
    private var disposed = false

    fun createCanvas(): HTMLDivElement {
        host?.let { return it }
        check(!disposed) { "Graphite surface has already been disposed" }

        val createdHost = document.createElement("div") as HTMLDivElement
        createdHost.style.width = "100%"
        createdHost.style.height = "100%"
        createdHost.style.display = "block"

        val created = document.createElement("canvas") as HTMLCanvasElement
        created.style.width = "100%"
        created.style.height = "100%"
        created.style.display = "block"
        createdHost.appendChild(created)
        host = createdHost
        canvas = created

        val createdEngine = WebGraphiteEngine(
            canvas = created,
            continuously = engineContinuously,
            onSurfaceCreated = renderer::onSurfaceCreated,
            onSurfaceChanged = { width, height ->
        renderer.onSurfaceChanged(IntSize(width, height))
            },
            onDrawFrame = { context ->
                renderer.onDrawFrame(WebGraphiteDrawContext(context))
            },
            onSurfaceError = renderer::onSurfaceError,
        )
        engine = createdEngine
        createdEngine.start()
        window.setTimeout({
            // Kotlin/Wasm can finish its first Compose placement pass with the
            // interop wrapper detached. Keep the real WebGPU host visible while
            // that wrapper is being attached or reused.
            if (document.documentElement?.contains(createdHost) != true) {
                createdHost.style.position = "fixed"
                createdHost.style.left = "0"
                createdHost.style.top = "0"
                document.body?.appendChild(createdHost)
                createdEngine.resizeToDisplaySize()
            }
            null
        }, 0)
        return createdHost
    }

    fun updateSize() {
        engine?.resizeToDisplaySize()
    }

    fun requestRender() {
        engine?.requestRender()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        engine?.dispose()
        engine = null
        host = null
        canvas = null
    }
}
