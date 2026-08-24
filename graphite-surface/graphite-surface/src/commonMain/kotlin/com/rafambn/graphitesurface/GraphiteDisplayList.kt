@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Immutable, backend-independent drawing commands reusable across runtimes.
 *
 * Close the caller-owned handle when it is no longer needed. Recordings and parent display lists
 * retain their own references, so closing this handle does not invalidate already-retained work.
 */
public class GraphiteDisplayList internal constructor(
    program: GraphiteCommandProgram,
) : AutoCloseable {
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private val retainedProgram: GraphiteReferenceCounted<GraphiteCommandProgram> =
        GraphiteReferenceCounted(program)

    public val isClosed: Boolean get() = closed.load()

    override fun close() {
        if (closed.compareAndSet(false, true)) retainedProgram.release()
    }

    internal fun retainProgram(): GraphiteRetainedReference<GraphiteCommandProgram> {
        if (closed.load()) throw GraphiteEncodingException.ClosedResource("display list")
        return retainedProgram.retain()
    }

    public companion object {
        /**
         * Builds a runtime-independent reusable command program on the caller thread.
         *
         * [maxCommandBufferBytes] applies only to this display list; runtime recording limits are
         * configured independently by [GraphiteRuntimeConfig.maxCommandBufferBytes].
         */
        public fun build(
            maxCommandBufferBytes: GraphiteCommandBufferLimit = GraphiteCommandBufferLimit.Default,
            block: GraphiteEncoder.() -> Unit,
        ): GraphiteDisplayList {
            val writer = GraphiteCommandWriter(maxCommandBufferBytes.bytes)
            return try {
                GraphiteEncoderImpl(writer, cancellationProbe = {}).block()
                GraphiteDisplayList(writer.finish())
            } catch (error: Throwable) {
                writer.close()
                throw error
            }
        }
    }
}
