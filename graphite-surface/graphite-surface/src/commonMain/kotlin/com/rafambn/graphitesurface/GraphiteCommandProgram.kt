@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicBoolean

internal class GraphiteCommandProgram(
    internal val commands: ByteArray,
    internal val resources: List<GraphiteRetainedReference<GraphiteCommandProgram>>,
) : AutoCloseable {
    private val closed: AtomicBoolean = AtomicBoolean(false)

    internal fun validate(maximumDepth: Int = 64) {
        require(maximumDepth > 0) { "display-list nesting exceeds 64 levels" }
        GraphiteCommandBuffer.validate(commands, resources.size)
        resources.forEach { it.value.validate(maximumDepth - 1) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        resources.forEach(GraphiteRetainedReference<GraphiteCommandProgram>::close)
    }
}
