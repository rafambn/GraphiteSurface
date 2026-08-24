@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicBoolean

/** Immutable recorder result that may be inserted into more than one frame. */
public class GraphiteRecording internal constructor(
    public val target: GraphiteRecordingTarget,
    private val commands: ByteArray,
) : AutoCloseable {
    private val closed: AtomicBoolean = AtomicBoolean(false)

    public val isClosed: Boolean get() = closed.load()

    override fun close() {
        closed.store(true)
    }

    internal fun snapshotCommands(): ByteArray {
        if (closed.load()) throw GraphiteEncodingException.ClosedResource("recording")
        return commands
    }
}
