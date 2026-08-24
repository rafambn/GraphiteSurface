package com.rafambn.graphitesurface

/** Best-effort synchronous metrics captured without pausing workers. */
public data class GraphiteMetricsSnapshot(
    public val capturedAtNanos: Long,
    public val recorders: List<Recorder>,
    public val acceptedFrames: Long,
    public val replacedFrames: Long,
    public val rejectedFrames: Long,
    public val pendingFrames: Int,
    public val archiveFailures: Long,
    public val resources: Resources,
) {
    public data class Recorder(
        public val index: Int,
        public val queueDepth: Int,
        public val queueCapacity: Int,
        public val submitted: Long,
        public val completed: Long,
        public val cancelled: Long,
        public val failed: Long,
        public val totalQueueWaitNanos: Long,
        public val maximumQueueWaitNanos: Long,
        public val totalRecordingNanos: Long,
        public val maximumRecordingNanos: Long,
    )

    public data class Resources(
        public val registered: Long,
        public val registeredBytes: Long,
        public val publications: Long,
        public val publishedBytes: Long,
        public val cacheHits: Long,
        public val released: Long,
        public val workerMessageBytes: Long,
        public val totalPreparationNanos: Long,
        public val maximumPreparationNanos: Long,
        public val totalValidationNanos: Long,
        public val maximumValidationNanos: Long,
    )
}
