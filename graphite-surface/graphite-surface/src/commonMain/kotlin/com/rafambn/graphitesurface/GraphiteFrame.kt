@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Immutable ordered content for one presentation generation.
 *
 * Close the caller-owned handle after [GraphiteRuntime.present]. The pending or in-flight
 * presentation retains its own snapshot.
 */
public class GraphiteFrame internal constructor(
    public val presentation: GraphitePresentationInfo,
    public val clearColor: GraphiteColor,
    insertions: List<GraphiteFrameInsertion>,
) : AutoCloseable {
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private val retainedContent: GraphiteReferenceCounted<GraphiteFrameContent> =
        GraphiteReferenceCounted(GraphiteFrameContent(insertions))

    public val isClosed: Boolean get() = closed.load()

    override fun close() {
        if (closed.compareAndSet(false, true)) retainedContent.release()
    }

    internal fun snapshot(): GraphiteFrameSnapshot {
        if (closed.load()) throw GraphiteEncodingException.ClosedResource("frame")
        return GraphiteFrameSnapshot(
            presentationGeneration = presentation.generation,
            clearColor = clearColor,
            content = retainedContent.retain(),
        )
    }
}
