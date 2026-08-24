@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

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
public class GraphiteRuntime private constructor(public val config: GraphiteRuntimeConfig) : AutoCloseable {
    internal val token: Any = Any()

    private val closing: AtomicBoolean = AtomicBoolean(false)
    private val attachmentIds: AtomicLong = AtomicLong(0)
    private val generations: AtomicLong = AtomicLong(0)
    private val attachment: AtomicReference<GraphitePresentationAttachment?> = AtomicReference(null)
    private val pendingFrame: AtomicReference<GraphiteFrameSnapshot?> = AtomicReference(null)
    private val acceptedFrames: AtomicLong = AtomicLong(0)
    private val replacedFrames: AtomicLong = AtomicLong(0)
    private val rejectedFrames: AtomicLong = AtomicLong(0)
    private val archiveFailures: AtomicLong = AtomicLong(0)
    private val resources: GraphiteResourceRegistry = GraphiteResourceRegistry()
    private val closed: CompletableDeferred<Unit> = CompletableDeferred()
    internal val shutdownRequested: CompletableDeferred<Unit> = CompletableDeferred()
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState: MutableStateFlow<GraphiteRuntimeState> =
        MutableStateFlow(GraphiteRuntimeState.Ready)
    private val mutablePresentation: MutableStateFlow<GraphitePresentationState> =
        MutableStateFlow(GraphitePresentationState.Detached)
    private val mutableEvents: MutableSharedFlow<GraphiteEvent> = MutableSharedFlow(
        extraBufferCapacity = EVENT_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    public val state: StateFlow<GraphiteRuntimeState> = mutableState.asStateFlow()
    public val presentation: StateFlow<GraphitePresentationState> = mutablePresentation.asStateFlow()
    public val events: SharedFlow<GraphiteEvent> = mutableEvents.asSharedFlow()
    public val recorders: List<GraphiteRecorder> = createRecorders()
    private val logger: GraphiteRuntimeLogger = GraphiteRuntimeLogger(config.archivist) { error ->
        archiveFailures.addAndFetch(1)
        mutableEvents.tryEmit(GraphiteEvent.ArchiveFailure(error))
    }

    public fun createRecordingTarget(pixelSize: GraphiteSize): GraphiteRecordingTarget {
        requireReady()
        require(pixelSize.width > 0 && pixelSize.height > 0) {
            "recording target dimensions must be positive"
        }
        return GraphiteRecordingTarget(pixelSize, token)
    }

    public fun createFrame(
        presentation: GraphitePresentationInfo,
        clearColor: GraphiteColor = GraphiteColor.Transparent,
        block: GraphiteFrameBuilder.() -> Unit = {},
    ): GraphiteFrame {
        requireReady()
        if (presentation.runtimeToken !== token) {
            throw GraphitePresentationException("presentation belongs to a different runtime")
        }
        val builder = GraphiteFrameBuilder(token)
        return try {
            builder.apply(block)
            GraphiteFrame(presentation, clearColor, builder.build())
        } catch (error: Throwable) {
            builder.close()
            throw error
        }
    }

    public fun present(frame: GraphiteFrame): GraphitePresentResult {
        if (mutableState.value != GraphiteRuntimeState.Ready) {
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

        val previous = pendingFrame.exchange(frame.snapshot())
        previous?.close()
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
    public suspend fun awaitIdle() {
        recorders.forEach { it.awaitIdle() }
    }

    public fun metricsSnapshot(): GraphiteMetricsSnapshot = GraphiteMetricsSnapshot(
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
        mutableState.value = GraphiteRuntimeState.Closing
        pendingFrame.exchange(null)?.close()
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
                mutableState.value = GraphiteRuntimeState.Closed
                closed.complete(Unit)
            } catch (error: Throwable) {
                val failure = GraphiteFailure(
                    kind = GraphiteFailure.Kind.WorkerTerminated,
                    stage = GraphiteFailure.Stage.Shutdown,
                    message = "worker shutdown failed",
                    cause = error,
                )
                mutableState.value = GraphiteRuntimeState.Failed(failure)
                mutableEvents.tryEmit(GraphiteEvent.FatalFailure(failure))
                closed.complete(Unit)
            } finally {
                scope.cancel()
            }
        }
    }

    public suspend fun awaitClosed() {
        closed.await()
    }

    internal fun requireReady() {
        val current = mutableState.value
        if (current != GraphiteRuntimeState.Ready) {
            if (current == GraphiteRuntimeState.Closing || current == GraphiteRuntimeState.Closed) {
                throw GraphiteRuntimeClosedException()
            }
            throw GraphiteRuntimeUnavailableException(current)
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
        mutableState.value = GraphiteRuntimeState.Failed(failure)
        mutableEvents.tryEmit(GraphiteEvent.FatalFailure(failure))
        logger.emit(
            operation = operation,
            outcome = "failed",
            fields = fields + mapOf(
                "failure_kind" to failure.kind,
                "error" to (failure.cause?.message ?: failure.cause ?: failure.message),
            ),
        )
        pendingFrame.exchange(null)?.close()
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
                pendingFrame.exchange(null)?.close()
                mutablePresentation.value = GraphitePresentationState.Attached(info)
                return info
            }
        }
    }

    internal fun detachPresentation(attachmentId: Long) {
        val current = attachment.load() ?: return
        if (current.id != attachmentId) return
        if (attachment.compareAndSet(current, null)) {
            pendingFrame.exchange(null)?.close()
            mutablePresentation.value = GraphitePresentationState.Detached
        }
    }

    internal fun takePendingFrame(attachmentId: Long): GraphiteFrameSnapshot? {
        val current = attachment.load() ?: return null
        if (current.id != attachmentId || current.info == null) return null
        val frame = pendingFrame.exchange(null) ?: return null
        if (frame.presentationGeneration == current.info.generation) return frame
        frame.close()
        return null
    }

    private fun detachPresentationTarget(attachmentId: Long) {
        while (true) {
            val current = attachment.load() ?: return
            if (current.id != attachmentId) return
            val updated = GraphitePresentationAttachment(current.id, current.requestFrame, info = null)
            if (attachment.compareAndSet(current, updated)) {
                pendingFrame.exchange(null)?.close()
                mutablePresentation.value = GraphitePresentationState.Detached
                return
            }
        }
    }

    private fun createRecorders(): List<GraphiteRecorder> {
        val createdWorkers = mutableListOf<PlatformRecorderWorker>()
        return try {
            List(config.recorderCount) { index ->
                val worker = PlatformRecorderWorker(index)
                createdWorkers += worker
                GraphiteRecorder(
                    index = index,
                    runtime = this,
                    worker = worker,
                    queueCapacity = config.recorderQueueCapacity,
                )
            }
        } catch (error: Throwable) {
            createdWorkers.forEach(PlatformRecorderWorker::close)
            throw error
        }
    }

    public companion object {
        private const val EVENT_CAPACITY: Int = 64

        public suspend fun create(
            config: GraphiteRuntimeConfig = GraphiteRuntimeConfig(),
        ): GraphiteRuntime = try {
            GraphiteRuntime(config).also { runtime ->
                runtime.logger.emit(
                    operation = "runtime_lifecycle",
                    outcome = "ready",
                    fields = mapOf(
                        "recorder_count" to config.recorderCount,
                        "recorder_queue_capacity" to config.recorderQueueCapacity,
                        "max_frames_in_flight" to config.maxFramesInFlight,
                    ),
                )
            }
        } catch (error: Throwable) {
            throw GraphiteInitializationException(GraphiteFailure.Stage.Initialization, error)
        }
    }
}
