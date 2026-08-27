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

internal actual fun supportsGraphiteRenderWorker(canvas: HTMLCanvasElement): Boolean = js(
    """
    Boolean(
      navigator.gpu &&
      globalThis.crossOriginIsolated === true &&
      typeof SharedArrayBuffer === 'function' &&
      typeof Worker === 'function' &&
      canvas &&
      typeof canvas.transferControlToOffscreen === 'function'
    )
    """,
)

internal actual fun createGraphiteRenderWorker(
    canvas: HTMLCanvasElement,
    onReady: () -> Unit,
    onFailure: (String) -> Unit,
    onDisposed: () -> Unit,
): JsAny = js(
    """
    (() => {
      const offscreen = canvas.transferControlToOffscreen();
      const worker = new Worker(
        new URL('./graphite-render-worker.mjs', import.meta.url),
        { type: 'module', name: 'GraphiteRender' }
      );
      worker.onmessage = (event) => {
        const message = event.data || {};
        if (message.type === 'ready') onReady();
        else if (message.type === 'error') onFailure(String(message.message || 'render Worker failed'));
        else if (message.type === 'disposed') onDisposed();
      };
      worker.onerror = (event) => onFailure(String(event.message || 'render Worker terminated unexpectedly'));
      worker.postMessage({ type: 'init', canvas: offscreen }, [offscreen]);
      return worker;
    })()
    """,
)

internal actual fun postGraphiteRenderFrame(
    worker: JsAny,
    width: Int,
    height: Int,
    commands: String,
) {
    js("worker.postMessage({ type: 'frame', width: width, height: height, commands: commands })")
}

internal actual fun disposeGraphiteRenderWorker(worker: JsAny) {
    js(
        """
        worker.postMessage({ type: 'dispose' });
        setTimeout(() => worker.terminate(), 2000);
        """,
    )
}
