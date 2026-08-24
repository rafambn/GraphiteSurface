@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicBoolean

/** Immutable, backend-independent drawing commands reusable across runtimes. */
public class GraphiteDisplayList internal constructor(private val commands: ByteArray) : AutoCloseable {
    private val closed: AtomicBoolean = AtomicBoolean(false)

    public val isClosed: Boolean get() = closed.load()

    override fun close() {
        closed.store(true)
    }

    internal fun commandBytes(): ByteArray {
        if (closed.load()) throw GraphiteEncodingException.ClosedResource("display list")
        return commands
    }
}
