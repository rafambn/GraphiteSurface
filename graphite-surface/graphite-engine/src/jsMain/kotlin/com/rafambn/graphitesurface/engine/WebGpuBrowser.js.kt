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

internal actual fun supportsGraphiteRenderWorker(canvas: HTMLCanvasElement): Boolean = js(
    """
    Boolean(
      navigator.gpu &&
      globalThis.crossOriginIsolated === true &&
      typeof SharedArrayBuffer === 'function' &&
      typeof Worker === 'function' &&
      arguments[0] &&
      typeof arguments[0].transferControlToOffscreen === 'function'
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
    (function(canvas, onReady, onFailure, onDisposed) {
      const offscreen = canvas.transferControlToOffscreen();
      const worker = new Worker(
        new URL('./graphite-render-worker.mjs', import.meta.url),
        { type: 'module', name: 'GraphiteRender' }
      );
      worker.onmessage = function(event) {
        const message = event.data || {};
        if (message.type === 'ready') onReady();
        else if (message.type === 'error') onFailure(String(message.message || 'render Worker failed'));
        else if (message.type === 'disposed') onDisposed();
      };
      worker.onerror = function(event) {
        onFailure(String(event.message || 'render Worker terminated unexpectedly'));
      };
      worker.postMessage({ type: 'init', canvas: offscreen }, [offscreen]);
      return worker;
    })(arguments[0], arguments[1], arguments[2], arguments[3])
    """,
)

internal actual fun postGraphiteRenderFrame(
    worker: JsAny,
    width: Int,
    height: Int,
    commands: String,
) {
    js("arguments[0].postMessage({ type: 'frame', width: arguments[1], height: arguments[2], commands: arguments[3] })")
}

internal actual fun disposeGraphiteRenderWorker(worker: JsAny) {
    js(
        """
        (function(worker) {
          worker.postMessage({ type: 'dispose' });
          setTimeout(function() { worker.terminate(); }, 2000);
        })(arguments[0])
        """,
    )
}
