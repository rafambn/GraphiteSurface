package com.rafambn.graphitesurface

/** Requested aggregate cache limits, reserved until backend cache control is wired. */
public data class GraphiteGpuCacheConfig(
    public val contextBytes: Long,
    public val recorderBytes: Long,
) {
    init {
        require(contextBytes > 0) { "contextBytes must be positive" }
        require(recorderBytes > 0) { "recorderBytes must be positive" }
    }

    public companion object {
        public val Default: GraphiteGpuCacheConfig = GraphiteGpuCacheConfig(
            contextBytes = 128L * 1024L * 1024L,
            recorderBytes = 128L * 1024L * 1024L,
        )
    }
}
