package com.rafambn.graphitesurface

import kotlinx.coroutines.channels.Channel
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
    val renderMode: GraphiteRenderMode = GraphiteRenderMode.Continuous,
    private val renderFrame: suspend GraphiteEngine.(
        frameTimeNanos: Long,
        presentation: GraphitePresentationInfo,
    ) -> Unit,
) {
    private val renderRequests = Channel<Unit>(Channel.CONFLATED)
    private val renderMutex = Mutex()

    /**
     * Requests one display-aligned frame in [GraphiteRenderMode.OnDemand].
     *
     * Multiple requests made before the next frame are coalesced. Requests made while a frame is
     * being produced retain at most one additional frame. This method may be called from any
     * thread.
     */
    fun requestRender() {
        check(renderMode == GraphiteRenderMode.OnDemand) {
            "requestRender() requires GraphiteRenderMode.OnDemand"
        }
        renderRequests.trySend(Unit)
    }

    /**
     * Invokes the frame producer once with the current monotonic time in
     * [GraphiteRenderMode.Manual].
     *
     * Returns `false` without invoking the callback when no presentation target is attached or the
     * runtime is unavailable.
     */
    suspend fun render(): Boolean = render(platformMonotonicNanos())

    /**
     * Invokes the frame producer once with [frameTimeNanos] in [GraphiteRenderMode.Manual].
     *
     * Returns `false` without invoking the callback when no presentation target is attached or the
     * runtime is unavailable. Concurrent calls are executed sequentially.
     */
    internal suspend fun render(frameTimeNanos: Long): Boolean {
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
        if (renderMode != expectedMode || !runtime.isReady) {
            return@withLock false
        }
        val presentation = runtime.presentation.value ?: return@withLock false
        if (expectedPresentation != null &&
            presentation.generation != expectedPresentation.generation
        ) {
            return@withLock false
        }
        runtime.renderFrame(frameTimeNanos, presentation)
        true
    }
}
