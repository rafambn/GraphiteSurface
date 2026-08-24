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
}
