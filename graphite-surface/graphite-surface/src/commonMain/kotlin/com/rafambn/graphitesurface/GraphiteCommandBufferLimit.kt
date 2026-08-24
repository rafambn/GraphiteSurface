package com.rafambn.graphitesurface

import kotlin.jvm.JvmInline

/** Maximum encoded bytes accepted for one recording or display list. */
@JvmInline
public value class GraphiteCommandBufferLimit(public val bytes: Int) {
    init {
        require(bytes > 0) { "command-buffer limit must be positive" }
    }

    public companion object {
        public val Default: GraphiteCommandBufferLimit = GraphiteCommandBufferLimit(4 * 1024 * 1024)
    }
}
