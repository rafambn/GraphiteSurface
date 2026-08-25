package com.rafambn.graphitesurface

/** Requested aggregate cache limits, reserved until backend cache control is wired. */
data class GraphiteGpuCacheConfig(
    val contextBytes: Long,
    val recorderBytes: Long,
) {
    init {
        require(contextBytes > 0) { "contextBytes must be positive" }
        require(recorderBytes > 0) { "recorderBytes must be positive" }
    }

    companion object {
        val Default: GraphiteGpuCacheConfig = GraphiteGpuCacheConfig(
            contextBytes = 128L * 1024L * 1024L,
            recorderBytes = 128L * 1024L * 1024L,
        )
    }
}
