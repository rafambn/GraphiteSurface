package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntSize
import com.rafambn.graphitesurface.engine.AndroidGraphiteRecorder
import com.rafambn.graphitesurface.engine.AndroidGraphiteRecordingContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal actual class PlatformRecorderWorker actual constructor(index: Int) {
    private val resources = GraphiteWorkerResourceCache()
    private val nativeRecorder = AtomicReference<AndroidGraphiteRecorder?>(null)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GraphiteRecorder-$index")
    }
    private val dispatcher = executor.asCoroutineDispatcher()

    internal actual suspend fun process(
        message: ByteArray,
        program: GraphiteCommandProgram,
        pixelSize: IntSize?,
    ): PlatformRecording = withContext(dispatcher + NonCancellable) {
        resources.process(message)
        val recorder = nativeRecorder.get()
            ?: return@withContext PlatformRecording(native = null)
        val size = checkNotNull(pixelSize) { "Native recording requires an attached presentation" }
        PlatformRecording(
            native = recorder.record(size.width, size.height) {
                AndroidGraphiteDrawContextAdapter(this).executeGraphiteCommands(program)
            },
        )
    }

    internal fun bind(context: AndroidGraphiteRecordingContext) {
        val recorder = context.makeRecorder()
        if (!nativeRecorder.compareAndSet(null, recorder)) {
            recorder.close()
            error("Graphite recorder is already bound to a native context")
        }
    }

    internal fun unbind() {
        val recorder = nativeRecorder.getAndSet(null) ?: return
        if (executor.isShutdown) {
            recorder.close()
        } else {
            executor.submit(recorder::close).get()
        }
    }

    internal actual fun close() {
        nativeRecorder.getAndSet(null)?.let { recorder -> executor.execute(recorder::close) }
        dispatcher.close()
    }

    internal actual suspend fun awaitClosed() {
        while (!executor.isTerminated) delay(1)
    }
}
