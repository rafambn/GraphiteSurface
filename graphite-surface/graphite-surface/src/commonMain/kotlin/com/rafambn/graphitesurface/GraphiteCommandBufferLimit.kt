package com.rafambn.graphitesurface

import kotlin.jvm.JvmInline

/** Maximum encoded bytes accepted for one command program. */
@JvmInline
value class GraphiteCommandBufferLimit(val bytes: Int) {
    init {
        require(bytes > 0) { "command-buffer limit must be positive" }
    }

    companion object {
        val Default: GraphiteCommandBufferLimit = GraphiteCommandBufferLimit(4 * 1024 * 1024)
    }
}
