package com.rafambn.graphitesurface

/**
 * Immutable, backend-independent drawing commands reusable across engines.
 * Its command data is managed by the Kotlin garbage collector.
 */
class GraphiteDisplayList internal constructor(
    internal val program: GraphiteCommandProgram,
)

/** Builds an engine-independent reusable command program on the caller thread. */
fun graphiteDisplayList(block: GraphiteEncoder.() -> Unit): GraphiteDisplayList =
    graphiteDisplayList(GraphiteCommandBufferLimit.Default, block)

internal fun graphiteDisplayList(
    maxCommandBufferBytes: GraphiteCommandBufferLimit,
    block: GraphiteEncoder.() -> Unit,
): GraphiteDisplayList {
    val writer = GraphiteCommandWriter(maxCommandBufferBytes.bytes)
    GraphiteEncoderImpl(writer, cancellationProbe = {}).block()
    return GraphiteDisplayList(writer.finish())
}
