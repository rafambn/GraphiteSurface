package com.rafambn.graphitesurface

/**
 * Immutable, backend-independent drawing commands reusable across engines.
 * Its command data is managed by the Kotlin garbage collector.
 */
class GraphiteDisplayList internal constructor(
    internal val program: GraphiteCommandProgram,
) {
    companion object {
        /**
         * Builds an engine-independent reusable command program on the caller thread.
         *
         * [maxCommandBufferBytes] applies only to this display list; runtime recording limits are
         * configured independently by [GraphiteEngine.maxCommandBufferBytes].
         */
        fun build(
            maxCommandBufferBytes: GraphiteCommandBufferLimit = GraphiteCommandBufferLimit.Default,
            block: GraphiteEncoder.() -> Unit,
        ): GraphiteDisplayList {
            val writer = GraphiteCommandWriter(maxCommandBufferBytes.bytes)
            GraphiteEncoderImpl(writer, cancellationProbe = {}).block()
            return GraphiteDisplayList(writer.finish())
        }
    }
}
