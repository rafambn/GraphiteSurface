@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicInt

internal class GraphiteReferenceCounted<T>(
    internal val value: T,
    private val releaseValue: (T) -> Unit = {},
) {
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
        if (remaining == 0) releaseValue(value)
    }
}
