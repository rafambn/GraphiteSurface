package com.rafambn.graphitesurface

internal class GraphiteWorkerResourceCache {
    private val resourceCounts: MutableMap<Long, Int> = mutableMapOf()

    internal fun process(message: ByteArray): ByteArray {
        val reader = GraphiteCommandReader(message)
        if (reader.readInt() != GraphiteWorkerMessage.Magic) error("invalid worker-message magic")
        if (reader.readInt() != GraphiteWorkerMessage.Version) error("unsupported worker-message version")
        val publicationCount = reader.readCount("resource publication")
        repeat(publicationCount) {
            val id = reader.readPositiveId()
            if (resourceCounts.containsKey(id)) error("resource ID was published more than once")
            val resourceCount = reader.readCount("resource reference")
            repeat(resourceCount) {
                val dependency = reader.readPositiveId()
                if (!resourceCounts.containsKey(dependency)) error("resource dependency is not published")
            }
            val commandSize = reader.readCount("resource command byte")
            GraphiteCommandBuffer.validate(reader.readBytes(commandSize), resourceCount)
            resourceCounts[id] = resourceCount
        }
        val rootResourceCount = reader.readCount("root resource reference")
        repeat(rootResourceCount) {
            val id = reader.readPositiveId()
            if (!resourceCounts.containsKey(id)) error("root resource is not published")
        }
        val rootCommandSize = reader.readCount("root command byte")
        GraphiteCommandBuffer.validate(reader.readBytes(rootCommandSize), rootResourceCount)
        reader.requireFinished()
        return message
    }

    private fun GraphiteCommandReader.readCount(label: String): Int = readInt().also { count ->
        if (count < 0) error("negative $label count")
    }

    private fun GraphiteCommandReader.readPositiveId(): Long = readLong().also { id ->
        if (id <= 0) error("resource ID must be positive")
    }
}
