@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicBoolean

/** Immutable ordered content for one presentation generation. */
public class GraphiteFrame internal constructor(
    public val presentation: GraphitePresentationInfo,
    public val clearColor: GraphiteColor,
    internal val insertions: List<GraphiteFrameInsertion>,
) : AutoCloseable {
    private val closed: AtomicBoolean = AtomicBoolean(false)

    public val isClosed: Boolean get() = closed.load()

    override fun close() {
        closed.store(true)
    }

    internal fun snapshot(): GraphiteFrameSnapshot {
        if (closed.load()) throw GraphiteEncodingException.ClosedResource("frame")
        return GraphiteFrameSnapshot(
            presentationGeneration = presentation.generation,
            clearColor = clearColor,
            insertions = insertions,
        )
    }
}
