package com.rafambn.graphitesurface

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual class PlatformRecorderWorker actual constructor(index: Int) {
    private val worker = WebValidationWorker(index)
    private val closed = CompletableDeferred<Unit>()

    internal actual suspend fun process(commands: ByteArray): ByteArray =
        suspendCancellableCoroutine { continuation ->
            worker.process(
                commands = commands,
                onSuccess = { result ->
                    if (continuation.isActive) {
                        try {
                            GraphiteCommandBuffer.validate(result)
                            continuation.resume(result)
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
