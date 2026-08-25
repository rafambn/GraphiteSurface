package com.rafambn.graphitesurface

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * User-owned frame producer for one [runtime].
 *
 * The renderer does not own or close the runtime. Its callback uses that runtime as receiver, is
 * serialized, and runs only while the runtime has an attached presentation target.
 */
class GraphiteRenderer(
    val runtime: GraphiteEngine,
    renderMode: GraphiteRenderMode = GraphiteRenderMode.Continuous,
    private val renderFrame: suspend GraphiteEngine.(
        frameTimeNanos: Long,
        presentation: GraphitePresentationInfo,
    ) -> Unit,
) {
    private val mutableRenderMode =
        MutableStateFlow(renderMode)
    private val renderRequests = Channel<Unit>(Channel.CONFLATED)
    private val renderMutex = Mutex()

    /** The current scheduling mode. Changing it updates an attached [GraphiteSurface]. */
    var renderMode: GraphiteRenderMode
        get() = mutableRenderMode.value
        set(value) {
            mutableRenderMode.value = value
        }

    internal val renderModes: StateFlow<GraphiteRenderMode> = mutableRenderMode.asStateFlow()

    /**
     * Requests one display-aligned frame in [GraphiteRenderMode.OnDemand].
     *
     * Multiple requests made before the next frame are coalesced. Requests made while a frame is
     * being produced retain at most one additional frame. This method may be called from any
     * thread.
     */
    fun requestRender() {
        if (renderMode == GraphiteRenderMode.OnDemand) {
            renderRequests.trySend(Unit)
        }
    }

    /**
     * Produces one frame with the current monotonic time in [GraphiteRenderMode.Manual].
     *
     * Returns `false` without invoking the callback when no presentation target is attached or the
     * runtime is unavailable.
     */
    suspend fun render(): Boolean = render(platformMonotonicNanos())

    /**
     * Produces one frame with [frameTimeNanos] in [GraphiteRenderMode.Manual].
     *
     * Returns `false` without invoking the callback when no presentation target is attached or the
     * runtime is unavailable. Concurrent calls are executed sequentially.
     */
    suspend fun render(frameTimeNanos: Long): Boolean {
        check(renderMode == GraphiteRenderMode.Manual) {
            "render() requires GraphiteRenderMode.Manual"
        }
        require(frameTimeNanos >= 0L) { "frame time must not be negative" }
        return invokeRenderer(
            frameTimeNanos = frameTimeNanos,
            expectedMode = GraphiteRenderMode.Manual,
            expectedPresentation = null,
        )
    }

    internal fun requestAttachedFrame() {
        renderRequests.trySend(Unit)
    }

    internal suspend fun awaitRenderRequest() {
        renderRequests.receive()
    }

    internal fun tryConsumeRenderRequest(): Boolean = renderRequests.tryReceive().isSuccess

    internal suspend fun renderScheduled(
        frameTimeNanos: Long,
        mode: GraphiteRenderMode,
        presentation: GraphitePresentationInfo,
    ): Boolean = invokeRenderer(frameTimeNanos, mode, presentation)

    private suspend fun invokeRenderer(
        frameTimeNanos: Long,
        expectedMode: GraphiteRenderMode,
        expectedPresentation: GraphitePresentationInfo?,
    ): Boolean = renderMutex.withLock {
        if (renderMode != expectedMode || runtime.state.value != GraphiteEngineState.Ready) {
            return@withLock false
        }
        val attached = runtime.presentation.value as? GraphitePresentationState.Attached
            ?: return@withLock false
        val presentation = attached.info
        if (expectedPresentation != null &&
            presentation.generation != expectedPresentation.generation
        ) {
            return@withLock false
        }
        runtime.renderFrame(frameTimeNanos, presentation)
        true
    }
}
