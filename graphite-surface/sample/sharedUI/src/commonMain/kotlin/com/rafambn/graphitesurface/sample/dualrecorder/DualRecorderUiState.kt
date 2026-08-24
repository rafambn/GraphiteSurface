package com.rafambn.graphitesurface.sample.dualrecorder

internal data class DualRecorderUiState(
    internal val recorders: List<Recorder> = listOf(
        Recorder(index = 0, enabled = true),
        Recorder(index = 1, enabled = true),
    ),
) {
    internal data class Recorder(
        internal val index: Int,
        internal val enabled: Boolean,
        internal val queueDepth: Int = 0,
        internal val queueCapacity: Int = 0,
        internal val completed: Long = 0,
        internal val averageRecordingNanos: Long = 0,
        internal val maximumRecordingNanos: Long = 0,
    )
}
