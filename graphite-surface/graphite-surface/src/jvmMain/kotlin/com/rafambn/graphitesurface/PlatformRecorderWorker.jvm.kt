package com.rafambn.graphitesurface

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal actual class PlatformRecorderWorker actual constructor(index: Int) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GraphiteRecorder-$index").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()

    internal actual suspend fun process(commands: ByteArray): ByteArray = withContext(dispatcher) {
        GraphiteCommandBuffer.validate(commands)
        commands.copyOf()
    }

    internal actual fun close() {
        dispatcher.close()
    }

    internal actual suspend fun awaitClosed() {
        while (!executor.isTerminated) delay(1)
    }
}
