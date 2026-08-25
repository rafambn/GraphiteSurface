package com.rafambn.graphitesurface

/** Best-effort synchronous metrics captured without pausing workers. */
data class GraphiteMetricsSnapshot(
    val capturedAtNanos: Long,
    val recorders: List<Recorder>,
    val acceptedFrames: Long,
    val replacedFrames: Long,
    val rejectedFrames: Long,
    val pendingFrames: Int,
    val archiveFailures: Long,
    val resources: Resources,
) {
    data class Recorder(
        val index: Int,
        val queueDepth: Int,
        val queueCapacity: Int,
        val submitted: Long,
        val completed: Long,
        val cancelled: Long,
        val failed: Long,
        val totalQueueWaitNanos: Long,
        val maximumQueueWaitNanos: Long,
        val totalRecordingNanos: Long,
        val maximumRecordingNanos: Long,
    )

    data class Resources(
        val registered: Long,
        val registeredBytes: Long,
        val publications: Long,
        val publishedBytes: Long,
        val cacheHits: Long,
        val released: Long,
        val workerMessageBytes: Long,
        val totalPreparationNanos: Long,
        val maximumPreparationNanos: Long,
        val totalValidationNanos: Long,
        val maximumValidationNanos: Long,
    )
}
