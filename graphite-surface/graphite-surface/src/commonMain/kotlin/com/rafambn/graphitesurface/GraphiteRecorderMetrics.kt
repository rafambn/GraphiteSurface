@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong

internal class GraphiteRecorderMetrics(private val capacity: Int) {
    private val depth: AtomicInt = AtomicInt(0)
    private val submitted: AtomicLong = AtomicLong(0)
    private val completed: AtomicLong = AtomicLong(0)
    private val cancelled: AtomicLong = AtomicLong(0)
    private val failed: AtomicLong = AtomicLong(0)
    private val totalQueueWaitNanos: AtomicLong = AtomicLong(0)
    private val maximumQueueWaitNanos: AtomicLong = AtomicLong(0)
    private val totalRecordingNanos: AtomicLong = AtomicLong(0)
    private val maximumRecordingNanos: AtomicLong = AtomicLong(0)

    internal fun admitted(queueWaitNanos: Long) {
        depth.addAndFetch(1)
        submitted.addAndFetch(1)
        totalQueueWaitNanos.addAndFetch(queueWaitNanos)
        maximumQueueWaitNanos.updateMaximum(queueWaitNanos)
    }

    internal fun succeeded(recordingNanos: Long) {
        completed.addAndFetch(1)
        recordDuration(recordingNanos)
        depth.addAndFetch(-1)
    }

    internal fun cancelled(recordingNanos: Long) {
        cancelled.addAndFetch(1)
        recordDuration(recordingNanos)
        depth.addAndFetch(-1)
    }

    internal fun failed(recordingNanos: Long) {
        failed.addAndFetch(1)
        recordDuration(recordingNanos)
        depth.addAndFetch(-1)
    }

    internal fun snapshot(index: Int): GraphiteMetricsSnapshot.Recorder = GraphiteMetricsSnapshot.Recorder(
        index = index,
        queueDepth = depth.load(),
        queueCapacity = capacity,
        submitted = submitted.load(),
        completed = completed.load(),
        cancelled = cancelled.load(),
        failed = failed.load(),
        totalQueueWaitNanos = totalQueueWaitNanos.load(),
        maximumQueueWaitNanos = maximumQueueWaitNanos.load(),
        totalRecordingNanos = totalRecordingNanos.load(),
        maximumRecordingNanos = maximumRecordingNanos.load(),
    )

    private fun recordDuration(recordingNanos: Long) {
        totalRecordingNanos.addAndFetch(recordingNanos)
        maximumRecordingNanos.updateMaximum(recordingNanos)
    }
}

private fun AtomicLong.updateMaximum(candidate: Long) {
    while (true) {
        val current = load()
        if (candidate <= current || compareAndSet(current, candidate)) return
    }
}
