@file:OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)

package com.rafambn.graphitesurface

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext

internal actual class PlatformRecorderWorker actual constructor(index: Int) {
    private val dispatcher = newSingleThreadContext("GraphiteRecorder-$index")
    private val closed = CompletableDeferred<Unit>()

    internal actual suspend fun process(commands: ByteArray): ByteArray = withContext(dispatcher) {
        GraphiteCommandBuffer.validate(commands)
        commands.copyOf()
    }

    internal actual fun close() {
        dispatcher.close()
        closed.complete(Unit)
    }

    internal actual suspend fun awaitClosed() {
        closed.await()
    }
}
