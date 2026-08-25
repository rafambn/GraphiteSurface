package com.rafambn.graphitesurface

import kotlinx.coroutines.flow.StateFlow

/** Optional runtime state and performance counters. */
class GraphiteDiagnostics internal constructor(
    /** Current lifecycle state. */
    val state: StateFlow<GraphiteEngineState>,
    private val snapshotProvider: () -> GraphiteMetricsSnapshot,
) {
    /** Captures best-effort counters without pausing workers. */
    fun snapshot(): GraphiteMetricsSnapshot = snapshotProvider()
}
