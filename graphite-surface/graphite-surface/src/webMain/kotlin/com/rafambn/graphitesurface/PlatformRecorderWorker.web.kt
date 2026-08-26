package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual class PlatformRecorderWorker actual constructor(index: Int) {
    private val worker = WebValidationWorker(index)
    private val resources = GraphiteWorkerResourceCache()
    private val closed = CompletableDeferred<Unit>()

    internal actual suspend fun process(
        message: ByteArray,
        program: GraphiteCommandProgram,
        pixelSize: IntSize?,
    ): PlatformRecording =
        suspendCancellableCoroutine { continuation ->
            worker.process(
                commands = message,
                onSuccess = { result ->
                    if (continuation.isActive) {
                        try {
                            resources.process(result)
                            continuation.resume(PlatformRecording())
                        } catch (error: Throwable) {
                            continuation.resumeWithException(error)
                        }
                    }
                },
                onFailure = { message ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException(message))
                    }
                },
            )
        }

    internal actual fun close() {
        worker.close()
        closed.complete(Unit)
    }

    internal actual suspend fun awaitClosed() {
        closed.await()
    }
}
