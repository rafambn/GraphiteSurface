@file:OptIn(
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.coroutines.DelicateCoroutinesApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntSize
import com.rafambn.graphitesurface.engine.GraphiteEngineGraphiteEngineView_iosKt
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.UIKit.UIView

internal actual class PlatformRecorderWorker actual constructor(index: Int) {
    private val resources = GraphiteWorkerResourceCache()
    private val binding = AtomicReference<NativeRecorderBinding?>(null)
    private val dispatcher = newSingleThreadContext("GraphiteRecorder-$index")
    private val closed = CompletableDeferred<Unit>()

    internal actual suspend fun process(
        message: ByteArray,
        program: GraphiteCommandProgram,
        pixelSize: IntSize?,
    ): PlatformRecording = withContext(dispatcher + NonCancellable) {
        resources.process(message)
        val native = binding.load()
            ?: return@withContext PlatformRecording(handle = 0uL)
        val size = checkNotNull(pixelSize) { "Native recording requires an attached presentation" }
        GraphiteEngineGraphiteEngineView_iosKt.gsBeginRecordingRecorder(
            native.handle,
            size.width,
            size.height,
        )
        GSGraphiteDrawContext(native.view, native.handle).executeGraphiteCommands(program)
        PlatformRecording(
            handle = GraphiteEngineGraphiteEngineView_iosKt
                .gsFinishRecordingRecorder(native.handle),
        )
    }

    internal fun bind(engineView: UIView) {
        val native = NativeRecorderBinding(
            view = engineView,
            handle = GraphiteEngineGraphiteEngineView_iosKt.gsCreateRecorderView(engineView),
        )
        if (!binding.compareAndSet(null, native)) {
            disposeNativeRecorder(native)
            error("Graphite recorder is already bound to a native context")
        }
    }

    internal fun unbind() {
        val native = binding.exchange(null) ?: return
        if (closed.isCompleted) return
        runBlocking {
            withContext(dispatcher) { disposeNativeRecorder(native) }
        }
    }

    internal actual fun close() {
        val native = binding.exchange(null)
        CoroutineScope(dispatcher).launch {
            native?.let(::disposeNativeRecorder)
            dispatcher.close()
            closed.complete(Unit)
        }
    }

    internal actual suspend fun awaitClosed() {
        closed.await()
    }

    private fun disposeNativeRecorder(native: NativeRecorderBinding) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDisposeRecorderRecorder(native.handle)
    }
}

private class NativeRecorderBinding(
    val view: UIView,
    val handle: ULong,
)
