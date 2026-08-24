@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong

internal class GraphiteReferenceCounted<T : AutoCloseable>(internal val value: T) {
    internal val identity: Long = nextIdentity.addAndFetch(1)
    private val references: AtomicInt = AtomicInt(1)

    internal fun retain(): GraphiteRetainedReference<T> {
        while (true) {
            val current = references.load()
            if (current == 0) throw GraphiteEncodingException.ClosedResource("retained resource")
            check(current < Int.MAX_VALUE) { "retained resource reference count overflow" }
            if (references.compareAndSet(current, current + 1)) {
                return GraphiteRetainedReference(this)
            }
        }
    }

    internal fun release() {
        val remaining = references.addAndFetch(-1)
        check(remaining >= 0) { "retained resource reference count underflow" }
        if (remaining == 0) value.close()
    }

    private companion object {
        val nextIdentity: AtomicLong = AtomicLong(0)
    }
}
