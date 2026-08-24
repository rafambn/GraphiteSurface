@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.rafambn.graphitesurface.engine

import org.w3c.dom.HTMLCanvasElement
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.js

/** Browser-only operations that are not yet represented by the Kotlin DOM bindings. */
internal fun requestWebGpuDevice(): Promise<JsAny?> = js(
    """
    Promise.resolve().then(function() {
        if (!navigator.gpu) {
            throw new Error('WebGPU is not available in this browser');
        }
        return navigator.gpu.requestAdapter();
    }).then(function(adapter) {
        if (!adapter) return null;
        return adapter.requestDevice();
    })
    """,
)

internal expect fun configureWebGpuCanvas(canvas: HTMLCanvasElement, device: JsAny): JsAny?

internal expect fun currentWebGpuTexture(context: JsAny): JsAny

internal expect fun requestWebGpuAnimationFrame(callback: (Double) -> Unit): Int

internal expect fun reportWebGpuError(message: String)

internal expect fun supportsGraphiteRenderWorker(canvas: HTMLCanvasElement): Boolean

internal expect fun createGraphiteRenderWorker(
    canvas: HTMLCanvasElement,
    onReady: () -> Unit,
    onFailure: (String) -> Unit,
    onDisposed: () -> Unit,
): JsAny

internal expect fun postGraphiteRenderFrame(
    worker: JsAny,
    width: Int,
    height: Int,
    commands: String,
)

internal expect fun disposeGraphiteRenderWorker(worker: JsAny)
