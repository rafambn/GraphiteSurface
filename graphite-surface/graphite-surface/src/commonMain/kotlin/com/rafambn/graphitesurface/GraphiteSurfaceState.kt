package com.rafambn.graphitesurface

import androidx.compose.runtime.Stable
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Controls frame requests for a [GraphiteSurface] using [GraphiteRenderMode.OnDemand]. */
@Stable
@OptIn(ExperimentalAtomicApi::class)
internal class GraphiteSurfaceState {
    private val requestFrameHandler: AtomicReference<(() -> Unit)?> = AtomicReference(null)

    internal fun setRequestFrameHandler(handler: (() -> Unit)?) {
        requestFrameHandler.store(handler)
    }

    internal fun clearRequestFrameHandler(handler: () -> Unit) {
        requestFrameHandler.compareAndSet(handler, null)
    }

    /**
     * Requests one frame in [GraphiteRenderMode.OnDemand].
     *
     * This has no effect while the surface is not attached or renders continuously.
     * This method may be called from any thread.
     */
    internal fun requestFrame() {
        requestFrameHandler.load()?.invoke()
    }
}
