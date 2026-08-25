@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** User-owned asynchronous Graphite engine and worker group. */
class GraphiteEngine(
    /** Number of stable recorder queues and their dedicated workers. */
    val recorderCount: Int = 1,
    /** Maximum number of calls waiting behind the active call of each recorder. */
    val recorderQueueCapacity: Int = 1,
) : AutoCloseable {
    init {
        require(recorderCount in 1..64) { "recorderCount must be in 1..64" }
        require(recorderQueueCapacity in 1..1024) { "recorderQueueCapacity must be in 1..1024" }
    }

    internal val token = Any()

    private val closing = AtomicBoolean(false)
    private val attachmentIds = AtomicLong(0)
    private val generations = AtomicLong(0)
    private val attachment = AtomicReference<GraphitePresentationAttachment?>(null)
    private val pendingFrame = AtomicReference<GraphiteFrame?>(null)
    private val acceptedFrames = AtomicLong(0)
    private val replacedFrames = AtomicLong(0)
    private val rejectedFrames = AtomicLong(0)
    private val resources = GraphiteResourceRegistry()
    private val closed = CompletableDeferred<Unit>()
    internal val shutdownRequested = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow<GraphiteEngineState>(GraphiteEngineState.Ready)
    private val mutablePresentation = MutableStateFlow<GraphitePresentationInfo?>(null)

    internal val presentation: StateFlow<GraphitePresentationInfo?> =
        mutablePresentation.asStateFlow()
    internal val isReady: Boolean
        get() = mutableState.value == GraphiteEngineState.Ready

    /** Lifecycle state and best-effort counters kept outside the rendering API. */
    val diagnostics = GraphiteDiagnostics(mutableState.asStateFlow(), ::metricsSnapshot)
    /** Stable recorder queues owned by this engine. */
    val recorders: List<GraphiteRecorder> = try {
        createRecorders()
    } catch (error: Throwable) {
        resources.close()
        scope.cancel()
        throw GraphiteInitializationException(error)
    }

    /** Builds a frame and places it in the latest-wins presentation mailbox. */
    fun present(
        presentation: GraphitePresentationInfo,
        clearColor: Color = Color.Transparent,
        block: GraphiteFrameBuilder.() -> Unit = {},
    ): GraphitePresentResult {
        if (!isReady) {
            rejectedFrames.addAndFetch(1)
            return GraphitePresentResult.RuntimeUnavailable
        }
        val currentAttachment = attachment.load()
        val currentInfo = currentAttachment?.info
        if (currentInfo == null) {
            rejectedFrames.addAndFetch(1)
            return GraphitePresentResult.NoPresentation
        }
        if (presentation.runtimeToken !== token || presentation.generation != currentInfo.generation) {
            rejectedFrames.addAndFetch(1)
            return GraphitePresentResult.StalePresentation
        }

        val frame = GraphiteFrame(
            presentation = presentation,
            clearColor = clearColor,
            insertions = GraphiteFrameBuilder(token).apply(block).build(),
        )
        val previous = pendingFrame.exchange(frame)
        val result = if (previous == null) {
            acceptedFrames.addAndFetch(1)
            GraphitePresentResult.Accepted
        } else {
            replacedFrames.addAndFetch(1)
            GraphitePresentResult.ReplacedPending
        }
        currentAttachment.requestFrame()
        return result
    }

    /** Waits until every admitted recorder job has finished. It does not wait for GPU completion. */
    suspend fun awaitRecordersIdle() {
        recorders.forEach { it.awaitIdle() }
    }

    internal suspend fun prepareRecording(
        program: GraphiteCommandProgram,
        workerIndex: Int,
    ): ByteArray = resources.prepare(program, workerIndex)

    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        shutdownRequested.complete(Unit)
        mutableState.value = GraphiteEngineState.Closing
        pendingFrame.store(null)
        attachment.store(null)
        mutablePresentation.value = null
        recorders.forEach(GraphiteRecorder::close)
        scope.launch {
            try {
                recorders.forEach { it.awaitClosed() }
                resources.close()
                mutableState.value = GraphiteEngineState.Closed
                closed.complete(Unit)
            } catch (error: Throwable) {
                mutableState.value = GraphiteEngineState.Failed(error)
                closed.complete(Unit)
            } finally {
                scope.cancel()
            }
        }
    }

    suspend fun awaitClosed() {
        closed.await()
    }

    internal fun requireReady() {
        val current = mutableState.value
        if (current != GraphiteEngineState.Ready) {
            if (current == GraphiteEngineState.Closing || current == GraphiteEngineState.Closed) {
                throw GraphiteEngineClosedException()
            }
            throw GraphiteEngineUnavailableException(current)
        }
    }

    internal fun failFromRenderWorker(error: Throwable) {
        failRuntime(error)
    }

    internal fun failFromRecorderWorker(error: Throwable) {
        failRuntime(error)
    }

    private fun failRuntime(error: Throwable) {
        if (!closing.compareAndSet(false, true)) return
        shutdownRequested.complete(Unit)
        mutableState.value = GraphiteEngineState.Failed(error)
        pendingFrame.store(null)
        recorders.forEach(GraphiteRecorder::close)
        scope.launch {
            try {
                recorders.forEach { it.awaitClosed() }
            } finally {
                resources.close()
                closed.complete(Unit)
                scope.cancel()
            }
        }
    }

    internal fun attachPresentation(requestFrame: () -> Unit): Long {
        requireReady()
        val id = attachmentIds.addAndFetch(1)
        val candidate = GraphitePresentationAttachment(id, requestFrame, info = null)
        check(attachment.compareAndSet(null, candidate)) {
            "GraphiteEngine already has an attached surface"
        }
        return id
    }

    internal fun updatePresentation(
        attachmentId: Long,
        pixelSize: IntSize,
        density: Float,
    ): GraphitePresentationInfo? {
        if (pixelSize == IntSize.Zero) {
            detachPresentationTarget(attachmentId)
            return null
        }
        while (true) {
            val current = attachment.load() ?: return null
            if (current.id != attachmentId) return null
            val existing = current.info
            if (existing != null && existing.pixelSize == pixelSize && existing.density == density) {
                return existing
            }
            val info = GraphitePresentationInfo(
                pixelSize = pixelSize,
                density = density,
                generation = generations.addAndFetch(1),
                runtimeToken = token,
            )
            val updated = GraphitePresentationAttachment(current.id, current.requestFrame, info)
            if (attachment.compareAndSet(current, updated)) {
                pendingFrame.store(null)
                mutablePresentation.value = info
                return info
            }
        }
    }

    internal fun detachPresentation(attachmentId: Long) {
        val current = attachment.load() ?: return
        if (current.id != attachmentId) return
        if (attachment.compareAndSet(current, null)) {
            pendingFrame.store(null)
            mutablePresentation.value = null
        }
    }

    internal fun takePendingFrame(attachmentId: Long): GraphiteFrame? {
        val current = attachment.load() ?: return null
        if (current.id != attachmentId || current.info == null) return null
        val frame = pendingFrame.exchange(null) ?: return null
        if (frame.presentation.generation == current.info.generation) return frame
        return null
    }

    internal fun hasPendingFrame(attachmentId: Long): Boolean {
        val current = attachment.load() ?: return false
        return current.id == attachmentId && current.info != null && pendingFrame.load() != null
    }

    private fun detachPresentationTarget(attachmentId: Long) {
        while (true) {
            val current = attachment.load() ?: return
            if (current.id != attachmentId) return
            val updated = GraphitePresentationAttachment(current.id, current.requestFrame, info = null)
            if (attachment.compareAndSet(current, updated)) {
                pendingFrame.store(null)
                mutablePresentation.value = null
                return
            }
        }
    }

    private fun createRecorders(): List<GraphiteRecorder> {
        val createdWorkers = mutableListOf<PlatformRecorderWorker>()
        return try {
            List(recorderCount) { index ->
                val worker = PlatformRecorderWorker(index)
                createdWorkers += worker
                GraphiteRecorder(
                    index = index,
                    runtime = this,
                    worker = worker,
                    queueCapacity = recorderQueueCapacity,
                )
            }
        } catch (error: Throwable) {
            createdWorkers.forEach(PlatformRecorderWorker::close)
            throw error
        }
    }

    private fun metricsSnapshot(): GraphiteMetricsSnapshot = GraphiteMetricsSnapshot(
        capturedAtNanos = platformMonotonicNanos(),
        recorders = recorders.map(GraphiteRecorder::metricsSnapshot),
        acceptedFrames = acceptedFrames.load(),
        replacedFrames = replacedFrames.load(),
        rejectedFrames = rejectedFrames.load(),
        pendingFrames = if (pendingFrame.load() == null) 0 else 1,
        resources = resources.snapshot(),
    )
}
