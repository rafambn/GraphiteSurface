@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import com.rafambn.scribe.Archivist
import androidx.compose.ui.graphics.Color
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** User-owned asynchronous Graphite engine and worker group. */
class GraphiteEngine(
    /** Number of stable recorder queues and their dedicated workers. */
    val recorderCount: Int = 1,
    /** Maximum number of calls waiting behind the active call of each recorder. */
    val recorderQueueCapacity: Int = 1,
    /** Submission upper bound. Current backends conservatively keep at most one frame in flight. */
    val maxFramesInFlight: Int = 2,
    /** Reserved cache policy. Current backends validate but do not enforce these limits. */
    val gpuCache: GraphiteGpuCacheConfig = GraphiteGpuCacheConfig.Default,
    /** Maximum encoded bytes in one recording command program. */
    val maxCommandBufferBytes: GraphiteCommandBufferLimit = GraphiteCommandBufferLimit.Default,
    /** Optional Scribe destination owned and retired by this runtime. */
    val archivist: Archivist? = null,
) : AutoCloseable {
    init {
        require(recorderCount in 1..64) { "recorderCount must be in 1..64" }
        require(recorderQueueCapacity in 1..1024) { "recorderQueueCapacity must be in 1..1024" }
        require(maxFramesInFlight in 1..8) { "maxFramesInFlight must be in 1..8" }
    }

    internal val token = Any()

    private val closing = AtomicBoolean(false)
    private val attachmentIds = AtomicLong(0)
    private val generations = AtomicLong(0)
    private val attachment: AtomicReference<GraphitePresentationAttachment?> = AtomicReference(null)
    private val pendingFrame: AtomicReference<GraphiteFrame?> = AtomicReference(null)
    private val acceptedFrames = AtomicLong(0)
    private val replacedFrames = AtomicLong(0)
    private val rejectedFrames = AtomicLong(0)
    private val archiveFailures = AtomicLong(0)
    private val resources = GraphiteResourceRegistry()
    private val closed = CompletableDeferred<Unit>()
    internal val shutdownRequested = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState: MutableStateFlow<GraphiteEngineState> =
        MutableStateFlow(GraphiteEngineState.Ready)
    private val mutablePresentation: MutableStateFlow<GraphitePresentationState> =
        MutableStateFlow(GraphitePresentationState.Detached)
    private val mutableEvents: MutableSharedFlow<GraphiteEvent> = MutableSharedFlow(
        extraBufferCapacity = EVENT_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val state: StateFlow<GraphiteEngineState> = mutableState.asStateFlow()
    val presentation: StateFlow<GraphitePresentationState> = mutablePresentation.asStateFlow()
    val events: SharedFlow<GraphiteEvent> = mutableEvents.asSharedFlow()
    val recorders: List<GraphiteRecorder> = try {
        createRecorders()
    } catch (error: Throwable) {
        resources.close()
        scope.cancel()
        throw GraphiteInitializationException(GraphiteFailure.Stage.Initialization, error)
    }
    private val logger = try {
        GraphiteEngineLogger(archivist) { error ->
            archiveFailures.addAndFetch(1)
            mutableEvents.tryEmit(GraphiteEvent.ArchiveFailure(error))
        }
    } catch (error: Throwable) {
        recorders.forEach(GraphiteRecorder::close)
        resources.close()
        scope.cancel()
        throw GraphiteInitializationException(GraphiteFailure.Stage.Initialization, error)
    }

    init {
        logger.emit(
            operation = "runtime_lifecycle",
            outcome = "ready",
            fields = mapOf(
                "recorder_count" to recorderCount,
                "recorder_queue_capacity" to recorderQueueCapacity,
                "max_frames_in_flight" to maxFramesInFlight,
            ),
        )
    }

    fun createFrame(
        presentation: GraphitePresentationInfo,
        clearColor: Color = Color.Transparent,
        block: GraphiteFrameBuilder.() -> Unit = {},
    ): GraphiteFrame {
        requireReady()
        if (presentation.runtimeToken !== token) {
            throw GraphitePresentationException("presentation belongs to a different runtime")
        }
        val insertions = GraphiteFrameBuilder(token).apply(block).build()
        return GraphiteFrame(presentation, clearColor, insertions)
    }

    fun present(frame: GraphiteFrame): GraphitePresentResult {
        if (mutableState.value != GraphiteEngineState.Ready) {
            rejectedFrames.addAndFetch(1)
            return GraphitePresentResult.RuntimeUnavailable
        }
        val currentAttachment = attachment.load()
        val currentInfo = currentAttachment?.info
        if (currentInfo == null) {
            rejectedFrames.addAndFetch(1)
            return GraphitePresentResult.NoPresentation
        }
        if (frame.presentation.runtimeToken !== token ||
            frame.presentation.generation != currentInfo.generation
        ) {
            rejectedFrames.addAndFetch(1)
            return GraphitePresentResult.StalePresentation
        }

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
    suspend fun awaitIdle() {
        recorders.forEach { it.awaitIdle() }
    }

    fun metricsSnapshot(): GraphiteMetricsSnapshot = GraphiteMetricsSnapshot(
        capturedAtNanos = platformMonotonicNanos(),
        recorders = recorders.map(GraphiteRecorder::metricsSnapshot),
        acceptedFrames = acceptedFrames.load(),
        replacedFrames = replacedFrames.load(),
        rejectedFrames = rejectedFrames.load(),
        pendingFrames = if (pendingFrame.load() == null) 0 else 1,
        archiveFailures = archiveFailures.load(),
        resources = resources.snapshot(),
    )

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
        mutablePresentation.value = GraphitePresentationState.Detached
        logger.emit("runtime_lifecycle", "closing")
        recorders.forEach(GraphiteRecorder::close)
        scope.launch {
            try {
                recorders.forEach { it.awaitClosed() }
                resources.close()
                logger.emit("runtime_lifecycle", "closed")
                logger.retire()
                mutableState.value = GraphiteEngineState.Closed
                closed.complete(Unit)
            } catch (error: Throwable) {
                val failure = GraphiteFailure(
                    kind = GraphiteFailure.Kind.WorkerTerminated,
                    stage = GraphiteFailure.Stage.Shutdown,
                    message = "worker shutdown failed",
                    cause = error,
                )
                mutableState.value = GraphiteEngineState.Failed(failure)
                mutableEvents.tryEmit(GraphiteEvent.FatalFailure(failure))
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

    internal fun recordingFailed(index: Int, error: Throwable) {
        mutableEvents.tryEmit(GraphiteEvent.RecordingFailed(index, error))
        logger.emit(
            operation = "recording",
            outcome = "failed",
            fields = mapOf("recorder_index" to index, "error" to (error.message ?: error)),
        )
    }

    internal fun failFromRenderWorker(error: Throwable) {
        failRuntime(
            GraphiteFailure(
                kind = GraphiteFailure.Kind.BackendFailure,
                stage = GraphiteFailure.Stage.Presentation,
                message = "render worker failed while executing a frame",
                cause = error,
            ),
            operation = "render_frame",
        )
    }

    internal fun failFromRecorderWorker(index: Int, error: Throwable) {
        failRuntime(
            GraphiteFailure(
                kind = GraphiteFailure.Kind.InternalInvariant,
                stage = GraphiteFailure.Stage.CommandValidation,
                message = "recorder worker $index rejected an internally encoded command buffer",
                cause = error,
            ),
            operation = "recorder_worker",
            fields = mapOf("recorder_index" to index),
        )
    }

    private fun failRuntime(
        failure: GraphiteFailure,
        operation: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        if (!closing.compareAndSet(false, true)) return
        shutdownRequested.complete(Unit)
        mutableState.value = GraphiteEngineState.Failed(failure)
        mutableEvents.tryEmit(GraphiteEvent.FatalFailure(failure))
        logger.emit(
            operation = operation,
            outcome = "failed",
            fields = fields + mapOf(
                "failure_kind" to failure.kind,
                "error" to (failure.cause?.message ?: failure.cause ?: failure.message),
            ),
        )
        pendingFrame.store(null)
        recorders.forEach(GraphiteRecorder::close)
        scope.launch {
            try {
                recorders.forEach { it.awaitClosed() }
            } finally {
                resources.close()
                logger.retire()
                closed.complete(Unit)
                scope.cancel()
            }
        }
    }

    internal fun attachPresentation(requestFrame: () -> Unit): Long? {
        requireReady()
        val id = attachmentIds.addAndFetch(1)
        val candidate = GraphitePresentationAttachment(id, requestFrame, info = null)
        if (!attachment.compareAndSet(null, candidate)) {
            mutableEvents.tryEmit(
                GraphiteEvent.PresentationAttachRejected("runtime already has an attached surface"),
            )
            logger.emit("presentation_attach", "rejected", mapOf("reason" to "already_attached"))
            return null
        }
        mutablePresentation.value = GraphitePresentationState.Attaching
        return id
    }

    internal fun updatePresentation(
        attachmentId: Long,
        pixelSize: GraphiteSize,
        density: Float,
    ): GraphitePresentationInfo? {
        if (pixelSize == GraphiteSize.Zero) {
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
                mutablePresentation.value = GraphitePresentationState.Attached(info)
                return info
            }
        }
    }

    internal fun detachPresentation(attachmentId: Long) {
        val current = attachment.load() ?: return
        if (current.id != attachmentId) return
        if (attachment.compareAndSet(current, null)) {
            pendingFrame.store(null)
            mutablePresentation.value = GraphitePresentationState.Detached
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
                mutablePresentation.value = GraphitePresentationState.Detached
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

    private companion object {
        private const val EVENT_CAPACITY: Int = 64
    }
}
