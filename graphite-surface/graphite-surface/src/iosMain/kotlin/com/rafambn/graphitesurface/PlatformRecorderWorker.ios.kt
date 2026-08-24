@file:OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)

package com.rafambn.graphitesurface

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext

internal actual class PlatformRecorderWorker actual constructor(index: Int) {
    private val resources = GraphiteWorkerResourceCache()
    private val dispatcher = newSingleThreadContext("GraphiteRecorder-$index")
    private val closed = CompletableDeferred<Unit>()

    internal actual suspend fun process(message: ByteArray): Unit = withContext(dispatcher + NonCancellable) {
        resources.process(message)
    }

    internal actual fun close() {
        dispatcher.close()
        closed.complete(Unit)
    }

    internal actual suspend fun awaitClosed() {
        closed.await()
    }
}
