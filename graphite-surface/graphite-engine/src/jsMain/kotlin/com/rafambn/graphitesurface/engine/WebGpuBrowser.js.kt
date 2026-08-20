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
    })(arguments[0], arguments[1])
    """,
)

internal actual fun currentWebGpuTexture(context: JsAny): JsAny =
    js("arguments[0].getCurrentTexture()")

internal actual fun requestWebGpuAnimationFrame(callback: (Double) -> Unit): Int =
    js("window.requestAnimationFrame(arguments[0])")

internal actual fun reportWebGpuError(message: String) {
    js("console.error(arguments[0])")
}
