@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicBoolean

internal class GraphiteRetainedReference<T : AutoCloseable>(
    private val owner: GraphiteReferenceCounted<T>,
) : AutoCloseable {
    private val closed: AtomicBoolean = AtomicBoolean(false)

    internal val identity: Long get() = owner.identity
    internal val value: T get() = owner.value

    internal fun retain(): GraphiteRetainedReference<T> = owner.retain()

    override fun close() {
        if (closed.compareAndSet(false, true)) owner.release()
    }
}
