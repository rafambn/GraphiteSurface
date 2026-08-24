package com.rafambn.graphitesurface

import com.rafambn.scribe.Archivist

/** Configuration fixed for the lifetime of one [GraphiteRuntime]. */
public class GraphiteRuntimeConfig(
    /** Number of stable recorder queues and their dedicated workers. */
    public val recorderCount: Int = 1,
    /** Maximum number of calls waiting behind the active call of each recorder. */
    public val recorderQueueCapacity: Int = 1,
    /** Submission upper bound. Current backends conservatively keep at most one frame in flight. */
    public val maxFramesInFlight: Int = 2,
    /** Reserved cache policy. The first runtime slice validates but does not enforce these limits. */
    public val gpuCache: GraphiteGpuCacheConfig = GraphiteGpuCacheConfig.Default,
    /** Maximum encoded bytes in one recording or display list. */
    public val maxCommandBufferBytes: GraphiteCommandBufferLimit = GraphiteCommandBufferLimit.Default,
    /** Optional Scribe destination owned and retired by this runtime. */
    public val archivist: Archivist? = null,
) {
    init {
        require(recorderCount in 1..64) { "recorderCount must be in 1..64" }
        require(recorderQueueCapacity in 1..1024) { "recorderQueueCapacity must be in 1..1024" }
        require(maxFramesInFlight in 1..8) { "maxFramesInFlight must be in 1..8" }
    }
}
