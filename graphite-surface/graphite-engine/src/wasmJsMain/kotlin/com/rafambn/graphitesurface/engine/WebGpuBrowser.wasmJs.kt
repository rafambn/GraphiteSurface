@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.rafambn.graphitesurface.engine

import org.w3c.dom.HTMLCanvasElement
import kotlin.js.JsAny
import kotlin.js.js

internal actual fun configureWebGpuCanvas(canvas: HTMLCanvasElement, device: JsAny): JsAny? = js(
    """
    (function(canvas, device) {
        var context = canvas.getContext('webgpu');
        if (!context) return null;
        context.configure({
            device: device,
            format: navigator.gpu.getPreferredCanvasFormat(),
            alphaMode: 'premultiplied'
        });
        return context;
    })(canvas, device)
    """,
)

internal actual fun currentWebGpuTexture(context: JsAny): JsAny =
    js("context.getCurrentTexture()")

internal actual fun requestWebGpuAnimationFrame(callback: (Double) -> Unit): Int =
    js("window.requestAnimationFrame(callback)")

internal actual fun reportWebGpuError(message: String) {
    js("console.error(message)")
}
