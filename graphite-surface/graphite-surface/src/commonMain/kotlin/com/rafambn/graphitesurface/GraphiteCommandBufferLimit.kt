package com.rafambn.graphitesurface

import kotlin.jvm.JvmInline

@JvmInline
internal value class GraphiteCommandBufferLimit(internal val bytes: Int) {
    init {
        require(bytes > 0) { "command-buffer limit must be positive" }
    }

    companion object {
        internal val Default = GraphiteCommandBufferLimit(4 * 1024 * 1024)
    }
}
