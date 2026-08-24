package com.rafambn.graphitesurface

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stable handle for one asynchronous recording queue. */
public class GraphiteRecorder internal constructor(
    public val index: Int,
    private val runtime: GraphiteRuntime,
    private val worker: PlatformRecorderWorker,
    queueCapacity: Int,
) {
    private val admission: Channel<Unit> = Channel<Unit>(queueCapacity + 1).also { channel ->
        repeat(queueCapacity + 1) { check(channel.trySend(Unit).isSuccess) }
    }
    private val execution: Mutex = Mutex()
    private val metrics: GraphiteRecorderMetrics = GraphiteRecorderMetrics(queueCapacity)

    public suspend fun record(
        target: GraphiteRecordingTarget,
        block: GraphiteEncoder.() -> Unit,
    ): GraphiteRecording {
        runtime.requireReady()
        if (target.runtimeToken !== runtime.token) {
            throw GraphitePresentationException("recording target belongs to a different runtime")
        }

        val queuedAt = platformMonotonicNanos()
        select {
            admission.onReceive { }
            runtime.shutdownRequested.onAwait { throw GraphiteRuntimeClosedException() }
        }
        val admittedAt = platformMonotonicNanos()
        metrics.admitted((admittedAt - queuedAt).coerceAtLeast(0))
        var encoderBlockCompleted = false
        var submittedToWorker = false
        try {
            val validated = execution.withLock {
                runtime.requireReady()
                val job = currentCoroutineContext()[Job]
                val writer = GraphiteCommandWriter(runtime.config.maxCommandBufferBytes.bytes)
                GraphiteEncoderImpl(writer) { job?.ensureActive() }.block()
                encoderBlockCompleted = true
                val encoded = writer.finish()
                submittedToWorker = true
                worker.process(encoded)
            }
            metrics.succeeded(elapsedSince(admittedAt))
            return GraphiteRecording(target, validated)
        } catch (cancelled: CancellationException) {
            metrics.cancelled(elapsedSince(admittedAt))
            throw cancelled
        } catch (error: Throwable) {
            metrics.failed(elapsedSince(admittedAt))
            if (submittedToWorker) {
                runtime.failFromRecorderWorker(index, error)
            } else if (encoderBlockCompleted) {
                runtime.recordingFailed(index, error)
            }
            throw error
        } finally {
            check(admission.trySend(Unit).isSuccess) { "recorder admission accounting is corrupted" }
        }
    }

    internal suspend fun awaitIdle() {
        execution.withLock { }
    }

    internal fun close() {
        worker.close()
    }

    internal suspend fun awaitClosed() {
        worker.awaitClosed()
    }

    internal fun metricsSnapshot(): GraphiteMetricsSnapshot.Recorder = metrics.snapshot(index)

    private fun elapsedSince(startNanos: Long): Long =
        (platformMonotonicNanos() - startNanos).coerceAtLeast(0)
}
