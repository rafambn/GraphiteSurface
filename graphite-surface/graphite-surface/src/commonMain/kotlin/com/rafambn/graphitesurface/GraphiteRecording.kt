@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Immutable recorder result that may be inserted into more than one frame.
 *
 * Close the caller-owned handle after inserting it. Every frame retains its own reference.
 */
public class GraphiteRecording internal constructor(
    internal val runtimeToken: Any,
    program: GraphiteCommandProgram,
) : AutoCloseable {
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private val retainedContent: GraphiteReferenceCounted<GraphiteRecordingContent> =
        GraphiteReferenceCounted(GraphiteRecordingContent(program))

    public val isClosed: Boolean get() = closed.load()

    override fun close() {
        if (closed.compareAndSet(false, true)) retainedContent.release()
    }

    internal fun retainContent(): GraphiteRetainedReference<GraphiteRecordingContent> {
        if (closed.load()) throw GraphiteEncodingException.ClosedResource("recording")
        return retainedContent.retain()
    }
}
