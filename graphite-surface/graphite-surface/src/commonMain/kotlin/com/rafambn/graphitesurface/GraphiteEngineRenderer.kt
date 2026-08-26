@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntSize
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference

internal class GraphiteEngineRenderer(private val runtime: GraphiteEngine) :
    GraphitePresentationRenderer {
    private val attachmentId = AtomicLong(0)
    private val density = AtomicReference(1f)

    internal fun bind(id: Long, density: Float) {
        this.density.store(density)
        attachmentId.store(id)
    }

    internal fun unbind(expectedId: Long?) {
        if (expectedId == null) return
        attachmentId.compareAndSet(expectedId, 0)
    }

    override fun onSurfaceCreated() = Unit

    override fun onSurfaceChanged(size: IntSize) {
        val id = attachmentId.load()
        if (id != 0L) runtime.updatePresentation(id, size, density.load())
    }

    override fun hasPendingFrame(): Boolean {
        val id = attachmentId.load()
        return id != 0L && runtime.hasPendingFrame(id)
    }

    override fun onDrawFrame(context: GraphiteDrawContext) {
        val id = attachmentId.load()
        if (id == 0L) return
        val frame = runtime.takePendingFrame(id) ?: return
        try {
            context.clear(frame.clearColor.toArgbLong())
            frame.insertions.forEach { insertion ->
                context.insertRecording(
                    recording = insertion.platformRecording,
                    program = insertion.program,
                    translation = insertion.translation,
                    clip = insertion.clip,
                )
            }
        } catch (error: Throwable) {
            runtime.failFromRenderWorker(error)
        }
    }

    override fun onSurfaceError(error: Throwable) {
        runtime.failFromRenderWorker(error)
    }
}
