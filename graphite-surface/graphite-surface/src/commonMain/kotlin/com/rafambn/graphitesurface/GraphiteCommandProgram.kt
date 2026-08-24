package com.rafambn.graphitesurface

internal class GraphiteCommandProgram(
    internal val commands: ByteArray,
    internal val resources: List<GraphiteCommandProgram>,
) {
    private val contentHashCode: Int = 31 * commands.contentHashCode() + resources.hashCode()

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is GraphiteCommandProgram &&
            contentHashCode == other.contentHashCode &&
            commands.contentEquals(other.commands) &&
            resources == other.resources

    override fun hashCode(): Int = contentHashCode

    internal fun validate(maximumDepth: Int = 64) {
        require(maximumDepth > 0) { "display-list nesting exceeds 64 levels" }
        GraphiteCommandBuffer.validate(commands, resources.size)
        resources.forEach { it.validate(maximumDepth - 1) }
    }
}
