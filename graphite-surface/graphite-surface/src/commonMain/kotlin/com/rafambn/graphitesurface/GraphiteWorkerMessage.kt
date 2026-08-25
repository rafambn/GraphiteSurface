package com.rafambn.graphitesurface

internal object GraphiteWorkerMessage {
    internal const val Magic: Int = 0x47535731
    internal const val Version: Int = 1

    internal fun encode(
        root: GraphiteCommandProgram,
        rootResourceIds: LongArray,
        publications: List<GraphiteWorkerPublication>,
    ): ByteArray {
        val writer = Writer()
        writer.writeInt(Magic)
        writer.writeInt(Version)
        writer.writeInt(publications.size)
        publications.forEach { publication ->
            writer.writeLong(publication.id)
            writer.writeInt(publication.resourceIds.size)
            publication.resourceIds.forEach(writer::writeLong)
            writer.writeInt(publication.commands.size)
            writer.writeBytes(publication.commands)
        }
        writer.writeInt(rootResourceIds.size)
        rootResourceIds.forEach(writer::writeLong)
        writer.writeInt(root.commands.size)
        writer.writeBytes(root.commands)
        return writer.finish()
    }

    private class Writer {
        private var bytes = ByteArray(256)
        private var size = 0

        fun writeInt(value: Int) {
            ensureCapacity(Int.SIZE_BYTES)
            repeat(Int.SIZE_BYTES) { index ->
                bytes[size++] = (value ushr (index * 8)).toByte()
            }
        }

        fun writeLong(value: Long) {
            ensureCapacity(Long.SIZE_BYTES)
            repeat(Long.SIZE_BYTES) { index ->
                bytes[size++] = (value ushr (index * 8)).toByte()
            }
        }

        fun writeBytes(value: ByteArray) {
            ensureCapacity(value.size)
            value.copyInto(bytes, destinationOffset = size)
            size += value.size
        }

        fun finish(): ByteArray = bytes.copyOf(size)

        private fun ensureCapacity(additionalBytes: Int) {
            val required = size + additionalBytes
            if (required <= bytes.size) return
            var newSize = bytes.size * 2
            while (newSize < required) newSize *= 2
            bytes = bytes.copyOf(newSize)
        }
    }
}
