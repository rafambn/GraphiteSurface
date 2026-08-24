package com.rafambn.graphitesurface

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal actual class PlatformRecorderWorker actual constructor(index: Int) {
    private val resources = GraphiteWorkerResourceCache()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GraphiteRecorder-$index")
    }
    private val dispatcher = executor.asCoroutineDispatcher()

    internal actual suspend fun process(message: ByteArray): Unit = withContext(dispatcher + NonCancellable) {
        resources.process(message)
    }

    internal actual fun close() {
        dispatcher.close()
    }

    internal actual suspend fun awaitClosed() {
        while (!executor.isTerminated) delay(1)
    }
}
